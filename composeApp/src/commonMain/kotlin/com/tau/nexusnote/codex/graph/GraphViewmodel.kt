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

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val repository: CodexRepository
) {
    // --- Engine & State ---
    private val layoutEngine = LayoutEngine(SpectralLayout(), CoseLayout())

    // The Layout Model (Source of Truth for Positions)
    private val _fcGraph = FcGraph()

    // The UI Model (Derived from FcGraph for rendering)
    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    // --- Configuration & Settings ---
    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    // --- Interaction State ---
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

    private var size = Size.Zero
    private var uiSyncJob: Job? = null

    init {
        // Observer for settings updates
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
            }
        }

        // Observer for Repository Constraints (Phase 6 Sync)
        viewModelScope.launch {
            repository.constraints.collect { dbConstraints ->
                updateGraphConstraints(dbConstraints)
            }
        }
    }

    // --- Data Synchronization (SQLite -> FcGraph) ---

    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        val currentFcNodes = _fcGraph.nodes.associateBy { it.id }
        val activeIds = mutableSetOf<String>()

        // 1. Add Standard Nodes
        nodeList.forEach { item ->
            val idStr = item.id.toString()
            activeIds.add(idStr)

            val existing = currentFcNodes[idStr]
            if (existing != null) {
                existing.data = item
                if (!existing.isCompound()) {
                    existing.width = _physicsOptions.value.nodeBaseRadius * 2.0
                    existing.height = existing.width
                }
            } else {
                val newNode = _fcGraph.addNode(id = idStr)
                newNode.data = item
                newNode.width = _physicsOptions.value.nodeBaseRadius * 2.0
                newNode.height = newNode.width
                newNode.x = (Math.random() - 0.5) * 100.0
                newNode.y = (Math.random() - 0.5) * 100.0
            }
        }

        // 2. Process N-nary Edges -> Convert to Hypernodes + Binary Edges
        edgeList.forEach { naryEdge ->
            val hyperNodeId = "edge_${naryEdge.id}"
            activeIds.add(hyperNodeId)

            // A. Create the "Hypernode" (The visual representation of the Edge)
            val existing = currentFcNodes[hyperNodeId]
            // Hypernodes are slightly smaller than regular nodes
            val hyperNodeSize = _physicsOptions.value.nodeBaseRadius * 1.2

            if (existing != null) {
                existing.data = naryEdge
                existing.width = hyperNodeSize
                existing.height = hyperNodeSize
            } else {
                val hyperNode = _fcGraph.addNode(id = hyperNodeId, isDummy = false)
                hyperNode.data = naryEdge
                hyperNode.width = hyperNodeSize
                hyperNode.height = hyperNodeSize
                // Randomize position initially
                hyperNode.x = (Math.random() - 0.5) * 100.0
                hyperNode.y = (Math.random() - 0.5) * 100.0
            }

            // B. Create Binary Edges (Hypernode <-> ParticipantNode) for Layout Engine
            // Note: We clear old edges first in step 4 below to ensure cleanliness
        }

        // 3. Remove Stale Nodes
        val nodesToRemove = _fcGraph.nodes.filter { it.id !in activeIds }
        nodesToRemove.forEach { _fcGraph.removeNode(it) }

        // 4. Rebuild Hierarchy (Nodes only)
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

        // 5. Sync Edges (Rebuild connectivity for Layout Engine)
        _fcGraph.edges.clear()
        edgeList.forEach { edge ->
            val hyperNodeId = "edge_${edge.id}"
            edge.participatingNodes.forEach { participant ->
                val participantId = participant.node.id.toString()
                _fcGraph.addEdge(sourceId = hyperNodeId, targetId = participantId)
            }
        }

        // 6. Generate UI Edges (Visual Lines)
        val uiEdges = mutableListOf<GraphEdge>()
        var edgeIdCounter = -100000L // Arbitrary start for purely visual edge IDs

        // Cache Schema Map for efficient lookup inside the loop
        val currentSchemaData = repository.schema.value
        val edgeSchemaMap = currentSchemaData?.edgeSchemas?.associateBy { it.id } ?: emptyMap()

        edgeList.forEach { edge ->
            val hyperNodeUiId = -1 * edge.id // Negative ID represents a hypernode
            val schema = edgeSchemaMap[edge.schemaId]

            edge.participatingNodes.forEach { participant ->
                val nodeUiId = participant.node.id
                val roleName = participant.role ?: ""

                // Lookup Role Definition to determine direction
                // Default to TARGET (Hypernode -> Node) if not found or unspecified
                val roleDef = schema?.roleDefinitions?.find { it.name == roleName }
                val direction = roleDef?.direction ?: RoleDirection.Target

                // SOURCE: Arrow points FROM Node TO Hypernode
                // TARGET: Arrow points FROM Hypernode TO Node
                val isSource = direction == RoleDirection.Source

                val sId = if (isSource) nodeUiId else hyperNodeUiId
                val tId = if (isSource) hyperNodeUiId else nodeUiId

                uiEdges.add(
                    GraphEdge(
                        id = edgeIdCounter--,
                        sourceId = sId,
                        targetId = tId,
                        label = "", // Spoke edges usually don't need labels
                        roleLabel = roleName, // Pass the role name to be rendered
                        strength = 1.0f,
                        colorInfo = labelToColor(edge.label)
                    )
                )
            }
        }
        _graphEdges.value = uiEdges

        _fcGraph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
        pushUiUpdate()
    }

    private fun updateGraphConstraints(constraints: List<LayoutConstraintItem>) {
        // Clear existing
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
        // Force an update to the constraint processor if it exists
        runEnforce()
    }


    // --- UI Synchronization ---

    private fun pushUiUpdate() {
        val newMap = _fcGraph.nodes.associate { fcNode ->
            val id: Long
            val label: String
            val property: String
            val isHyper: Boolean
            val colorInfo: ColorInfo

            val data = fcNode.data
            if (data is NodeDisplayItem) {
                id = data.id
                label = data.label
                property = data.displayProperty
                isHyper = false
                colorInfo = labelToColor(label)
            } else if (data is EdgeDisplayItem) {
                // Map Edge ID to a negative Long to distinguish from Node IDs
                id = -1 * data.id
                label = data.label
                property = data.label // Display the edge label on the hypernode
                isHyper = true
                colorInfo = labelToColor(label)
            } else {
                // Fallback for nodes without data (shouldn't happen often)
                id = fcNode.id.toLongOrNull() ?: fcNode.id.hashCode().toLong()
                label = "Unknown"
                property = "Unknown"
                isHyper = false
                colorInfo = labelToColor("Unknown")
            }

            val radius = (fcNode.width / 2.0).toFloat()

            val uiNode = GraphNode(
                id = id,
                label = label,
                displayProperty = property,
                pos = Offset(fcNode.getCenter().first.toFloat(), fcNode.getCenter().second.toFloat()),
                vel = Offset(0f, 0f),
                mass = 1.0f,
                radius = radius,
                width = fcNode.width.toFloat(),
                height = fcNode.height.toFloat(),
                isCompound = fcNode.isCompound(),
                isHyperNode = isHyper,
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

    // --- Layout Pipeline Steps ---

    fun runRandomize() {
        viewModelScope.launch {
            layoutEngine.randomize(_fcGraph)
            pushUiUpdate()
        }
    }

    fun runDraft() {
        viewModelScope.launch(Dispatchers.Default) {
            _isDetangling.value = true
            layoutEngine.runDraft(_fcGraph, LayoutConfig())
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

            val config = LayoutConfig()
            if (burst) {
                config.maxIterations = 60
                config.initialTemp = 50.0
            }

            layoutEngine.runPolishing(_fcGraph, config)

            stopUiSync()
            _isDetangling.value = false
        }
    }

    // --- Constraint Management ---

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

    // --- Gesture Handlers ---

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

        // Just update UI to show movement, do not run physics simulation
        pushUiUpdate()
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
            // Resolve the visual ID (Long) from the internal Node (String ID/Data)
            val id = when (val data = tappedNode.data) {
                is NodeDisplayItem -> data.id
                is EdgeDisplayItem -> -1 * data.id // Ensure we pass the negative ID for hypernodes
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

    fun startSimulation() {
        _simulationRunning.value = true
        if (_fcGraph.nodes.isNotEmpty()) {
            runPolishing(burst = true)
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