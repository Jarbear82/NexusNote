package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.GraphNode
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
import kotlin.random.Random
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.utils.getFileName
import java.io.File

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val dbPath: String // Needed to resolve relative image paths
) {

    private val physicsEngine = PhysicsEngine()

    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

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

    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()

    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()

    // NEW: Collapse State
    private val _collapsedNodes = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedNodes = _collapsedNodes.asStateFlow()

    private var size = Size.Zero

    // Keep references to raw data to re-compute on collapse toggle
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

    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        lastNodeList = nodeList
        lastEdgeList = edgeList
        recomputeGraphState()
    }

    fun toggleNodeCollapse(nodeId: Long) {
        _collapsedNodes.update {
            if (it.contains(nodeId)) it - nodeId else it + nodeId
        }
        recomputeGraphState()
    }

    private fun recomputeGraphState() {
        val collapsedIds = _collapsedNodes.value
        val parentMap = mutableMapOf<Long, Long>() // Child ID -> Parent ID

        // 1. Identify Hierarchy (Spine)
        // Find CONTAINS edges to build parent map
        lastEdgeList.filter { it.label == StandardSchemas.EDGE_CONTAINS }.forEach { edge ->
            parentMap[edge.dst.id] = edge.src.id
        }

        // 2. Identify Hidden Nodes (Children of Collapsed parents)
        val hiddenNodeIds = mutableSetOf<Long>()

        // Recursive function to check if a node is hidden
        fun isHidden(nodeId: Long): Boolean {
            val parentId = parentMap[nodeId] ?: return false
            // If parent is collapsed, I am hidden
            if (collapsedIds.contains(parentId)) return true
            // If parent is hidden (because grandparent is collapsed), I am hidden
            return isHidden(parentId)
        }

        lastNodeList.forEach { node ->
            if (isHidden(node.id)) hiddenNodeIds.add(node.id)
        }

        // 3. Compute Edges (With Roll-up)
        // Helper to find the "Visual Source" - the nearest visible ancestor
        fun getVisualNodeId(actualId: Long): Long {
            if (!hiddenNodeIds.contains(actualId)) return actualId

            var currentId = actualId
            while (hiddenNodeIds.contains(currentId)) {
                val parentId = parentMap[currentId]
                if (parentId == null) return currentId // Should not happen if logic is correct
                currentId = parentId
            }
            return currentId
        }

        val visibleEdgesMap = mutableMapOf<Pair<Long, Long>, GraphEdge>()

        lastEdgeList.forEach { edge ->
            val visualSrc = getVisualNodeId(edge.src.id)
            val visualDst = getVisualNodeId(edge.dst.id)

            // Ignore edges that become self-loops due to roll-up (internal edges)
            if (visualSrc == visualDst) return@forEach

            val isProxy = (visualSrc != edge.src.id) || (visualDst != edge.dst.id)
            val pair = Pair(visualSrc, visualDst)

            if (isProxy) {
                // Aggregate
                val existing = visibleEdgesMap[pair]
                val connectionLabel = "${edge.id}: ${edge.label}"
                if (existing != null) {
                    visibleEdgesMap[pair] = existing.copy(
                        representedConnections = existing.representedConnections + connectionLabel
                    )
                } else {
                    visibleEdgesMap[pair] = GraphEdge(
                        id = -1L * edge.id, // Negative ID for proxy? Or hash.
                        sourceId = visualSrc,
                        targetId = visualDst,
                        label = "Aggregated",
                        strength = 1.0f,
                        colorInfo = labelToColor("Aggregated"),
                        isProxy = true,
                        representedConnections = listOf(connectionLabel)
                    )
                }
            } else {
                // Normal Edge
                visibleEdgesMap[pair] = GraphEdge(
                    id = edge.id,
                    sourceId = visualSrc,
                    targetId = visualDst,
                    label = edge.label,
                    strength = 1.0f,
                    colorInfo = labelToColor(edge.label),
                    isProxy = false
                )
            }
        }

        // 4. Update Nodes (Filter Hidden)
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        visibleEdgesMap.values.forEach { edge ->
            edgeCountByNodeId[edge.sourceId] = (edgeCountByNodeId[edge.sourceId] ?: 0) + 1
            edgeCountByNodeId[edge.targetId] = (edgeCountByNodeId[edge.targetId] ?: 0) + 1
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = lastNodeList.filter { !hiddenNodeIds.contains(it.id) }.associate { node ->
                val id = node.id
                val edgeCount = edgeCountByNodeId[id] ?: 0
                val radius = _physicsOptions.value.nodeBaseRadius + (edgeCount * _physicsOptions.value.nodeRadiusEdgeFactor)
                val mass = (edgeCount + 1).toFloat()
                val existingNode = currentNodes[id]

                // Resolve absolute path for background
                val absBgPath = node.backgroundImagePath?.let { relative ->
                    val dbFile = File(dbPath)
                    File(dbFile.parent, relative).absolutePath
                }

                val newNode = if (existingNode != null) {
                    existingNode.copy(
                        label = node.label,
                        displayProperty = node.displayProperty,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label),
                        backgroundImagePath = absBgPath,
                        isCollapsed = collapsedIds.contains(id)
                    )
                } else {
                    GraphNode(
                        id = id,
                        label = node.label,
                        displayProperty = node.displayProperty,
                        pos = Offset(Random.nextFloat() * 100 - 50, Random.nextFloat() * 100 - 50),
                        vel = Offset.Zero,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label),
                        isFixed = false,
                        backgroundImagePath = absBgPath,
                        isCollapsed = collapsedIds.contains(id)
                    )
                }
                id to newNode
            }
            newNodeMap
        }

        _graphEdges.value = visibleEdgesMap.values.toList()
    }

    // --- Coordinate Conversion ---

    fun screenToWorld(screenPos: Offset): Offset {
        val pan = _transform.value.pan
        val zoom = _transform.value.zoom
        val center = Offset(size.width / 2f, size.height / 2f)
        return (screenPos - center - pan * zoom) / zoom
    }

    private fun screenDeltaToWorldDelta(screenDelta: Offset): Offset {
        return screenDelta / _transform.value.zoom
    }

    // --- Gesture Handlers ---

    fun onPan(delta: Offset) {
        _transform.update {
            it.copy(pan = it.pan + (delta / it.zoom))
        }
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
            val distance = (worldPos - node.pos).getDistance()
            distance < node.radius
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
                    newNodes[tappedNode.id] = node.copy(isFixed = true)
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
            if (node != null) {
                newNodes[nodeId] = node.copy(pos = node.pos + worldDelta)
            }
            newNodes
        }
    }

    fun onDragEnd() {
        val nodeId = _draggedNodeId.value ?: return
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                newNodes[nodeId] = node.copy(
                    isFixed = false,
                    vel = _dragVelocity.value / (1f / 60f)
                )
            }
            newNodes
        }
        _draggedNodeId.value = null
        _dragVelocity.value = Offset.Zero
    }

    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)

        if (tappedNode != null) {
            onNodeTapped(tappedNode.id)
        }
    }

    // --- UI Handlers ---

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() {
        _showFabMenu.update { !it }
    }

    fun toggleSettings() {
        _showSettings.update { !it }
    }

    fun startSimulation() {
        _simulationRunning.value = settingsFlow.value.graphRendering.startSimulationOnLoad
    }

    fun stopSimulation() {
        _simulationRunning.value = false
    }

    fun onCleared() {
        stopSimulation()
    }

    fun onShowDetangleDialog() {
        _showDetangleDialog.value = true
        _showSettings.value = false
    }

    fun onDismissDetangleDialog() {
        _showDetangleDialog.value = false
    }

    fun startDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        stopSimulation()
        _isDetangling.value = true
        _showDetangleDialog.value = false

        viewModelScope.launch(Dispatchers.Default) {
            val layoutFlow = runFRLayout(_graphNodes.value, _graphEdges.value, params)
            layoutFlow.collect { tickedNodes ->
                _graphNodes.value = tickedNodes
            }
            withContext(Dispatchers.Main) {
                _isDetangling.value = false
                startSimulation()
            }
        }
    }
}