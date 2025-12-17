package com.tau.nexusnote.codex.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.codex.graph.fcose.*
import com.tau.nexusnote.codex.graph.physics.DetangleEngine
import com.tau.nexusnote.codex.graph.physics.PhysicsEngine
import com.tau.nexusnote.codex.graph.physics.PhysicsOptions
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.settings.SettingsData
import com.tau.nexusnote.utils.labelToColor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>,
    private val repository: CodexRepository
) {
    // --- Engines ---
    // 1. One-Shot Layout Engine (fCoSE, Spectral, etc.)
    private val layoutEngine = LayoutEngine(SpectralLayout(), CoseLayout())

    // 2. Continuous Physics Engine (ForceAtlas2)
    private val physicsEngine = PhysicsEngine()

    // The Layout Model (Source of Truth for One-Shot Algorithms and Hierarchy)
    private val _fcGraph = FcGraph()
    // Mutex to guard access to _fcGraph during structural modifications and layout iterations
    private val graphMutex = Mutex()

    // The UI Model (Derived from FcGraph, updated by PhysicsEngine)
    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    // --- Configuration & Settings ---
    // PhysicsOptions: Used by Continuous Physics
    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    // LayoutConfig: Used by One-Shot Layouts (fCoSE)
    private val _layoutConfig = MutableStateFlow(LayoutConfig())
    val layoutConfig = _layoutConfig.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    // --- Interaction State ---
    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    private val _draggedNodeId = MutableStateFlow<Long?>(null) // ID of the node currently being dragged

    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    // Tracks if a heavy One-Shot layout is running (blocks physics)
    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()

    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()

    // --- Simulation State ---
    // Tracks if the user wants simulation enabled in general
    private val _simulationEnabled = MutableStateFlow(false)
    val simulationEnabled = _simulationEnabled.asStateFlow()

    // Tracks if the simulation is currently asleep due to low energy
    private val _isSimulationPaused = MutableStateFlow(false)
    val isSimulationPaused = _isSimulationPaused.asStateFlow()

    // --- Layout State ---
    private val _selectedDetangler = MutableStateFlow(DetangleAlgorithm.FRUCHTERMAN_REINGOLD)
    val selectedDetangler = _selectedDetangler.asStateFlow()

    private var size = Size.Zero

    init {
        // Observer for settings updates
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
                // Wake up if physics parameters change
                wakeSimulation()
            }
        }

        // Observer for Repository Constraints
        viewModelScope.launch {
            repository.constraints.collect { dbConstraints ->
                updateGraphConstraints(dbConstraints)
            }
        }

        // Start the continuous physics loop
        startContinuousPhysicsLoop()
    }

    // --- Continuous Physics Loop ---

    private fun startContinuousPhysicsLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                // EXPLICIT LOGIC: Pause physics if detangling is active
                val shouldRun = _simulationEnabled.value &&
                        !_isSimulationPaused.value &&
                        !_isDetangling.value &&
                        _graphNodes.value.isNotEmpty()

                if (shouldRun) {
                    val nodes = _graphNodes.value
                    val edges = _graphEdges.value
                    val options = _physicsOptions.value
                    val dt = 0.016f // Fixed time step (~60 FPS)

                    // 1. Run Physics Step
                    val updatedNodes = physicsEngine.update(nodes, edges, options, dt)

                    // 2. Calculate System Energy (Sum of velocities)
                    // Used to auto-pause when the system stabilizes
                    var totalEnergy = 0.0
                    updatedNodes.values.forEach { node ->
                        totalEnergy += node.vel.getDistance()
                    }

                    // 3. Auto-Pause Logic
                    // Threshold is 0.5 pixels/frame total movement.
                    // Don't pause if the user is actively dragging a node.
                    val isDragging = _draggedNodeId.value != null
                    if (totalEnergy < 0.5 && !isDragging) {
                        _isSimulationPaused.value = true
                    }

                    // 4. Update UI State
                    _graphNodes.value = updatedNodes

                    // 5. Sync back to FcGraph (The source of truth for One-Shot layouts)
                    // This ensures that if we run fCoSE later, it starts from current positions.
                    // We try to acquire the lock; if busy (e.g. data update happening), skip sync this frame
                    if (graphMutex.tryLock()) {
                        try {
                            syncFcGraphFromUi(updatedNodes)
                        } finally {
                            graphMutex.unlock()
                        }
                    }
                }

                delay(16) // ~60 FPS loop
            }
        }
    }

    /**
     * Wakes the simulation up from auto-pause.
     * Call this on any interaction (drag, pan, zoom) or setting change.
     */
    fun wakeSimulation() {
        _isSimulationPaused.value = false
    }

    fun startSimulation() {
        _simulationEnabled.value = true
        wakeSimulation()
    }

    fun stopSimulation() {
        _simulationEnabled.value = false
    }

    // --- Data Synchronization (SQLite -> FcGraph -> UI) ---

    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        viewModelScope.launch {
            graphMutex.withLock {
                val currentFcNodes = _fcGraph.nodes.associateBy { it.id }
                val activeIds = mutableSetOf<String>()

                // 1. Add Standard Nodes
                nodeList.forEach { item ->
                    val idStr = item.id.toString()
                    activeIds.add(idStr)

                    val existing = currentFcNodes[idStr]
                    if (existing != null) {
                        existing.data = item
                        // Don't resize compound nodes here, they are calculated
                        if (!existing.isCompound()) {
                            existing.width = _physicsOptions.value.nodeBaseRadius * 2.0
                            existing.height = existing.width
                        }
                    } else {
                        val newNode = _fcGraph.addNode(id = idStr)
                        newNode.data = item
                        newNode.width = _physicsOptions.value.nodeBaseRadius * 2.0
                        newNode.height = newNode.width
                        // Initialize randomly to avoid stacking
                        newNode.x = (Math.random() - 0.5) * 200.0
                        newNode.y = (Math.random() - 0.5) * 200.0
                    }
                }

                // 2. Process N-nary Edges -> Convert to Hypernodes
                edgeList.forEach { naryEdge ->
                    val hyperNodeId = "edge_${naryEdge.id}"
                    activeIds.add(hyperNodeId)

                    val existing = currentFcNodes[hyperNodeId]
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
                        hyperNode.x = (Math.random() - 0.5) * 200.0
                        hyperNode.y = (Math.random() - 0.5) * 200.0
                    }
                }

                // 3. Remove Stale Nodes
                val nodesToRemove = _fcGraph.nodes.filter { it.id !in activeIds }
                nodesToRemove.forEach { _fcGraph.removeNode(it) }

                // 4. Rebuild Hierarchy
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

                // 5. Sync Edges (Topology for fCoSE)
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

                // Update compound bounds based on children
                _fcGraph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }

                // Initial UI Push
                pushUiUpdate()
            }

            // Trigger Startup Layout Pipeline
            if (_graphNodes.value.isNotEmpty()) {
                // 1. Run the heavy one-shot layout to organize everything
                runDetangle(DetangleAlgorithm.FCOSE, emptyMap())
            }
        }
    }

    private fun updateGraphConstraints(constraints: List<LayoutConstraintItem>) {
        viewModelScope.launch {
            graphMutex.withLock {
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
            }
        }
    }

    private fun pushUiUpdate() {
        val newMap = _fcGraph.nodes.associate { fcNode ->
            val id: Long
            val label: String
            val property: String
            val isHyper: Boolean
            val colorInfo: ColorInfo

            when (val data = fcNode.data) {
                is NodeDisplayItem -> {
                    id = data.id
                    label = data.label
                    property = data.displayProperty
                    isHyper = false
                    colorInfo = labelToColor(label)
                }

                is EdgeDisplayItem -> {
                    id = -1 * data.id
                    label = data.label
                    property = data.label
                    isHyper = true
                    colorInfo = labelToColor(label)
                }

                else -> {
                    id = fcNode.id.toLongOrNull() ?: fcNode.id.hashCode().toLong()
                    label = "Unknown"
                    property = "Unknown"
                    isHyper = false
                    colorInfo = labelToColor("Unknown")
                }
            }

            // Preservation of Compound flag is derived from FcNode state
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
                isCompound = fcNode.isCompound(), // STRICT PRESERVATION
                isHyperNode = isHyper,
                colorInfo = colorInfo,
                isFixed = fcNode.isFixed
            )
            id to uiNode
        }
        _graphNodes.value = newMap
    }

    /**
     * Syncs positions from the UI GraphNodes back to the Domain FcNodes.
     * This ensures that if we run fCoSE later, it starts from the current state.
     */
    private fun syncFcGraphFromUi(uiNodes: Map<Long, GraphNode>) {
        uiNodes.values.forEach { uiNode ->
            val idStr = if (uiNode.isHyperNode) "edge_${-uiNode.id}" else uiNode.id.toString()
            _fcGraph.getNode(idStr)?.let { fcNode ->
                fcNode.setCenter(uiNode.pos.x.toDouble(), uiNode.pos.y.toDouble())
            }
        }
    }

    // --- The Detangler Interface (Phase 2 Logic) ---

    /**
     * The Master Function for all One-Shot Layouts.
     * Explicitly pauses the continuous physics loop to prevent force fighting.
     */
    fun runDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.Default) {
            // 1. Lock Physics
            _isDetangling.value = true

            try {
                when (algorithm) {
                    DetangleAlgorithm.FCOSE -> {
                        // fCoSE: Runs the full pipeline directly on the FcGraph
                        // Order: Randomize -> Draft -> Transform -> Enforce -> Polish
                        val config = _layoutConfig.value

                        graphMutex.withLock {
                            layoutEngine.randomize(_fcGraph)
                            pushUiUpdate()
                        }
                        delay(200) // Visual flair

                        graphMutex.withLock {
                            layoutEngine.runDraft(_fcGraph, config)
                            pushUiUpdate()
                        }
                        delay(100)

                        graphMutex.withLock {
                            layoutEngine.runTransform(_fcGraph)
                            pushUiUpdate()
                        }

                        graphMutex.withLock {
                            layoutEngine.runEnforce(_fcGraph)
                            pushUiUpdate()
                        }

                        graphMutex.withLock {
                            layoutEngine.runPolishing(_fcGraph, config)
                            pushUiUpdate()
                        }
                    }

                    DetangleAlgorithm.FRUCHTERMAN_REINGOLD, DetangleAlgorithm.KAMADA_KAWAI -> {
                        // Non-Compound Aware Algorithms
                        // Strategy: Filter to LEAF nodes only, run algorithm, then update compound bounds.

                        // 1. Filter Nodes
                        val leafNodes = _graphNodes.value.filter { !it.value.isCompound }
                        val edges = _graphEdges.value

                        // 2. Select Flow
                        val flow = if (algorithm == DetangleAlgorithm.FRUCHTERMAN_REINGOLD) {
                            DetangleEngine.runFRLayout(leafNodes, edges, params)
                        } else {
                            DetangleEngine.runKKLayout(leafNodes, edges, params)
                        }

                        // 3. Collect and Apply
                        flow.collect { updatedLeaves ->
                            // Update the master UI map with new leaf positions
                            val currentMap = _graphNodes.value.toMutableMap()
                            updatedLeaves.forEach { (id, node) -> currentMap[id] = node }

                            // 4. PRESERVE HIERARCHY:
                            // Sync updated leaves to FcGraph, then ask FcGraph to recalculate compound bounds
                            graphMutex.withLock {
                                syncFcGraphFromUi(currentMap)
                                _fcGraph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
                                // 5. Push final result (Leaves + Updated Compounds) back to UI
                                pushUiUpdate()
                            }
                        }
                    }

                    else -> {
                        // Not implemented
                        println("Algorithm $algorithm not implemented yet.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // 2. Unlock Physics
                _isDetangling.value = false
                wakeSimulation()
            }
        }
    }

    // Individual steps exposed for UI control panel (fCoSE specific)
    fun runFullFcosePipeline() {
        runDetangle(DetangleAlgorithm.FCOSE, emptyMap())
    }

    fun runRandomize() {
        viewModelScope.launch {
            graphMutex.withLock {
                layoutEngine.randomize(_fcGraph)
                pushUiUpdate()
            }
            wakeSimulation()
        }
    }

    fun runDraft() {
        viewModelScope.launch(Dispatchers.Default) {
            _isDetangling.value = true
            graphMutex.withLock {
                layoutEngine.runDraft(_fcGraph, _layoutConfig.value)
                pushUiUpdate()
            }
            _isDetangling.value = false
            wakeSimulation()
        }
    }

    fun runTransform() {
        viewModelScope.launch {
            graphMutex.withLock {
                layoutEngine.runTransform(_fcGraph)
                pushUiUpdate()
            }
        }
    }

    fun runEnforce() {
        viewModelScope.launch {
            graphMutex.withLock {
                layoutEngine.runEnforce(_fcGraph)
                pushUiUpdate()
            }
        }
    }

    fun runPolishing() {
        viewModelScope.launch(Dispatchers.Default) {
            _isDetangling.value = true
            graphMutex.withLock {
                layoutEngine.runPolishing(_fcGraph, _layoutConfig.value)
                pushUiUpdate()
            }
            _isDetangling.value = false
            wakeSimulation()
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

    // --- Settings Updates ---

    fun updateLayoutConfig(config: LayoutConfig) {
        _layoutConfig.value = config
    }

    fun updatePhysicsOptions(options: PhysicsOptions) {
        _physicsOptions.value = options
        wakeSimulation()
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
        wakeSimulation()
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
        wakeSimulation()
    }

    fun onDragStart(screenPos: Offset): Boolean {
        val worldPos = screenToWorld(screenPos)
        // Hit test against GraphNodes
        val nodes = _graphNodes.value.values
        val tappedNode = nodes.filter { node ->
            val distSq = (node.pos.x - worldPos.x) * (node.pos.x - worldPos.x) +
                    (node.pos.y - worldPos.y) * (node.pos.y - worldPos.y)
            distSq < (node.radius * node.radius)
        }.minByOrNull { (it.pos - worldPos).getDistance() }

        return if (tappedNode != null) {
            _draggedNodeId.value = tappedNode.id
            // Mark as fixed so Physics engine knows not to move it via forces
            _graphNodes.update { current ->
                val updatedNode = tappedNode.copy(isFixed = true)
                current + (updatedNode.id to updatedNode)
            }
            startSimulation()
            true
        } else {
            false
        }
    }

    fun onDrag(screenDelta: Offset) {
        val id = _draggedNodeId.value ?: return
        val worldDelta = screenDeltaToWorldDelta(screenDelta)

        _graphNodes.update { current ->
            val node = current[id] ?: return@update current
            val newNode = node.copy(pos = node.pos + worldDelta)
            current + (id to newNode)
        }
        // Force simulation to update neighbors
        wakeSimulation()
    }

    fun onDragEnd() {
        val id = _draggedNodeId.value
        if (id != null) {
            _graphNodes.update { current ->
                val node = current[id] ?: return@update current
                val newNode = node.copy(isFixed = false) // Release the lock
                current + (id to newNode)
            }
        }
        _draggedNodeId.value = null
        wakeSimulation()
    }

    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        val worldPos = screenToWorld(screenPos)
        // Simple hit test
        val nodes = _graphNodes.value.values
        val tappedNode = nodes.filter { node ->
            val distSq = (node.pos.x - worldPos.x) * (node.pos.x - worldPos.x) +
                    (node.pos.y - worldPos.y) * (node.pos.y - worldPos.y)
            distSq < (node.radius * node.radius)
        }.minByOrNull { (it.pos - worldPos).getDistance() }

        if (tappedNode != null) {
            onNodeTapped(tappedNode.id)
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

    fun onCleared() {
        stopSimulation()
    }

    fun onShowDetangleDialog() { _showDetangleDialog.value = true }
    fun onDismissDetangleDialog() { _showDetangleDialog.value = false }
    fun startDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        _showDetangleDialog.value = false
        runDetangle(algorithm, params)
    }
}