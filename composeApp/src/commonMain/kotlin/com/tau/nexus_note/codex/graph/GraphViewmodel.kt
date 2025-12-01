package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.datamodels.NodeTopology
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.settings.GraphLayoutMode
import com.tau.nexus_note.settings.LayoutDirection
import com.tau.nexus_note.settings.SettingsData
import com.tau.nexus_note.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

    // --- Graph Data ---
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
    private val _expandedNodes = MutableStateFlow<Set<Long>>(emptySet())

    // Selection & Navigation State
    private val _selectionRect = MutableStateFlow<Rect?>(null)
    val selectionRect = _selectionRect.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode = _isEditMode.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _pendingEdgeSourceId = MutableStateFlow<Long?>(null)
    val pendingEdgeSourceId = _pendingEdgeSourceId.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<Long?>(null)

    // Derived State: Set of Node IDs that should be dimmed
    val dimmedNodeIds: StateFlow<Set<Long>> = combine(
        _selectedNodeId,
        _graphEdges,
        _graphNodes
    ) { selectedId, edges, nodes ->
        if (selectedId == null) {
            emptySet()
        } else {
            val neighbors = edges.mapNotNull { edge ->
                if (edge.sourceId == selectedId) edge.targetId
                else if (edge.targetId == selectedId) edge.sourceId
                else null
            }.toSet()
            nodes.keys.filter { it != selectedId && !neighbors.contains(it) }.toSet()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Events to signal Parent View
    private val _nodeSelectionRequest = MutableSharedFlow<List<Long>>()
    val nodeSelectionRequest = _nodeSelectionRequest.asSharedFlow()

    private val _createNodeRequest = MutableSharedFlow<Offset>()
    val createNodeRequest = _createNodeRequest.asSharedFlow()

    private val _createEdgeRequest = MutableSharedFlow<Pair<Long, Long>>()
    val createEdgeRequest = _createEdgeRequest.asSharedFlow()

    private var size = Size.Zero
    private var currentDensity: Float = 1.0f

    // --- Sizing Context (Dynamic based on window density) ---
    private var sizingContext = SizingContext(
        charWidthPx = 9f,
        lineHeightPx = 20f,
        paddingPx = 32f
    )

    private var lastNodeList: List<NodeDisplayItem> = emptyList()
    private var lastEdgeList: List<EdgeDisplayItem> = emptyList()
    private var lastSchema: SchemaData? = null

    private var settleFramesRemaining = 0
    private var selectionStartPos: Offset? = null

    // Kept for signature compatibility if GraphView calls it, though unused locally now
    fun updateTextMeasurer(measurer: TextMeasurer) {
        // No-op with new deterministic sizing
    }

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                if (_graphNodes.value.isEmpty()) {
                    _physicsOptions.value = settings.graphPhysics.options
                    _layoutMode.value = settings.graphPhysics.layoutMode
                    _layoutDirection.value = settings.graphPhysics.hierarchicalDirection
                    physicsEngine.setSolverType(settings.graphPhysics.options.solver)
                }
                _renderingSettings.value = settings.graphRendering
            }
        }
    }

    suspend fun runSimulationLoop() {
        var lastTimeNanos = withFrameNanos { it }
        val energyThreshold = 0.5f

        while (true) {
            val currentTimeNanos = withFrameNanos { it }
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            if (_simulationRunning.value) {
                val totalEnergy = physicsEngine.calculateSystemEnergy(_graphNodes.value)
                if (totalEnergy < energyThreshold && _draggedNodeId.value == null) {
                    _simulationRunning.value = false
                }
            }

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
                delay(50)
            }
        }
    }

    // --- Sizing & Density Logic ---

    fun updateDensity(density: Float) {
        if (abs(currentDensity - density) > 0.1f) {
            currentDensity = density

            // Calculate constants based on Monospace 14sp
            // 14sp * density = pixels.
            // Monospace char width is approx 0.6 * fontSize
            val fontSizePx = 14f * density
            val charW = fontSizePx * 0.65f // Tuning: slightly wider than 0.6
            val lineH = fontSizePx * 1.4f // standard line height multiplier

            sizingContext = SizingContext(
                charWidthPx = charW,
                lineHeightPx = lineH,
                paddingPx = 16f * density * 2 // 32dp padding total
            )

            // Trigger Recalculation of all nodes
            recalculateAllNodeSizes()
        }
    }

    private fun recalculateAllNodeSizes() {
        val current = _graphNodes.value
        if (current.isEmpty()) return

        val updated = current.mapValues { (id, node) ->
            // Find original data to get content
            val displayItem = lastNodeList.find { it.id == id }
            if (displayItem != null) {
                // Recalculate dimensions deterministically
                val newSize = NodeSizeCalculator.calculate(displayItem, node.isExpanded, sizingContext)
                updateNodeDimensions(node, newSize, node.isExpanded)
            } else {
                node
            }
        }
        _graphNodes.value = updated
    }

    // --- Navigation API ---

    fun fitToScreen() {
        val nodes = _graphNodes.value.values
        if (nodes.isEmpty() || size == Size.Zero) return

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        nodes.forEach { node ->
            minX = min(minX, node.pos.x - node.radius)
            minY = min(minY, node.pos.y - node.radius)
            maxX = max(maxX, node.pos.x + node.radius)
            maxY = max(maxY, node.pos.y + node.radius)
        }

        val graphWidth = maxX - minX
        val graphHeight = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        val padding = 50f
        val scaleX = size.width / (graphWidth + padding * 2)
        val scaleY = size.height / (graphHeight + padding * 2)

        val targetZoom = min(scaleX, scaleY).coerceIn(0.02f, 5f)
        val targetPan = Offset(-centerX, -centerY)

        animateTransform(targetPan, targetZoom)
    }

    fun focusOnNode(nodeId: Long) {
        val node = _graphNodes.value[nodeId] ?: return
        val targetPan = -node.pos
        val currentZoom = _transform.value.zoom
        val targetZoom = if (currentZoom < 0.8f) 1.2f else currentZoom

        animateTransform(targetPan, targetZoom)
    }

    private fun animateTransform(targetPan: Offset, targetZoom: Float) {
        viewModelScope.launch {
            val startPan = _transform.value.pan
            val startZoom = _transform.value.zoom
            val duration = 500L
            val startTime = System.currentTimeMillis()

            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed / duration.toFloat()).coerceIn(0f, 1f)
                val t = 1f - (1f - fraction) * (1f - fraction) * (1f - fraction)

                val currentPan = startPan + (targetPan - startPan) * t
                val currentZoom = startZoom + (targetZoom - startZoom) * t

                _transform.update { it.copy(pan = currentPan, zoom = currentZoom) }

                if (fraction >= 1f) break
                delay(16)
            }
        }
    }

    fun setSelectedNode(id: Long?) {
        _selectedNodeId.value = id
    }

    // --- Rectangular Selection API ---

    fun toggleSelectionMode() {
        _isSelectionMode.update { !it }
        _selectionRect.value = null
    }

    fun onSelectionDragStart(screenPos: Offset) {
        if (!_isSelectionMode.value) return
        selectionStartPos = screenPos
        _selectionRect.value = Rect(screenPos, screenPos)
    }

    fun onSelectionDrag(screenPos: Offset) {
        if (!_isSelectionMode.value) return
        val start = selectionStartPos ?: return
        _selectionRect.value = Rect(
            min(start.x, screenPos.x),
            min(start.y, screenPos.y),
            max(start.x, screenPos.x),
            max(start.y, screenPos.y)
        )
    }

    fun onSelectionDragEnd() {
        if (!_isSelectionMode.value) return
        val rect = _selectionRect.value ?: return
        _selectionRect.value = null
        selectionStartPos = null

        val selectedIds = mutableListOf<Long>()
        val transform = _transform.value
        val screenCenter = Offset(size.width / 2f, size.height / 2f)

        _graphNodes.value.values.forEach { node ->
            val nodeScreenPos = screenCenter + (node.pos + transform.pan) * transform.zoom
            if (rect.contains(nodeScreenPos)) {
                selectedIds.add(node.id)
            }
        }

        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                _nodeSelectionRequest.emit(selectedIds.take(2))
            }
        }
    }

    // --- Manipulation API ---

    fun toggleEditMode() {
        _isEditMode.update { !it }
        _pendingEdgeSourceId.value = null
    }

    fun onBackgroundTap(screenPos: Offset) {
        if (_isEditMode.value) {
            val worldPos = screenToWorld(screenPos)
            viewModelScope.launch {
                _createNodeRequest.emit(worldPos)
            }
        }
    }

    fun onNodeTap(nodeId: Long) {
        if (_isEditMode.value) {
            val pending = _pendingEdgeSourceId.value
            if (pending == null) {
                _pendingEdgeSourceId.value = nodeId
            } else {
                if (pending != nodeId) {
                    viewModelScope.launch {
                        _createEdgeRequest.emit(Pair(pending, nodeId))
                    }
                }
                _pendingEdgeSourceId.value = null
            }
        } else {
            // Expansion Logic
            toggleNodeExpansion(nodeId)
        }
    }

    // --- Graph Logic & Updates ---

    suspend fun updateGraphData(
        nodeList: List<NodeDisplayItem>,
        edgeList: List<EdgeDisplayItem>,
        schema: SchemaData?
    ) {
        lastNodeList = nodeList
        lastEdgeList = edgeList
        lastSchema = schema

        val isFirstLoad = _graphNodes.value.isEmpty()
        recomputeGraphState(calculateNewPositions = isFirstLoad, runStabilization = false)
    }

    fun onLayoutModeChanged(mode: GraphLayoutMode) {
        _layoutMode.value = mode
        when (mode) {
            GraphLayoutMode.CONTINUOUS -> _simulationRunning.value = true
            GraphLayoutMode.COMPUTED -> {
                _simulationRunning.value = false
                triggerComputedLayout(emptyMap())
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

    /**
     * Executes a specific detangle algorithm with user parameters.
     */
    fun runDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        _simulationRunning.value = false // Stop any active simulation to prevent fighting

        when (algorithm) {
            DetangleAlgorithm.FRUCHTERMAN_REINGOLD -> triggerComputedLayout(params)
            else -> triggerComputedLayout(params) // Fallback for other algo keys
        }
    }

    fun onTriggerLayoutAction() {
        when (_layoutMode.value) {
            GraphLayoutMode.CONTINUOUS -> {
                viewModelScope.launch { recomputeGraphState(calculateNewPositions = true, runStabilization = true) }
            }

            GraphLayoutMode.COMPUTED -> triggerComputedLayout(emptyMap())
            GraphLayoutMode.HIERARCHICAL -> triggerHierarchicalLayout()
        }
    }

    private fun triggerComputedLayout(params: Map<String, Any>) {
        _isProcessingLayout.value = true
        viewModelScope.launch(Dispatchers.Default) {
            // Merge defaults if not present
            val defaultParams = mapOf("iterations" to 1000, "area" to 2.0f, "gravity" to 0.1f)
            val finalParams = defaultParams + params

            val layoutFlow = runFRLayout(_graphNodes.value, _graphEdges.value, finalParams)
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
        viewModelScope.launch { recomputeGraphState(calculateNewPositions = false, runStabilization = false) }

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

    fun updateLodThreshold(threshold: Float) {
        _renderingSettings.update { it.copy(lodThreshold = threshold) }
    }

    fun toggleSnap(enabled: Boolean) {
        _snapEnabled.value = enabled
    }

    private suspend fun recomputeGraphState(calculateNewPositions: Boolean, runStabilization: Boolean) {
        withContext(Dispatchers.Default) {
            val collapsedIds = _collapsedNodes.value
            val expandedIds = _expandedNodes.value
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

            val visibleEdgesMap = mutableMapOf<Pair<Long, Long>, GraphEdge>()

            lastEdgeList.forEach { edge ->
                val visualSrc = edge.src.id
                val visualDst = edge.dst.id

                if (hiddenNodeIds.contains(visualSrc) || hiddenNodeIds.contains(visualDst)) return@forEach

                val pair = Pair(visualSrc, visualDst)
                val strength = if (edge.label == StandardSchemas.EDGE_CONTAINS) 0.5f else 0.05f

                val isBidirectional = lastEdgeList.any {
                    it.src.id == visualDst && it.dst.id == visualSrc
                }

                if (visibleEdgesMap[pair] == null) {
                    visibleEdgesMap[pair] = GraphEdge(
                        id = edge.id, sourceId = visualSrc, targetId = visualDst,
                        label = edge.label, strength = strength, colorInfo = labelToColor(edge.label),
                        isProxy = false, isBidirectional = isBidirectional
                    )
                }
            }

            val edges = visibleEdgesMap.values.toList()

            // Initialize Nodes
            val currentNodesSnapshot = _graphNodes.value
            val newNodeMap = mutableMapOf<Long, GraphNode>()

            var spiralIndex = 0

            lastNodeList.forEach { node ->
                val id = node.id
                if (hiddenNodeIds.contains(id)) return@forEach

                val existingNode = currentNodesSnapshot[id]
                val pos = if (calculateNewPositions) {
                    // Phyllotaxis Spiral
                    val angle = spiralIndex * 0.5f
                    val radius = 50f * sqrt(spiralIndex.toFloat())
                    spiralIndex++

                    Offset(
                        radius * cos(angle),
                        radius * sin(angle)
                    )
                } else {
                    existingNode?.pos ?: Offset.Zero
                }

                val locked = existingNode?.isLocked ?: false
                val isExpanded = expandedIds.contains(id)

                val nodeObj = createGraphNode(node, pos, id, collapsedIds.contains(id), locked, isExpanded)

                if (!calculateNewPositions && existingNode != null) {
                    nodeObj.vel = existingNode.vel
                }
                newNodeMap[id] = nodeObj
            }

            _graphNodes.value = newNodeMap
            _graphEdges.value = edges

            // Immediate Stabilization (Optional)
            if (calculateNewPositions || runStabilization) {
                // Run 150 ticks of physics synchronously to untangle
                val dt = 0.1f // High step for fast untangle
                // Explicit Type Fix: Use Map interface
                var tempNodes: Map<Long, GraphNode> = newNodeMap
                repeat(150) {
                    tempNodes = physicsEngine.update(tempNodes, edges, _physicsOptions.value, dt)
                }
                _graphNodes.value = tempNodes

                if (_layoutMode.value == GraphLayoutMode.CONTINUOUS) {
                    _simulationRunning.value = true
                }
            }
        }
    }

    fun toggleNodeExpansion(nodeId: Long) {
        _expandedNodes.update { current ->
            if (current.contains(nodeId)) current - nodeId else current + nodeId
        }
        val isExpanded = _expandedNodes.value.contains(nodeId)

        val node = _graphNodes.value[nodeId] ?: return
        val displayItem = lastNodeList.find { it.id == nodeId } ?: return

        // Deterministic size calculation
        val newSize = NodeSizeCalculator.calculate(displayItem, isExpanded, sizingContext)

        _graphNodes.update { nodes ->
            val mutable = nodes.toMutableMap()
            mutable[nodeId] = updateNodeDimensions(node, newSize, isExpanded)
            mutable
        }

        _simulationRunning.value = true
        settleFramesRemaining = 60
    }

    private fun updateNodeDimensions(node: GraphNode, size: Size, expanded: Boolean): GraphNode {
        // Calculate radius to encompass the rectangular content
        val r = (sqrt(size.width * size.width + size.height * size.height) / 2f) + 20f
        val w = size.width
        val h = size.height

        return when (node) {
            is TitleGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is HeadingGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is ShortTextGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is LongTextGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is CodeBlockGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is MapGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is SetGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is UnorderedListGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is OrderedListGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is TagGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is TableGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is ImageGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
            is ListGraphNode -> node.copy(radius = r, width = w, height = h, isExpanded = expanded)
        }
    }

    private fun createGraphNode(
        node: NodeDisplayItem,
        pos: Offset,
        id: Long,
        isCollapsed: Boolean,
        isLocked: Boolean,
        isExpanded: Boolean
    ): GraphNode {
        // 1. Deterministic Sizing (No UI measurement needed)
        val size = NodeSizeCalculator.calculate(node, isExpanded, sizingContext)
        val width = size.width
        val height = size.height
        val radius = (sqrt(width * width + height * height) / 2f) + 10f

        val topology = node.style.definition.topology
        val mass = when(topology) { NodeTopology.ROOT -> 60f; NodeTopology.BRANCH -> 30f; NodeTopology.LEAF -> 10f }

        val colorInfo = labelToColor(node.label)
        val absBgPath = node.backgroundImagePath?.let { File(mediaPath, it).absolutePath }
        val props = node.properties

        return when (node.style) {
            NodeStyle.TITLE, NodeStyle.DOCUMENT -> TitleGraphNode(
                title = node.displayProperty,
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.HEADING, NodeStyle.SECTION -> HeadingGraphNode(
                text = node.displayProperty,
                level = props[StandardSchemas.PROP_LEVEL]?.toIntOrNull() ?: 1,
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.SHORT_TEXT, NodeStyle.GENERIC -> ShortTextGraphNode(
                text = node.displayProperty,
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.TAG -> TagGraphNode(
                name = node.displayProperty,
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.ATTACHMENT -> {
                val mimeType = props[StandardSchemas.PROP_MIME_TYPE] ?: "application/octet-stream"
                if (mimeType.startsWith("image")) {
                    val relativePath = props[StandardSchemas.PROP_URI]
                    val fullPath = if (relativePath != null) File(mediaPath, relativePath).absolutePath else ""
                    ImageGraphNode(
                        uri = fullPath,
                        altText = node.displayProperty,
                        id = id, label = node.label, pos = pos, vel = Offset.Zero,
                        mass = mass, radius = radius, width = width, height = height,
                        colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
                    )
                } else {
                    ShortTextGraphNode(
                        text = "${node.displayProperty} ($mimeType)",
                        id = id, label = node.label, pos = pos, vel = Offset.Zero,
                        mass = mass, radius = radius, width = width, height = height,
                        colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
                    )
                }
            }
            NodeStyle.LONG_TEXT, NodeStyle.BLOCK -> LongTextGraphNode(
                content = props[StandardSchemas.PROP_CONTENT] ?: node.displayProperty,
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.CODE_BLOCK -> CodeBlockGraphNode(
                code = props[StandardSchemas.PROP_CONTENT] ?: "",
                language = props[StandardSchemas.PROP_LANGUAGE] ?: "text",
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.MAP -> MapGraphNode(
                data = com.tau.nexus_note.utils.PropertySerialization.deserializeMap(props[StandardSchemas.PROP_MAP_DATA] ?: "{}"),
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.SET -> SetGraphNode(
                items = com.tau.nexus_note.utils.PropertySerialization.deserializeList(props[StandardSchemas.PROP_LIST_ITEMS] ?: "[]").distinct(),
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.UNORDERED_LIST, NodeStyle.LIST -> UnorderedListGraphNode(
                items = com.tau.nexus_note.utils.PropertySerialization.deserializeList(props[StandardSchemas.PROP_LIST_ITEMS] ?: "[]"),
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.ORDERED_LIST -> OrderedListGraphNode(
                items = com.tau.nexus_note.utils.PropertySerialization.deserializeList(props[StandardSchemas.PROP_LIST_ITEMS] ?: "[]"),
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.TABLE -> TableGraphNode(
                headers = com.tau.nexus_note.utils.PropertySerialization.deserializeList(props[StandardSchemas.PROP_HEADERS] ?: "[]"),
                rows = com.tau.nexus_note.utils.PropertySerialization.deserializeListOfMaps(props[StandardSchemas.PROP_DATA] ?: "[]"),
                caption = props[StandardSchemas.PROP_CAPTION],
                id = id, label = node.label, pos = pos, vel = Offset.Zero,
                mass = mass, radius = radius, width = width, height = height,
                colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
            )
            NodeStyle.IMAGE -> {
                val relativePath = props[StandardSchemas.PROP_URI]
                val fullPath = if (relativePath != null) File(mediaPath, relativePath).absolutePath else ""

                ImageGraphNode(
                    uri = fullPath,
                    altText = props[StandardSchemas.PROP_ALT_TEXT] ?: node.displayProperty,
                    id = id, label = node.label, pos = pos, vel = Offset.Zero,
                    mass = mass, radius = radius, width = width, height = height,
                    colorInfo = colorInfo, isLocked = isLocked, isExpanded = isExpanded, backgroundImagePath = absBgPath
                )
            }
        }
    }

    // --- Drag Logic ---

    fun onDragStart(nodeId: Long) {
        _draggedNodeId.value = nodeId
        _dragVelocity.value = Offset.Zero
        _simulationRunning.value = true

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

    fun onPan(delta: Offset) {
        _transform.update { it.copy(pan = it.pan + (delta / it.zoom)) }
    }

    fun onZoom(zoomFactor: Float, zoomCenterScreen: Offset) {
        val newZoomFactor = 1.0f + (zoomFactor - 1.0f) * settingsFlow.value.graphInteraction.zoomSensitivity
        _transform.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * newZoomFactor).coerceIn(0.02f, 5.0f)
            val sizeCenter = Offset(size.width / 2f, size.height / 2f)
            val worldPos = (zoomCenterScreen - state.pan * oldZoom - sizeCenter) / oldZoom
            val newPan = (zoomCenterScreen - worldPos * newZoom - sizeCenter) / newZoom
            state.copy(pan = newPan, zoom = newZoom)
        }
    }

    fun setZoom(targetZoom: Float) {
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val currentZoom = _transform.value.zoom
        val zoomFactor = targetZoom / currentZoom
        onZoom(zoomFactor, screenCenter)
    }

    fun onResize(newSize: IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() {
        _showFabMenu.update { !it }
    }

    fun toggleSettings() {
        _showSettings.update { !it }
    }

    fun onCleared() {
        _simulationRunning.value = false
    }
}