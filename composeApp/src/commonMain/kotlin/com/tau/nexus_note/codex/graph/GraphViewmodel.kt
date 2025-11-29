package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.settings.GraphLayoutMode
import com.tau.nexus_note.settings.LayoutDirection
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

    private val _layoutDirection = MutableStateFlow(LayoutDirection.LEFT_RIGHT)
    val layoutDirection = _layoutDirection.asStateFlow()

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

    // --- Phase 3: Algorithmic Clustering State ---
    private val _clusteringResult = MutableStateFlow<ClusteringResult?>(null)

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
                    _layoutDirection.value = settings.graphPhysics.hierarchicalDirection
                    // Initialize solver type
                    physicsEngine.setSolverType(settings.graphPhysics.options.solver)
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
                GraphLayoutMode.HIERARCHICAL -> false
            }

            if (shouldSimulate && _graphNodes.value.isNotEmpty()) {
                val updatedNodes = physicsEngine.update(
                    _graphNodes.value,
                    _graphEdges.value,
                    _physicsOptions.value,
                    dt.coerceAtMost(0.032f)
                )

                val draggedId = _draggedNodeId.value
                if (draggedId != null) {
                    val currentLiveNode = _graphNodes.value[draggedId]
                    val updatedNode = updatedNodes[draggedId]
                    if (currentLiveNode != null && updatedNode != null) {
                        updatedNode.pos = currentLiveNode.pos
                        updatedNode.vel = Offset.Zero
                    }
                }

                _graphNodes.value = updatedNodes

                if (settleFramesRemaining > 0) settleFramesRemaining--
            } else {
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

        val isFirstLoad = _graphNodes.value.isEmpty()
        recomputeGraphState(calculateNewPositions = isFirstLoad)

        if (isFirstLoad && _layoutMode.value == GraphLayoutMode.CONTINUOUS) {
            _simulationRunning.value = true
        }
    }

    // --- Clustering Actions ---

    fun clusterOutliers() {
        // Collect current positions to keep clusters stable relative to their contents
        val currentPositions = _graphNodes.value.mapValues { it.value.pos }

        val result = ClusteringEngine.clusterOutliers(lastNodeList, lastEdgeList, currentPositions)
        _clusteringResult.value = result
        recomputeGraphState(calculateNewPositions = false)
    }

    fun clusterByHubSize(threshold: Int) {
        val currentPositions = _graphNodes.value.mapValues { it.value.pos }

        val result = ClusteringEngine.clusterByHubSize(lastNodeList, lastEdgeList, currentPositions, threshold)
        _clusteringResult.value = result
        recomputeGraphState(calculateNewPositions = false)
    }

    fun clearClustering() {
        _clusteringResult.value = null
        recomputeGraphState(calculateNewPositions = false)
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

    fun onLayoutDirectionChanged(direction: LayoutDirection) {
        _layoutDirection.value = direction
        if (_layoutMode.value == GraphLayoutMode.HIERARCHICAL) {
            triggerHierarchicalLayout()
        }
    }

    fun onTriggerLayoutAction() {
        when (_layoutMode.value) {
            GraphLayoutMode.CONTINUOUS -> {
                recomputeGraphState(calculateNewPositions = true)
            }
            GraphLayoutMode.COMPUTED -> triggerComputedLayout()
            GraphLayoutMode.HIERARCHICAL -> triggerHierarchicalLayout()
        }
    }

    private fun triggerComputedLayout() {
        _isProcessingLayout.value = true
        viewModelScope.launch(Dispatchers.Default) {
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
        recomputeGraphState(calculateNewPositions = false)

        viewModelScope.launch(Dispatchers.Default) {
            val currentNodes = _graphNodes.value
            val currentEdges = _graphEdges.value
            val direction = _layoutDirection.value

            val newPositions = HierarchicalLayout.arrange(currentNodes, currentEdges, direction)

            val updatedNodes = currentNodes.mapValues { (id, node) ->
                val pos = newPositions[id] ?: node.pos
                node.copyNode().apply {
                    this.pos = pos
                    this.vel = Offset.Zero
                    this.isFixed = false
                }
            }

            _graphNodes.value = updatedNodes

            withContext(Dispatchers.Main) {
                _isProcessingLayout.value = false
            }
        }
    }

    fun updatePhysicsOptions(options: PhysicsOptions) {
        _physicsOptions.value = options
        physicsEngine.setSolverType(options.solver)
    }

    fun toggleSnap(enabled: Boolean) {
        _snapEnabled.value = enabled
    }

    // --- Graph Logic ---

    private fun recomputeGraphState(calculateNewPositions: Boolean) {
        val collapsedIds = _collapsedNodes.value
        val parentMap = mutableMapOf<Long, Long>()
        val clusterData = _clusteringResult.value

        // 1. Build Parent Map (Structural Hierarchy)
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

        // 2. Recursive Lookup Logic
        fun getVisualNodeId(actualId: Long): Long {
            // First pass: Check structural hiding
            var currentId = actualId
            if (hiddenNodeIds.contains(actualId)) {
                // Walk up until we find the visible parent
                var walker = actualId
                while (hiddenNodeIds.contains(walker)) {
                    val parentId = parentMap[walker] ?: break
                    walker = parentId
                }
                currentId = walker
            }

            // Second pass: Check algorithmic clustering
            // If the visible structural node is now part of a cluster, return cluster ID
            val clusterId = clusterData?.nodeMap?.get(currentId)
            return clusterId ?: currentId
        }

        val visibleEdgesMap = mutableMapOf<Pair<Long, Long>, GraphEdge>()

        lastEdgeList.forEach { edge ->
            val visualSrc = getVisualNodeId(edge.src.id)
            val visualDst = getVisualNodeId(edge.dst.id)

            val isProxy = (visualSrc != edge.src.id) || (visualDst != edge.dst.id)

            // Skip loops if they are hidden inside the same node
            if (visualSrc == visualDst) return@forEach

            val pair = Pair(visualSrc, visualDst)
            val strength = if(edge.label == StandardSchemas.EDGE_CONTAINS) 0.5f else 0.05f

            val isBidirectional = lastEdgeList.any {
                getVisualNodeId(it.src.id) == visualDst && getVisualNodeId(it.dst.id) == visualSrc
            }

            // Use negative IDs for proxy edges to avoid key collisions with real DB edge IDs
            val edgeId = if(isProxy) -1L * edge.id else edge.id

            if (isProxy) {
                if (visibleEdgesMap[pair] == null) {
                    visibleEdgesMap[pair] = GraphEdge(
                        id = edgeId, sourceId = visualSrc, targetId = visualDst,
                        label = "Aggregated", strength = strength, colorInfo = labelToColor("Aggregated"),
                        isProxy = true, representedConnections = listOf("${edge.id}: ${edge.label}"),
                        isBidirectional = isBidirectional
                    )
                }
            } else {
                visibleEdgesMap[pair] = GraphEdge(
                    id = edgeId, sourceId = visualSrc, targetId = visualDst,
                    label = edge.label, strength = strength, colorInfo = labelToColor(edge.label),
                    isProxy = false, isBidirectional = isBidirectional
                )
            }
        }

        val initialPositions = if (calculateNewPositions) {
            LayoutStrategy.calculateInitialPositions(lastNodeList, lastEdgeList)
        } else {
            emptyMap()
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = mutableMapOf<Long, GraphNode>()

            // A. Process Regular Nodes (Filtered)
            val clusterHiddenIds = clusterData?.nodeMap?.keys ?: emptySet()

            lastNodeList.forEach { node ->
                val id = node.id
                // Skip if hidden structurally OR hidden by cluster
                if (hiddenNodeIds.contains(id) || clusterHiddenIds.contains(id)) return@forEach

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
                }
                newNodeMap[id] = nodeObj
            }

            // B. Add Cluster Nodes
            clusterData?.clusters?.forEach { (clusterId, clusterNode) ->
                val existingNode = currentNodes[clusterId]

                // Preserve physics state if cluster existed
                val finalNode = if (existingNode != null) {
                    clusterNode.copy(
                        pos = existingNode.pos,
                        vel = existingNode.vel,
                        isLocked = existingNode.isLocked
                    )
                } else {
                    clusterNode
                }
                newNodeMap[clusterId] = finalNode
            }

            newNodeMap
        }
        _graphEdges.value = visibleEdgesMap.values.toList()
    }

    private fun createGraphNode(node: NodeDisplayItem, pos: Offset, id: Long, isCollapsed: Boolean, isLocked: Boolean): GraphNode {
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

    fun onNodeSizeChanged(nodeId: Long, size: IntSize) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val newRadius = (sqrt(width * width + height * height) / 2f) + 10f

        _graphNodes.update { currentNodes ->
            val node = currentNodes[nodeId] ?: return@update currentNodes
            if (abs(node.radius - newRadius) < 5f) return@update currentNodes

            val newNodes = currentNodes.toMutableMap()
            // Generic update logic relying on copy
            val updatedNode = when (node) {
                is GenericGraphNode -> node.copy(radius = newRadius)
                is DocumentGraphNode -> node.copy(radius = newRadius)
                is SectionGraphNode -> node.copy(radius = newRadius)
                is BlockGraphNode -> node.copy(radius = newRadius)
                is CodeBlockGraphNode -> node.copy(radius = newRadius)
                is TableGraphNode -> node.copy(radius = newRadius)
                is TagGraphNode -> node.copy(radius = newRadius)
                is AttachmentGraphNode -> node.copy(radius = newRadius)
                is ClusterNode -> node.copy(radius = newRadius) // Handle cluster
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

    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) { }

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) { size = Size(newSize.width.toFloat(), newSize.height.toFloat()) }
    fun updateDensity(density: Float) { currentDensity = density }
    fun onFabClick() { _showFabMenu.update { !it } }
    fun toggleSettings() { _showSettings.update { !it } }
    fun onCleared() { _simulationRunning.value = false }
}