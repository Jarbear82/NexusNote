package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.settings.SettingsData
import com.tau.nexus_note.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.utils.PropertySerialization
import java.io.File

// Explicitly import the new GraphNode interface to avoid conflict with datamodels.GraphNode
import com.tau.nexus_note.codex.graph.GraphNode

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val mediaPath: String
) {

    private val physicsEngine = PhysicsEngine()

    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    private val _simulationRunning = MutableStateFlow(false)
    val simulationRunning = _simulationRunning.asStateFlow()

    // Use the specific GraphNode interface
    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    private val _draggedNodeId = MutableStateFlow<Long?>(null)
    private val _dragVelocity = MutableStateFlow(Offset.Zero)

    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()

    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()

    private val _collapsedNodes = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedNodes = _collapsedNodes.asStateFlow()

    private var size = Size.Zero
    private var currentDensity: Float = 1.0f

    private var lastNodeList: List<NodeDisplayItem> = emptyList()
    private var lastEdgeList: List<EdgeDisplayItem> = emptyList()

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
            }
        }
    }

    suspend fun runSimulationLoop() {
        if (!_simulationRunning.value) return

        var lastTimeNanos = withFrameNanos { it }
        while (_simulationRunning.value) {
            val currentTimeNanos = withFrameNanos { it }
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            if (_graphNodes.value.isNotEmpty()) {
                val updatedNodes = physicsEngine.update(
                    _graphNodes.value,
                    _graphEdges.value,
                    _physicsOptions.value,
                    dt.coerceAtMost(0.032f)
                )
                _graphNodes.value = updatedNodes
            }
        }
    }

    fun updateDensity(density: Float) {
        if (abs(currentDensity - density) > 0.01f) {
            currentDensity = density
            // Recalculate radii with new density, but preserve positions
            if (lastNodeList.isNotEmpty()) {
                recomputeGraphState(calculateNewPositions = false)
            }
        }
    }

    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        lastNodeList = nodeList
        lastEdgeList = edgeList
        recomputeGraphState(calculateNewPositions = true)
    }

    fun toggleNodeCollapse(nodeId: Long) {
        _collapsedNodes.update {
            if (it.contains(nodeId)) it - nodeId else it + nodeId
        }
        recomputeGraphState(calculateNewPositions = false)
    }

    /**
     * @param calculateNewPositions If true, runs LayoutStrategy. If false, preserves current node positions (used for collapsing).
     */
    private fun recomputeGraphState(calculateNewPositions: Boolean) {
        val collapsedIds = _collapsedNodes.value
        val parentMap = mutableMapOf<Long, Long>()

        lastEdgeList.filter { it.label == StandardSchemas.EDGE_CONTAINS }.forEach { edge ->
            parentMap[edge.dst.id] = edge.src.id
        }

        val hiddenNodeIds = mutableSetOf<Long>()
        fun isHidden(nodeId: Long): Boolean {
            val parentId = parentMap[nodeId] ?: return false
            if (collapsedIds.contains(parentId)) return true
            return isHidden(parentId)
        }
        lastNodeList.forEach { node ->
            if (isHidden(node.id)) hiddenNodeIds.add(node.id)
        }

        // Edges logic
        fun getVisualNodeId(actualId: Long): Long {
            if (!hiddenNodeIds.contains(actualId)) return actualId
            var currentId = actualId
            while (hiddenNodeIds.contains(currentId)) {
                val parentId = parentMap[currentId]
                if (parentId == null) return currentId
                currentId = parentId
            }
            return currentId
        }

        val visibleEdgesMap = mutableMapOf<Pair<Long, Long>, GraphEdge>()
        lastEdgeList.forEach { edge ->
            val visualSrc = getVisualNodeId(edge.src.id)
            val visualDst = getVisualNodeId(edge.dst.id)
            if (visualSrc == visualDst) return@forEach

            val isProxy = (visualSrc != edge.src.id) || (visualDst != edge.dst.id)
            val pair = Pair(visualSrc, visualDst)

            val strength = if(edge.label == StandardSchemas.EDGE_CONTAINS) 0.5f else 0.05f // Stiff spine, loose ribs

            if (isProxy) {
                visibleEdgesMap[pair] = GraphEdge(
                    id = -1L * edge.id,
                    sourceId = visualSrc,
                    targetId = visualDst,
                    label = "Aggregated",
                    strength = strength,
                    colorInfo = labelToColor("Aggregated"),
                    isProxy = true,
                    representedConnections = listOf("${edge.id}: ${edge.label}")
                )
            } else {
                visibleEdgesMap[pair] = GraphEdge(
                    id = edge.id,
                    sourceId = visualSrc,
                    targetId = visualDst,
                    label = edge.label,
                    strength = strength,
                    colorInfo = labelToColor(edge.label),
                    isProxy = false
                )
            }
        }

        // --- POSITIONING LOGIC ---
        // If it's a fresh load, calculate positions using LayoutStrategy
        val initialPositions = if (calculateNewPositions) {
            LayoutStrategy.calculateInitialPositions(lastNodeList, lastEdgeList)
        } else {
            emptyMap()
        }

        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        visibleEdgesMap.values.forEach {
            edgeCountByNodeId[it.sourceId] = (edgeCountByNodeId[it.sourceId] ?: 0) + 1
            edgeCountByNodeId[it.targetId] = (edgeCountByNodeId[it.targetId] ?: 0) + 1
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = lastNodeList.filter { !hiddenNodeIds.contains(it.id) }.associate { node ->
                val id = node.id
                val edgeCount = edgeCountByNodeId[id] ?: 0

                // 1. Calculate Base Radius based on settings (Scaled by density)
                val baseRadius = (_physicsOptions.value.nodeBaseRadius + (edgeCount * 1.5f)) * currentDensity

                // 2. FIX: Apply visual size overrides based on the Renderer widths in NodeRenderers.kt
                // We approximate the radius as roughly half the width of the card.
                // IMPORTANT: Scale by currentDensity to convert DP to Pixels
                val radius = when(node.label) {
                    StandardSchemas.DOC_NODE_DOCUMENT -> 160f * currentDensity // Width 300dp
                    StandardSchemas.DOC_NODE_SECTION -> 150f * currentDensity  // Width 280dp
                    StandardSchemas.DOC_NODE_BLOCK -> 135f * currentDensity    // Width 250dp
                    StandardSchemas.DOC_NODE_CODE_BLOCK -> 160f * currentDensity // Width 300dp
                    StandardSchemas.DOC_NODE_TABLE -> 150f * currentDensity // Width 280dp
                    StandardSchemas.DOC_NODE_ATTACHMENT -> 110f * currentDensity // Width 200dp
                    else -> baseRadius // Keep small radius for Tags and generic nodes
                }

                val mass = (edgeCount + 1).toFloat()

                val existingNode = currentNodes[id]

                // If we have a calculated initial position, use it. Otherwise use existing or 0,0
                val pos = if (calculateNewPositions) {
                    initialPositions[id] ?: Offset.Zero
                } else {
                    existingNode?.pos ?: Offset.Zero
                }

                val absBgPath = node.backgroundImagePath?.let { File(mediaPath, it).absolutePath }

                val colorInfo = labelToColor(node.label)
                val props = node.properties

                // --- FACTORY: Create specific node types ---
                // Use the interface type GraphNode explicitly
                val newNode: GraphNode = when(node.label) {
                    StandardSchemas.DOC_NODE_DOCUMENT -> DocumentGraphNode(
                        title = node.displayProperty,
                        metaJson = props[StandardSchemas.PROP_FRONTMATTER] ?: "{}",
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath, isCollapsed = collapsedIds.contains(id)
                    )
                    StandardSchemas.DOC_NODE_SECTION -> SectionGraphNode(
                        title = node.displayProperty,
                        level = props[StandardSchemas.PROP_LEVEL]?.toIntOrNull() ?: 1,
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath, isCollapsed = collapsedIds.contains(id)
                    )
                    StandardSchemas.DOC_NODE_BLOCK -> BlockGraphNode(
                        content = props[StandardSchemas.PROP_CONTENT] ?: "",
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath
                    )
                    StandardSchemas.DOC_NODE_CODE_BLOCK -> CodeBlockGraphNode(
                        code = props[StandardSchemas.PROP_CONTENT] ?: "",
                        language = props[StandardSchemas.PROP_LANGUAGE] ?: "text",
                        caption = props[StandardSchemas.PROP_CAPTION] ?: "",
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath
                    )
                    StandardSchemas.DOC_NODE_TABLE -> TableGraphNode(
                        headers = PropertySerialization.deserializeList(props[StandardSchemas.PROP_HEADERS] ?: ""),
                        data = emptyList(), // Not fully implemented in parser yet
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath
                    )
                    StandardSchemas.DOC_NODE_TAG -> TagGraphNode(
                        name = props[StandardSchemas.PROP_NAME] ?: "tag",
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath
                    )
                    StandardSchemas.DOC_NODE_ATTACHMENT -> AttachmentGraphNode(
                        filename = props[StandardSchemas.PROP_NAME] ?: "file",
                        mimeType = props[StandardSchemas.PROP_MIME_TYPE] ?: "",
                        resolvedPath = absBgPath ?: "", // Attachment usually sets BG path property logic in Repo
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath
                    )
                    else -> GenericGraphNode(
                        id = id, label = node.label, displayProperty = node.displayProperty,
                        pos = pos, vel = Offset.Zero, mass = mass, radius = radius,
                        colorInfo = colorInfo, backgroundImagePath = absBgPath, isCollapsed = collapsedIds.contains(id)
                    )
                }

                // Preserve velocity/position if we aren't hard resetting
                if (!calculateNewPositions && existingNode != null) {
                    newNode.pos = existingNode.pos
                    newNode.vel = existingNode.vel
                    newNode.isFixed = existingNode.isFixed
                }

                id to newNode
            }
            newNodeMap
        }
        _graphEdges.value = visibleEdgesMap.values.toList()
    }

    fun screenToWorld(screenPos: Offset): Offset {
        val pan = _transform.value.pan
        val zoom = _transform.value.zoom
        val center = Offset(size.width / 2f, size.height / 2f)
        return (screenPos - center - pan * zoom) / zoom
    }

    private fun screenDeltaToWorldDelta(screenDelta: Offset): Offset {
        return screenDelta / _transform.value.zoom
    }

    fun onPan(delta: Offset) {
        _transform.update { it.copy(pan = it.pan + (delta / it.zoom)) }
    }

    fun onZoom(zoomFactor: Float, zoomCenterScreen: Offset) {
        val newZoomFactor = 1.0f + (zoomFactor - 1.0f) * settingsFlow.value.graphInteraction.zoomSensitivity
        _transform.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * newZoomFactor).coerceIn(0.1f, 10.0f)
            val sizeCenter = Offset(size.width / 2f, size.height / 2f)
            val worldPos = (zoomCenterScreen - state.pan * oldZoom - sizeCenter) / oldZoom
            val newPan = (zoomCenterScreen - worldPos * newZoom - sizeCenter) / newZoom
            state.copy(pan = newPan, zoom = newZoom)
        }
    }

    private fun findNodeAt(worldPos: Offset): GraphNode? {
        return _graphNodes.value.values.reversed().find { node ->
            val dx = abs(worldPos.x - node.pos.x)
            val dy = abs(worldPos.y - node.pos.y)
            dx < node.radius && dy < node.radius // Use actual radius for hit testing
        }
    }

    fun onDragStart(screenPos: Offset): Boolean {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)
        return if (tappedNode != null) {
            _draggedNodeId.value = tappedNode.id
            _dragVelocity.value = Offset.Zero
            _graphNodes.update { allNodes ->
                val newNodes = allNodes.toMutableMap()
                val node = newNodes[tappedNode.id]
                if (node != null) {
                    node.isFixed = true
                }
                newNodes
            }
            true
        } else {
            false
        }
    }

    fun onDrag(screenDelta: Offset) {
        val nodeId = _draggedNodeId.value ?: return
        val worldDelta = screenDeltaToWorldDelta(screenDelta)
        _dragVelocity.value = worldDelta
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) node.pos += worldDelta
            newNodes
        }
    }

    fun onDragEnd() {
        val nodeId = _draggedNodeId.value ?: return
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                node.isFixed = false
                node.vel = _dragVelocity.value / (1f / 60f)
            }
            newNodes
        }
        _draggedNodeId.value = null
        _dragVelocity.value = Offset.Zero
    }

    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)
        if (tappedNode != null) onNodeTapped(tappedNode.id)
    }

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() { _showFabMenu.update { !it } }
    fun toggleSettings() { _showSettings.update { !it } }
    fun startSimulation() { _simulationRunning.value = settingsFlow.value.graphRendering.startSimulationOnLoad }
    fun stopSimulation() { _simulationRunning.value = false }
    fun onCleared() { stopSimulation() }
    fun onShowDetangleDialog() { _showDetangleDialog.value = true; _showSettings.value = false }
    fun onDismissDetangleDialog() { _showDetangleDialog.value = false }
    fun startDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        stopSimulation()
        _isDetangling.value = true
        _showDetangleDialog.value = false
        viewModelScope.launch(Dispatchers.Default) {
            val layoutFlow = runFRLayout(_graphNodes.value, _graphEdges.value, params)
            layoutFlow.collect { tickedNodes -> _graphNodes.value = tickedNodes }
            withContext(Dispatchers.Main) { _isDetangling.value = false; startSimulation() }
        }
    }
}