package com.tau.nexusnote.codex.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.codex.graph.fcose.*
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.settings.SettingsData
import com.tau.nexusnote.utils.labelToColor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.sqrt

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val repository: CodexRepository
) {
    // --- Engine & State ---
    private val layoutEngine = LayoutEngine(SpectralLayout(), CoseLayout())

    private val _fcGraph = FcGraph()

    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _layoutConfig = MutableStateFlow(settingsFlow.value.graphPhysics.config)
    val layoutConfig = _layoutConfig.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    private val _draggedNodeId = MutableStateFlow<String?>(null)

    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()

    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()

    private val _simulationRunning = MutableStateFlow(false)
    val simulationRunning = _simulationRunning.asStateFlow()

    private var nodeSizeCalculator: NodeSizeCalculator? = null

    private var size = Size.Zero
    private var uiSyncJob: Job? = null

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _layoutConfig.value = settings.graphPhysics.config
                _renderingSettings.value = settings.graphRendering
            }
        }

        viewModelScope.launch {
            repository.constraints.collect { dbConstraints ->
                updateGraphConstraints(dbConstraints)
            }
        }
    }

    fun setNodeSizeCalculator(calculator: NodeSizeCalculator) {
        this.nodeSizeCalculator = calculator
        viewModelScope.launch(Dispatchers.Default) {
            recalculateNodeSizes()
        }
    }

    private suspend fun recalculateNodeSizes() {
        val calc = nodeSizeCalculator ?: return

        _fcGraph.nodes.forEach { node ->
            if (!node.isCompound() && !node.isDummy && !node.isRepresentative) {
                val data = node.data

                // Add buffer to physics body to ensure visual gap
                val physicsBuffer = 20.0

                if (data is NodeDisplayItem) {
                    val size = calc.measure(data.content, data.config)
                    node.width = size.width.toDouble() + physicsBuffer
                    node.height = size.height.toDouble() + physicsBuffer
                } else if (data is EdgeDisplayItem) {
                    val content = NodeContent.TextContent(data.label)
                    val size = calc.measure(content, null)
                    node.width = size.width.toDouble() + physicsBuffer
                    node.height = size.height.toDouble() + physicsBuffer
                }
            }
        }
        _fcGraph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
        pushUiUpdate()
    }

    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        val currentFcNodes = _fcGraph.nodes.associateBy { it.id }
        val activeIds = mutableSetOf<String>()
        val calc = nodeSizeCalculator
        val baseWidth = (settingsFlow.value.graphInteraction.nodeBaseRadius * 2.0).toDouble()
        val physicsBuffer = 20.0

        nodeList.forEach { item ->
            val idStr = item.id.toString()
            activeIds.add(idStr)

            val existing = currentFcNodes[idStr]
            if (existing != null) {
                existing.data = item
                if (!existing.isCompound() && calc != null) {
                    val size = calc.measure(item.content, item.config)
                    existing.width = size.width.toDouble() + physicsBuffer
                    existing.height = size.height.toDouble() + physicsBuffer
                }
            } else {
                val newNode = _fcGraph.addNode(id = idStr)
                newNode.data = item

                if (calc != null) {
                    val size = calc.measure(item.content, item.config)
                    newNode.width = size.width.toDouble() + physicsBuffer
                    newNode.height = size.height.toDouble() + physicsBuffer
                } else {
                    newNode.width = baseWidth
                    newNode.height = baseWidth
                }

                newNode.x = (Math.random() - 0.5) * 100.0
                newNode.y = (Math.random() - 0.5) * 100.0
            }
        }

        edgeList.forEach { naryEdge ->
            val hyperNodeId = "edge_${naryEdge.id}"
            activeIds.add(hyperNodeId)

            val existing = currentFcNodes[hyperNodeId] ?: _fcGraph.addNode(id = hyperNodeId, isDummy = false)
            existing.data = naryEdge

            if (calc != null) {
                val labelContent = NodeContent.TextContent(naryEdge.label)
                val size = calc.measure(labelContent, null)
                existing.width = size.width.toDouble() + physicsBuffer
                existing.height = size.height.toDouble() + physicsBuffer
            } else {
                existing.width = baseWidth
                existing.height = baseWidth
            }

            if (existing.x == 0.0 && existing.y == 0.0) {
                existing.x = (Math.random() - 0.5) * 100.0
                existing.y = (Math.random() - 0.5) * 100.0
            }
        }

        val nodesToRemove = _fcGraph.nodes.filter { it.id !in activeIds }
        nodesToRemove.forEach { _fcGraph.removeNode(it) }

        _fcGraph.nodes.forEach {
            it.parent = null
            it.children.clear()
        }
        nodeList.forEach { item ->
            if (item.parentId != null) {
                val child = _fcGraph.getNode(item.id.toString())
                val parent = _fcGraph.getNode(item.parentId.toString())
                if (child != null && parent != null && child != parent) {
                    child.parent = parent
                    parent.children.add(child)
                }
            }
        }

        _fcGraph.edges.clear()
        edgeList.forEach { edge ->
            val hyperNodeId = "edge_${edge.id}"
            edge.participatingNodes.forEach { participant ->
                val participantId = participant.node.id.toString()
                _fcGraph.addEdge(sourceId = hyperNodeId, targetId = participantId)
            }
        }

        val uiEdges = mutableListOf<GraphEdge>()
        var edgeIdCounter = -100000L

        val currentSchemaData = repository.schema.value
        val edgeSchemaMap = currentSchemaData?.edgeSchemas?.associateBy { it.id } ?: emptyMap()

        edgeList.forEach { edge ->
            val hyperNodeUiId = -1 * edge.id
            val schema = edgeSchemaMap[edge.schemaId]

            edge.participatingNodes.forEach { participant ->
                val nodeUiId = participant.node.id
                val roleName = participant.role ?: ""
                val roleDef = schema?.roleDefinitions?.find { it.name == roleName }
                val direction = roleDef?.direction ?: RoleDirection.Target
                val isSource = direction == RoleDirection.Source

                val sId = if (isSource) nodeUiId else hyperNodeUiId
                val tId = if (isSource) hyperNodeUiId else nodeUiId

                uiEdges.add(
                    GraphEdge(
                        id = edgeIdCounter--,
                        sourceId = sId,
                        targetId = tId,
                        label = "",
                        roleLabel = roleName,
                        strength = 1.0f,
                        colorInfo = labelToColor(edge.label)
                    )
                )
            }
        }
        _graphEdges.value = uiEdges

        _fcGraph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }

        viewModelScope.launch(Dispatchers.Default) {
            recalculateNodeSizes()
        }

        pushUiUpdate()
    }

    private fun updateGraphConstraints(constraints: List<LayoutConstraintItem>) {
        _fcGraph.fixedConstraints.clear()
        _fcGraph.alignConstraints.clear()
        _fcGraph.relativeConstraints.clear()

        constraints.forEach { item ->
            val nodes = item.nodeIds.mapNotNull { id -> _fcGraph.getNode(id.toString()) }

            when (item.type) {
                "ALIGN_VERTICAL" -> {
                    if (nodes.isNotEmpty()) {
                        _fcGraph.alignConstraints.add(AlignmentConstraint(nodes, AlignmentDirection.VERTICAL))
                    }
                }
                "ALIGN_HORIZONTAL" -> {
                    if (nodes.isNotEmpty()) {
                        _fcGraph.alignConstraints.add(AlignmentConstraint(nodes, AlignmentDirection.HORIZONTAL))
                    }
                }
                "RELATIVE_LR" -> {
                    if (nodes.size >= 2) {
                        _fcGraph.relativeConstraints.add(
                            RelativeConstraint(nodes[0], nodes[1], AlignmentDirection.HORIZONTAL)
                        )
                    }
                }
                "RELATIVE_TB" -> {
                    if (nodes.size >= 2) {
                        _fcGraph.relativeConstraints.add(
                            RelativeConstraint(nodes[0], nodes[1], AlignmentDirection.VERTICAL)
                        )
                    }
                }
            }
        }
        runEnforce()
    }

    private fun pushUiUpdate() {
        val newMap = _fcGraph.nodes.associate { fcNode ->
            val id: Long
            val label: String
            val property: String
            val isHyper: Boolean
            val colorInfo: ColorInfo
            val content: NodeContent?
            val config: SchemaConfig?

            val data = fcNode.data
            if (data is NodeDisplayItem) {
                id = data.id
                label = data.label
                property = data.displayProperty
                isHyper = false
                colorInfo = labelToColor(label)
                content = data.content
                config = data.config
            } else if (data is EdgeDisplayItem) {
                id = -1 * data.id
                label = data.label
                property = data.label
                isHyper = true
                colorInfo = labelToColor(label)
                content = null
                config = null
            } else {
                id = fcNode.id.toLongOrNull() ?: fcNode.id.hashCode().toLong()
                label = "Unknown"
                property = "Unknown"
                isHyper = false
                colorInfo = labelToColor("Unknown")
                content = null
                config = null
            }

            val radius = (sqrt(fcNode.width * fcNode.width + fcNode.height * fcNode.height) / 2.0).toFloat()

            val uiNode = GraphNode(
                id = id,
                label = label,
                displayProperty = property,
                pos = Offset(fcNode.getCenter().first.toFloat(), fcNode.getCenter().second.toFloat()),
                radius = radius,
                width = fcNode.width.toFloat(),
                height = fcNode.height.toFloat(),
                isCompound = fcNode.isCompound(),
                isHyperNode = isHyper,
                content = content,
                config = config,
                colorInfo = colorInfo,
                isFixed = fcNode.isFixed
            )
            id to uiNode
        }
        _graphNodes.value = newMap
    }

    suspend fun runSimulationLoop() {
        startUiSync()
        try {
            awaitCancellation()
        } finally {
            stopUiSync()
        }
    }

    private fun startUiSync() {
        if (uiSyncJob?.isActive == true) return
        uiSyncJob = viewModelScope.launch {
            while (isActive) {
                pushUiUpdate()
                delay(32)
            }
        }
    }

    private fun stopUiSync() {
        uiSyncJob?.cancel()
        pushUiUpdate()
    }

    fun runRandomize() {
        viewModelScope.launch {
            layoutEngine.randomize(_fcGraph)
            pushUiUpdate()
        }
    }

    fun runDraft() {
        viewModelScope.launch(Dispatchers.Default) {
            _isDetangling.value = true
            layoutEngine.runDraft(_fcGraph, _layoutConfig.value)
            pushUiUpdate()
            _isDetangling.value = false
        }
    }

    fun runTransform() {
        viewModelScope.launch {
            layoutEngine.runTransform(_fcGraph)
            pushUiUpdate()
        }
    }

    fun runEnforce() {
        viewModelScope.launch {
            layoutEngine.runEnforce(_fcGraph)
            pushUiUpdate()
        }
    }

    fun runPolishing(burst: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            _isDetangling.value = true
            startUiSync()

            val config = _layoutConfig.value
            if (burst) {
                layoutEngine.runPolishing(_fcGraph, config.copy(
                    maxIterations = 60,
                    initialTemp = 50.0
                ))
            } else {
                layoutEngine.runPolishing(_fcGraph, config)
            }

            stopUiSync()
            _isDetangling.value = false
        }
    }

    fun addConstraint(type: ConstraintUiType, nodeIds: List<Long>) {
        if (nodeIds.isEmpty()) return

        val dbType = when(type) {
            ConstraintUiType.ALIGN_VERTICAL -> "ALIGN_VERTICAL"
            ConstraintUiType.ALIGN_HORIZONTAL -> "ALIGN_HORIZONTAL"
            ConstraintUiType.RELATIVE_LR -> "RELATIVE_LR"
            ConstraintUiType.RELATIVE_TB -> "RELATIVE_TB"
        }

        val params = emptyMap<String, Any>()
        repository.addConstraint(dbType, nodeIds, params)
    }

    fun groupSelectedNodes(nodeIds: List<Long>) {
        if (nodeIds.isEmpty()) return
        repository.createCompoundNode("New Group", nodeIds)
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

    fun onDragStart(screenPos: Offset): Boolean {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = _fcGraph.nodes.find { node ->
            val (cx, cy) = node.getCenter()
            if (node.isCompound()) {
                val halfW = node.width / 2
                val halfH = node.height / 2
                val l = cx - halfW
                val r = cx + halfW
                val t = cy - halfH
                val b = cy + halfH
                worldPos.x in l..r && worldPos.y in t..b
            } else {
                val dx = cx - worldPos.x
                val dy = cy - worldPos.y
                (dx*dx + dy*dy) < (node.width/2 * node.width/2)
            }
        }

        return if (tappedNode != null) {
            _draggedNodeId.value = tappedNode.id
            tappedNode.isFixed = true
            pushUiUpdate()
            true
        } else {
            false
        }
    }

    fun onDrag(screenDelta: Offset) {
        val id = _draggedNodeId.value ?: return
        val node = _fcGraph.getNode(id) ?: return
        val worldDelta = screenDeltaToWorldDelta(screenDelta)

        node.x += worldDelta.x
        node.y += worldDelta.y
        node.parent?.updateBoundsFromChildren()

        viewModelScope.launch(Dispatchers.Default) {
            val config = _layoutConfig.value.copy(maxIterations = 5, initialTemp = 20.0)
            layoutEngine.runPolishing(_fcGraph, config)
            pushUiUpdate()
        }
    }

    fun onDragEnd() {
        val id = _draggedNodeId.value ?: return
        val node = _fcGraph.getNode(id)
        if (node != null) {
            node.isFixed = false
        }
        _draggedNodeId.value = null
        runPolishing(burst = true)
    }

    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        val worldPos = screenToWorld(screenPos)
        val candidates = _fcGraph.nodes.filter { node ->
            val (cx, cy) = node.getCenter()
            if (node.isCompound()) {
                val halfW = node.width / 2
                val halfH = node.height / 2
                worldPos.x in (cx - halfW)..(cx + halfW) && worldPos.y in (cy - halfH)..(cy + halfH)
            } else {
                val dx = cx - worldPos.x
                val dy = cy - worldPos.y
                (dx*dx + dy*dy) < (node.width/2 * node.width/2)
            }
        }
        val tappedNode = candidates.minByOrNull { it.width * it.height }

        if (tappedNode != null) {
            val id = when (val data = tappedNode.data) {
                is NodeDisplayItem -> data.id
                is EdgeDisplayItem -> -1 * data.id
                else -> tappedNode.id.toLongOrNull()
            }
            if (id != null) {
                onNodeTapped(id)
            }
        }
    }

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() {
        _showFabMenu.update { !it }
    }

    fun toggleSettings() {
        _showSettings.update { !it }
    }

    fun startSimulation(fullPipeline: Boolean = false) {
        _simulationRunning.value = true
        if (_fcGraph.nodes.isNotEmpty()) {
            if (fullPipeline) {
                runDraft()
            } else {
                runPolishing(burst = true)
            }
        }
    }

    fun stopSimulation() {
        _simulationRunning.value = false
        stopUiSync()
    }

    fun onCleared() {
        stopSimulation()
    }

    fun onShowDetangleDialog() { _showDetangleDialog.value = true }
    fun onDismissDetangleDialog() { _showDetangleDialog.value = false }
    fun startDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        runDraft()
    }
}