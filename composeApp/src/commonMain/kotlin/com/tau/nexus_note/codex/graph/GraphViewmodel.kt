package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.settings.GraphLayoutMode
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
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val mediaPath: String
) {
    private val physicsEngine = PhysicsEngine()

    // Local State overrides
    private val _layoutMode = MutableStateFlow(GraphLayoutMode.CONTINUOUS)
    val layoutMode = _layoutMode.asStateFlow()

    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    private val _snapEnabled = MutableStateFlow(true)
    val snapEnabled = _snapEnabled.asStateFlow()

    private val _simulationRunning = MutableStateFlow(false)
    val simulationRunning = _simulationRunning.asStateFlow()

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

    private val _isProcessingLayout = MutableStateFlow(false)
    val isProcessingLayout = _isProcessingLayout.asStateFlow()

    private val _collapsedNodes = MutableStateFlow<Set<Long>>(emptySet())

    private var size = Size.Zero
    private var currentDensity: Float = 1.0f

    private var lastNodeList: List<NodeDisplayItem> = emptyList()
    private var lastEdgeList: List<EdgeDisplayItem> = emptyList()
    private var lastSchema: SchemaData? = null

    // For "Settle" behavior in Computed mode
    private var settleFramesRemaining = 0

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                // Initial load from settings, then we diverge
                if (_graphNodes.value.isEmpty()) {
                    _physicsOptions.value = settings.graphPhysics.options
                    _layoutMode.value = settings.graphPhysics.layoutMode
                }
                _renderingSettings.value = settings.graphRendering
            }
        }
    }

    suspend fun runSimulationLoop() {
        var lastTimeNanos = withFrameNanos { it }
        while (true) {
            val currentTimeNanos = withFrameNanos { it }
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            // Determine if we should update physics
            val shouldSimulate = when (_layoutMode.value) {
                GraphLayoutMode.CONTINUOUS -> _simulationRunning.value
                GraphLayoutMode.COMPUTED -> settleFramesRemaining > 0
                GraphLayoutMode.HIERARCHICAL -> false // Strictly static unless explicit re-layout
            }

            if (shouldSimulate && _graphNodes.value.isNotEmpty()) {
                val updatedNodes = physicsEngine.update(
                    _graphNodes.value,
                    _graphEdges.value,
                    _physicsOptions.value,
                    dt.coerceAtMost(0.032f)
                )

                // Merge the dragged node's live position back into the physics result.
                val draggedId = _draggedNodeId.value
                if (draggedId != null) {
                    val currentLiveNode = _graphNodes.value[draggedId]
                    val updatedNode = updatedNodes[draggedId]
                    if (currentLiveNode != null && updatedNode != null) {
                        updatedNode.pos = currentLiveNode.pos
                        updatedNode.vel = Offset.Zero // Zero out velocity while dragging
                    }
                }

                _graphNodes.value = updatedNodes

                if (settleFramesRemaining > 0) settleFramesRemaining--
            } else {
                // Throttle loop when idle
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun updateGraphData(
        nodeList: List<NodeDisplayItem>,
        edgeList: List<EdgeDisplayItem>,
        schema: SchemaData?
    ) {
        lastNodeList = nodeList
        lastEdgeList = edgeList
        lastSchema = schema

        // If first load, force calculation. Otherwise preserve.
        val isFirstLoad = _graphNodes.value.isEmpty()
        recomputeGraphState(calculateNewPositions = isFirstLoad)

        if (isFirstLoad && _layoutMode.value == GraphLayoutMode.CONTINUOUS) {
            _simulationRunning.value = true
        }
    }

    // --- Mode Switching & Actions ---

    fun onLayoutModeChanged(mode: GraphLayoutMode) {
        _layoutMode.value = mode
        when (mode) {
            GraphLayoutMode.CONTINUOUS -> _simulationRunning.value = true
            GraphLayoutMode.COMPUTED -> {
                _simulationRunning.value = false
                triggerComputedLayout()
            }
            GraphLayoutMode.HIERARCHICAL -> {
                _simulationRunning.value = false
                triggerHierarchicalLayout()
            }
        }
    }

    fun onTriggerLayoutAction() {
        when (_layoutMode.value) {
            GraphLayoutMode.CONTINUOUS -> {
                // "Detangle" -> Reset positions near center and let physics explode them
                recomputeGraphState(calculateNewPositions = true)
            }
            GraphLayoutMode.COMPUTED -> triggerComputedLayout()
            GraphLayoutMode.HIERARCHICAL -> triggerHierarchicalLayout()
        }
    }

    private fun triggerComputedLayout() {
        _isProcessingLayout.value = true
        viewModelScope.launch(Dispatchers.Default) {
            // Run FR Layout for N iterations
            val params = mapOf("iterations" to 1000, "area" to 2.0f, "gravity" to 0.1f)
            val layoutFlow = runFRLayout(_graphNodes.value, _graphEdges.value, params)
            layoutFlow.collect { tickedNodes ->
                _graphNodes.value = tickedNodes
            }
            withContext(Dispatchers.Main) {
                _isProcessingLayout.value = false
            }
        }
    }

    private fun triggerHierarchicalLayout() {
        _isProcessingLayout.value = true
        recomputeGraphState(calculateNewPositions = true)
        _isProcessingLayout.value = false
    }

    fun updatePhysicsOptions(options: com.tau.nexus_note.codex.graph.physics.PhysicsOptions) {
        _physicsOptions.value = options
    }

    fun toggleSnap(enabled: Boolean) {
        _snapEnabled.value = enabled
    }

    // --- Graph Logic ---

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

            val isProxy = (visualSrc != edge.src.id) || (visualDst != edge.dst.id)
            val pair = Pair(visualSrc, visualDst)
            val strength = if(edge.label == StandardSchemas.EDGE_CONTAINS) 0.5f else 0.05f

            val isBidirectional = lastEdgeList.any {
                getVisualNodeId(it.src.id) == visualDst && getVisualNodeId(it.dst.id) == visualSrc
            }
            val isSelfLoop = visualSrc == visualDst

            if (isProxy) {
                if (!isSelfLoop || visibleEdgesMap[pair] == null) {
                    visibleEdgesMap[pair] = GraphEdge(
                        id = -1L * edge.id, sourceId = visualSrc, targetId = visualDst,
                        label = "Aggregated", strength = strength, colorInfo = labelToColor("Aggregated"),
                        isProxy = true, representedConnections = listOf("${edge.id}: ${edge.label}"),
                        isBidirectional = isBidirectional, isSelfLoop = isSelfLoop
                    )
                }
            } else {
                visibleEdgesMap[pair] = GraphEdge(
                    id = edge.id, sourceId = visualSrc, targetId = visualDst,
                    label = edge.label, strength = strength, colorInfo = labelToColor(edge.label),
                    isProxy = false, isBidirectional = isBidirectional, isSelfLoop = isSelfLoop
                )
            }
        }

        val initialPositions = if (calculateNewPositions) {
            LayoutStrategy.calculateInitialPositions(lastNodeList, lastEdgeList)
        } else {
            emptyMap()
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = lastNodeList.filter { !hiddenNodeIds.contains(it.id) }.associate { node ->
                val id = node.id
                val existingNode = currentNodes[id]
                val pos = if (calculateNewPositions) {
                    initialPositions[id] ?: Offset.Zero
                } else {
                    existingNode?.pos ?: Offset.Zero
                }

                val locked = existingNode?.isLocked ?: false
                val nodeObj = createGraphNode(node, pos, id, collapsedIds.contains(id), locked)

                if (!calculateNewPositions && existingNode != null) {
                    nodeObj.vel = existingNode.vel
                    // Preserve existing radius if we aren't creating from scratch to avoid snapping
                    if (existingNode.radius > nodeObj.radius) {
                        // Use a copy to update radius if needed, but since we are re-creating the object
                        // from `createGraphNode` we should probably just use the logic below
                        // or trust onNodeSizeChanged to fix it after render.
                        // For now, let's trust createGraphNode as the baseline.
                    }
                }

                id to nodeObj
            }
            newNodeMap
        }
        _graphEdges.value = visibleEdgesMap.values.toList()
    }

    private fun createGraphNode(node: NodeDisplayItem, pos: Offset, id: Long, isCollapsed: Boolean, isLocked: Boolean): GraphNode {
        // Initial estimates - these will be corrected by onNodeSizeChanged after first render
        val radius = when(node.style) {
            NodeStyle.DOCUMENT -> 160f * currentDensity
            NodeStyle.SECTION -> 150f * currentDensity
            NodeStyle.BLOCK -> 135f * currentDensity
            NodeStyle.CODE_BLOCK -> 160f * currentDensity
            NodeStyle.TABLE -> 150f * currentDensity
            NodeStyle.ATTACHMENT -> 110f * currentDensity
            else -> (_physicsOptions.value.nodeBaseRadius + 5f) * currentDensity
        }
        val mass = 10f
        val colorInfo = labelToColor(node.label)
        val absBgPath = node.backgroundImagePath?.let { File(mediaPath, it).absolutePath }
        val props = node.properties

        return when(node.style) {
            NodeStyle.DOCUMENT -> DocumentGraphNode(node.displayProperty, props[StandardSchemas.PROP_FRONTMATTER] ?: "{}", id, node.label, node.displayProperty, pos, Offset.Zero, mass, radius, colorInfo, false, isLocked, Offset.Zero, 0f, 0f, isCollapsed, absBgPath)
            NodeStyle.SECTION -> SectionGraphNode(node.displayProperty, props[StandardSchemas.PROP_LEVEL]?.toIntOrNull() ?: 1, id, node.label, node.displayProperty, pos, Offset.Zero, mass, radius, colorInfo, false, isLocked, Offset.Zero, 0f, 0f, isCollapsed, absBgPath)
            NodeStyle.BLOCK -> BlockGraphNode(props[StandardSchemas.PROP_CONTENT] ?: node.displayProperty, id, node.label, node.displayProperty, pos, Offset.Zero, mass, radius, colorInfo, false, isLocked, Offset.Zero, 0f, 0f, isCollapsed, absBgPath)
            else -> GenericGraphNode(id, node.label, node.displayProperty, emptyMap(), pos, Offset.Zero, mass, radius, colorInfo, false, isLocked, Offset.Zero, 0f, 0f, isCollapsed, absBgPath)
        }
    }

    // --- Interaction ---

    // DYNAMIC SIZING: Called from UI when the composable size changes
    fun onNodeSizeChanged(nodeId: Long, size: IntSize) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        // Calculate radius as half of the diagonal + padding to fully enclose the rectangular card
        val newRadius = (sqrt(width * width + height * height) / 2f) + 10f

        _graphNodes.update { currentNodes ->
            val node = currentNodes[nodeId] ?: return@update currentNodes

            // Threshold to prevent jitter/infinite loops if size shifts slightly
            if (abs(node.radius - newRadius) < 5f) return@update currentNodes

            val newNodes = currentNodes.toMutableMap()

            // Create a COPY of the node with the new radius.
            // Since `radius` is a `val` property on the data classes, copy() works perfectly.
            val updatedNode = when (node) {
                is GenericGraphNode -> node.copy(radius = newRadius)
                is DocumentGraphNode -> node.copy(radius = newRadius)
                is SectionGraphNode -> node.copy(radius = newRadius)
                is BlockGraphNode -> node.copy(radius = newRadius)
                is CodeBlockGraphNode -> node.copy(radius = newRadius)
                is TableGraphNode -> node.copy(radius = newRadius)
                is TagGraphNode -> node.copy(radius = newRadius)
                is AttachmentGraphNode -> node.copy(radius = newRadius)
                // If you add more node types, add them here
            }
            newNodes[nodeId] = updatedNode
            newNodes
        }
    }

    fun onDragStart(nodeId: Long) {
        _draggedNodeId.value = nodeId
        _dragVelocity.value = Offset.Zero

        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            newNodes[nodeId]?.let { node ->
                val newNode = node.copyNode()
                newNode.isFixed = true
                newNodes[nodeId] = newNode
            }
            newNodes
        }
    }

    fun onDrag(screenDelta: Offset) {
        val nodeId = _draggedNodeId.value ?: return
        val worldDelta = screenDelta / _transform.value.zoom
        _dragVelocity.value = worldDelta

        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            newNodes[nodeId]?.let { node ->
                val newNode = node.copyNode()
                newNode.pos += worldDelta
                newNodes[nodeId] = newNode
            }
            newNodes
        }
    }

    fun onDragEnd() {
        val nodeId = _draggedNodeId.value ?: return
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            newNodes[nodeId]?.let { node ->
                val newNode = node.copyNode()
                if (!_snapEnabled.value) {
                    newNode.isLocked = true
                }
                newNode.isFixed = false
                newNode.vel = _dragVelocity.value / (1f / 60f)
                newNodes[nodeId] = newNode
            }
            newNodes
        }
        _draggedNodeId.value = null

        if (_layoutMode.value == GraphLayoutMode.COMPUTED && _snapEnabled.value) {
            settleFramesRemaining = 60
        }
    }

    fun onNodeLockToggle(nodeId: Long) {
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            newNodes[nodeId]?.let { node ->
                val newNode = node.copyNode()
                newNode.isLocked = !newNode.isLocked
                newNodes[nodeId] = newNode
            }
            newNodes
        }
    }

    fun screenToWorld(screenPos: Offset): Offset {
        val pan = _transform.value.pan
        val zoom = _transform.value.zoom
        val center = Offset(size.width / 2f, size.height / 2f)
        return (screenPos - center - pan * zoom) / zoom
    }

    fun onPan(delta: Offset) { _transform.update { it.copy(pan = it.pan + (delta / it.zoom)) } }
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

    // Tap uses screen pos to find logic, but dragging uses direct ID
    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        // Logic handled by NodeWrapper tap, but this remains for background taps if needed
    }

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) { size = Size(newSize.width.toFloat(), newSize.height.toFloat()) }
    fun updateDensity(density: Float) { currentDensity = density }
    fun onFabClick() { _showFabMenu.update { !it } }
    fun toggleSettings() { _showSettings.update { !it } }
    fun onCleared() { _simulationRunning.value = false }
}