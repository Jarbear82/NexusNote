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
import com.tau.nexusnote.settings.GraphRenderingSettings
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
    private val layoutEngine = LayoutEngine(SpectralLayout(), CoseLayout())
    private val physicsEngine = PhysicsEngine()
    private val _fcGraph = FcGraph()
    private val graphMutex = Mutex()

    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _layoutConfig = MutableStateFlow(LayoutConfig())
    val layoutConfig = _layoutConfig.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    private val _draggedNodeId = MutableStateFlow<Long?>(null)
    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()
    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()
    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()
    private val _simulationEnabled = MutableStateFlow(false)
    val simulationEnabled = _simulationEnabled.asStateFlow()
    private val _isSimulationPaused = MutableStateFlow(false)
    val isSimulationPaused = _isSimulationPaused.asStateFlow()
    private val _selectedDetangler = MutableStateFlow(DetangleAlgorithm.FRUCHTERMAN_REINGOLD)

    private var size = Size.Zero

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
                wakeSimulation()
            }
        }
        viewModelScope.launch {
            repository.constraints.collect { dbConstraints ->
                updateGraphConstraints(dbConstraints)
            }
        }
        startContinuousPhysicsLoop()
    }

    private fun startContinuousPhysicsLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val shouldRun = _simulationEnabled.value && !_isSimulationPaused.value && !_isDetangling.value && _graphNodes.value.isNotEmpty()
                if (shouldRun) {
                    val nodes = _graphNodes.value
                    val edges = _graphEdges.value
                    val options = _physicsOptions.value
                    
                    // Optimized: In-place mutation
                    physicsEngine.update(nodes, edges, options, 0.016f)

                    var totalEnergy = 0.0
                    nodes.values.forEach { totalEnergy += it.vel.getDistance() }

                    if (totalEnergy < 0.5 && _draggedNodeId.value == null) {
                        _isSimulationPaused.value = true
                    }

                    // Map is no longer re-emitted; GraphNode.pos is MutableState
                    
                    if (graphMutex.tryLock()) {
                        try { syncFcGraphFromUi(nodes) } finally { graphMutex.unlock() }
                    }
                }
                delay(16)
            }
        }
    }

    fun wakeSimulation() { _isSimulationPaused.value = false }
    fun startSimulation() { _simulationEnabled.value = true; wakeSimulation() }
    fun stopSimulation() { _simulationEnabled.value = false }

    // --- Data Synchronization (UI <-> Domain) ---
    // Note: This method now receives generic DisplayItems and must construct full GraphNodes.
    // Ideally, we should pull full GraphNodes from repository, but sticking to existing flow:
    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                graphMutex.withLock {
                    // We need schema definitions to build GraphNode/Edge.
                    val currentSchemaData = repository.schema.value
                    val nodeSchemaMap = currentSchemaData?.nodeSchemas?.associateBy { it.id } ?: emptyMap()
                    val edgeSchemaMap = currentSchemaData?.edgeSchemas?.associateBy { it.id } ?: emptyMap()

                    val showAttributes = _renderingSettings.value.showAttributesAsNodes

                    // --- Generate Virtual Elements ---
                    val effectiveNodes = nodeList.toMutableList()
                    val virtualEdges = mutableListOf<EdgeDisplayItem>() // Reuse DisplayItem for ease
                    
                    if (showAttributes) {
                        nodeList.forEach { item ->
                            val schema = nodeSchemaMap[item.schemaId]
                            if (schema != null) {
                                item.properties.forEach { (key, value) ->
                                    val propDef = schema.properties.find { it.name == key }
                                    if (propDef != null) {
                                        if (propDef.type == CodexPropertyDataTypes.REFERENCE) {
                                            // REFERENCE: Edge to existing Node
                                            val targetId = value.toLongOrNull()
                                            if (targetId != null) {
                                                // Check if target is in the current graph (optional, but safe)
                                                // We assume targetId refers to a valid node.
                                                
                                                // Create a virtual Edge Item
                                                // ID must be unique. Using negative hash.
                                                val vEdgeId = -1L * (item.id.hashCode() + key.hashCode() + targetId).toLong()
                                                // We need a participant list
                                                val parts = listOf(
                                                    EdgeParticipant(item, "Source"),
                                                    EdgeParticipant(NodeDisplayItem(targetId, "Node", "Target", 0L), "Property") // Target
                                                )
                                                virtualEdges.add(EdgeDisplayItem(
                                                    id = vEdgeId,
                                                    label = key, // Label is the property name
                                                    properties = emptyMap(),
                                                    participatingNodes = parts,
                                                    schemaId = 0L // Dummy schema
                                                ))
                                            }
                                        } else {
                                            // PRIMITIVE: New Node + Edge
                                            // Virtual Node ID
                                            val vNodeId = -1L * (item.id.hashCode() + key.hashCode() + value.hashCode()).toLong()
                                            val vNode = NodeDisplayItem(
                                                id = vNodeId,
                                                label = key, // Schema/Type label
                                                displayProperty = value,
                                                schemaId = 0L
                                            )
                                            effectiveNodes.add(vNode)

                                            // Edge Source -> vNode
                                            val vEdgeId = -1L * (item.id.hashCode() + vNodeId).toLong()
                                             val parts = listOf(
                                                    EdgeParticipant(item, "Source"),
                                                    EdgeParticipant(vNode, "Property")
                                                )
                                            virtualEdges.add(EdgeDisplayItem(
                                                id = vEdgeId,
                                                label = key,
                                                properties = emptyMap(),
                                                participatingNodes = parts,
                                                schemaId = 0L
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val effectiveEdges = edgeList + virtualEdges

                    val currentFcNodes = _fcGraph.nodes.associateBy { it.id }
                    val activeIds = mutableSetOf<String>()

                    // 1. Nodes
                    effectiveNodes.forEach { item ->
                        val idStr = item.id.toString()
                        activeIds.add(idStr)
                        val existing = currentFcNodes[idStr]

                        val schema = nodeSchemaMap[item.schemaId]

                        if (existing != null) {
                            existing.data = item // Update payload
                        } else {
                            val newNode = _fcGraph.addNode(id = idStr)
                            newNode.data = item
                            newNode.x = (Math.random() - 0.5) * 200.0
                            newNode.y = (Math.random() - 0.5) * 200.0
                        }
                    }

                    // 2. Edges
                    val uiEdges = mutableListOf<GraphEdge>()
                    effectiveEdges.forEach { item ->
                        // Every edge is ALSO a node in the layout (Diamond/HyperNode)
                        val idStr = item.id.toString()
                        activeIds.add(idStr)
                        val existing = currentFcNodes[idStr]

                        val schema = edgeSchemaMap[item.schemaId]

                        if (existing != null) {
                            existing.data = item
                        } else {
                            val newNode = _fcGraph.addNode(id = idStr)
                            newNode.data = item
                            newNode.x = (Math.random() - 0.5) * 200.0
                            newNode.y = (Math.random() - 0.5) * 200.0
                        }

                        // Create segments for visualization based on Role Direction
                        item.participatingNodes.forEach { participant ->
                            val roleName = participant.role ?: "Unknown"
                            val roleDef = schema?.roles?.find { it.name == roleName }
                            val isSource = roleDef?.direction == RelationDirection.SOURCE || roleName == "Source"

                            // If Source: Participant -> EdgeNode
                            // If Target: EdgeNode -> Participant
                            
                            val sId = if (isSource) participant.node.id else item.id
                            val tId = if (isSource) item.id else participant.node.id
                            
                            // Unique ID for visual segment
                            val segmentId = -1 * (item.id * 31 + participant.node.id).toLong()

                            uiEdges.add(GraphEdge(
                                id = segmentId, 
                                schemas = emptyList(),
                                properties = emptyList(),
                                sourceId = sId,
                                targetId = tId,
                                strength = 1.0f,
                                roleLabel = roleName,
                                colorInfo = labelToColor(item.label)
                            ))
                        }
                    }
                    _graphEdges.value = uiEdges

                    // Cleanup stale nodes
                    val nodesToRemove = _fcGraph.nodes.filter { it.id !in activeIds }
                    nodesToRemove.forEach { _fcGraph.removeNode(it) }

                    // Sync Topology in FcGraph
                    _fcGraph.edges.clear()
                    uiEdges.forEach { edge ->
                        _fcGraph.addEdge(edge.sourceId.toString(), edge.targetId.toString())
                    }

                    pushUiUpdate()
                }
            }
            if (_graphNodes.value.isNotEmpty()) runDetangle(DetangleAlgorithm.FCOSE, emptyMap())
        }
    }

    // ... (Rest of Layout/Physics/Interaction methods remain similar, using new GraphNode properties)

    private fun updateGraphConstraints(constraints: List<LayoutConstraintItem>) { /* ... */ }

    private fun pushUiUpdate() {
        // Reconstruct GraphNode from FcNode + Data
        val newMap = _fcGraph.nodes.associate { fcNode ->
            val id = fcNode.id.toLongOrNull() ?: 0L

            val uiNode = when (val data = fcNode.data) {
                is NodeDisplayItem -> {
                    val label = data.label
                    val dummySchema = SchemaDefinition(id = data.schemaId, name = label, isRelation = false, properties = emptyList())
                    GraphNode(
                        id = id,
                        schemas = listOf(dummySchema),
                        displayProperty = data.displayProperty,
                        initialPos = Offset(fcNode.getCenter().first.toFloat(), fcNode.getCenter().second.toFloat()),
                        vel = Offset.Zero,
                        mass = 1.0f,
                        radius = (fcNode.width / 2.0).toFloat(),
                        width = fcNode.width.toFloat(),
                        height = fcNode.height.toFloat(),
                        isCompound = fcNode.isCompound(),
                        isHyperNode = false,
                        colorInfo = labelToColor(label),
                        isFixed = fcNode.isFixed,
                        properties = emptyList()
                    )
                }
                is EdgeDisplayItem -> {
                    val label = data.label
                    val dummySchema = SchemaDefinition(id = data.schemaId, name = label, isRelation = true, properties = emptyList())
                    GraphNode(
                        id = id,
                        schemas = listOf(dummySchema),
                        displayProperty = label,
                        initialPos = Offset(fcNode.getCenter().first.toFloat(), fcNode.getCenter().second.toFloat()),
                        vel = Offset.Zero,
                        mass = 1.0f,
                        radius = (fcNode.width / 3.0).toFloat(),
                        width = (fcNode.width * 0.8f).toFloat(),
                        height = (fcNode.height * 0.8f).toFloat(),
                        isCompound = false,
                        isHyperNode = true,
                        colorInfo = labelToColor(label),
                        isFixed = fcNode.isFixed,
                        properties = emptyList()
                    )
                }
                else -> {
                    // Fallback for unknown types
                    val label = "Unknown"
                    val dummySchema = SchemaDefinition(id = 0L, name = label, isRelation = false, properties = emptyList())
                    GraphNode(
                        id = id,
                        schemas = listOf(dummySchema),
                        displayProperty = label,
                        initialPos = Offset(fcNode.getCenter().first.toFloat(), fcNode.getCenter().second.toFloat()),
                        vel = Offset.Zero,
                        mass = 1.0f,
                        radius = (fcNode.width / 2.0).toFloat(),
                        width = fcNode.width.toFloat(),
                        height = fcNode.height.toFloat(),
                        isCompound = fcNode.isCompound(),
                        isHyperNode = false,
                        colorInfo = labelToColor(label),
                        isFixed = fcNode.isFixed,
                        properties = emptyList()
                    )
                }
            }
            id to uiNode
        }
        _graphNodes.value = newMap
    }

    private fun syncFcGraphFromUi(uiNodes: Map<Long, GraphNode>) {
        uiNodes.values.forEach { uiNode ->
            _fcGraph.getNode(uiNode.id.toString())?.let { fcNode ->
                fcNode.setCenter(uiNode.pos.x.toDouble(), uiNode.pos.y.toDouble())
            }
        }
    }

    // ... (runDetangle, gesture handlers, etc. - no structural changes needed here)
    fun runDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) { /* ... */ }
    fun runFullFcosePipeline() { /* ... */ }
    fun runRandomize() { /* ... */ }
    fun runDraft() { /* ... */ }
    fun runTransform() { /* ... */ }
    fun runEnforce() { /* ... */ }
    fun runPolishing() { /* ... */ }
    fun addConstraint(type: ConstraintUiType, nodeIds: List<Long>) { /* ... */ }
    fun groupSelectedNodes(nodeIds: List<Long>) { /* ... */ }
    fun updateLayoutConfig(config: LayoutConfig) { _layoutConfig.value = config }
    fun updatePhysicsOptions(options: PhysicsOptions) { _physicsOptions.value = options; wakeSimulation() }
    fun updateRenderingSettings(settings: GraphRenderingSettings) { 
        _renderingSettings.value = settings
        // Trigger graph update to refresh virtual nodes
        // We can't easily re-trigger `combine` flow in CodexViewModel.
        // But `updateGraphData` reads `_renderingSettings.value`.
        // We need to re-run `updateGraphData` with current data.
        // We don't store current data in ViewModel (only in _graphNodes which is output).
        // However, `CodexViewModel` observes `renderingSettings`? No.
        // `CodexViewModel` calls `updateGraphData`.
        
        // Solution: GraphViewmodel should trigger a refresh request?
        // Or simpler: `GraphViewmodel` can't easily re-run logic without input data.
        // But `CodexViewModel` observes `settingsFlow`? No, that's global settings.
        // `_renderingSettings` is local to ViewModel (initialized from global).
        
        // If I update `_renderingSettings`, the next time `updateGraphData` is called, it will work.
        // But I want immediate effect.
        // `CodexViewModel` holds the source of truth (`visibleNodes`).
        // If I expose `renderingSettings` as a flow that `CodexViewModel` collects?
        // Or `GraphViewModel` exposes a "RefreshNeeded" event?
    }
    fun screenToWorld(screenPos: Offset): Offset { return (screenPos - Offset(size.width/2f, size.height/2f) - _transform.value.pan * _transform.value.zoom) / _transform.value.zoom }
    
    fun onPan(delta: Offset) { 
        _transform.value = _transform.value.copy(pan = _transform.value.pan + (delta / _transform.value.zoom))
        wakeSimulation() 
    }

    fun onZoom(zoomFactor: Float, zoomCenter: Offset) {
        val currentZoom = _transform.value.zoom
        val newZoom = (currentZoom * zoomFactor).coerceIn(0.1f, 5.0f)
        
        // Calculate world position of the zoom center before zoom
        val worldCenter = screenToWorld(zoomCenter)
        
        // We want worldCenter to map to the same screen position after zoom
        // screenPos = (worldPos * zoom) + center + (pan * zoom)
        // pan = (screenPos - center)/zoom - worldPos
        
        val screenCenter = Offset(size.width/2f, size.height/2f)
        // New Pan to keep worldCenter under zoomCenter
        val newPan = ((zoomCenter - screenCenter) / newZoom) - worldCenter

        _transform.value = TransformState(pan = newPan, zoom = newZoom)
        wakeSimulation()
    }

    fun onDragStart(pos: Offset): Boolean {
        val worldPos = screenToWorld(pos)
        val node = _graphNodes.value.values.find { (it.pos - worldPos).getDistance() <= it.radius + 10f } // +10 touch slop
        if (node != null) {
            _draggedNodeId.value = node.id
            node.isFixed = true
            wakeSimulation()
            return true
        }
        return false
    }

    fun onDrag(delta: Offset) {
        val id = _draggedNodeId.value
        if (id != null) {
            val node = _graphNodes.value[id]
            if (node != null) {
                // Delta is in screen pixels. Convert to world delta.
                val worldDelta = delta / _transform.value.zoom
                node.pos += worldDelta
                wakeSimulation()
            }
        }
    }

    fun onDragEnd() {
        val id = _draggedNodeId.value
        if (id != null) {
            val node = _graphNodes.value[id]
            if (node != null) {
                node.isFixed = false // Release physics lock
                node.vel = Offset.Zero // Reset velocity to stop flinging
            }
            _draggedNodeId.value = null
            wakeSimulation()
        }
    }

    fun onTap(pos: Offset, cb: (Long)->Unit) {
        val worldPos = screenToWorld(pos)
        // Check exact radius for tap
        val node = _graphNodes.value.values.sortedByDescending { it.id }.find { (it.pos - worldPos).getDistance() <= it.radius }
        if (node != null) {
            cb(node.id)
        }
    }
    fun onResize(s: androidx.compose.ui.unit.IntSize) { size = Size(s.width.toFloat(), s.height.toFloat()) }
    fun onFabClick() { _showFabMenu.value = !_showFabMenu.value }
    fun toggleSettings() { _showSettings.value = !_showSettings.value }
    fun onCleared() { stopSimulation() }
    fun onShowDetangleDialog() { _showDetangleDialog.value = true }
    fun onDismissDetangleDialog() { _showDetangleDialog.value = false }
    fun startDetangle(alg: DetangleAlgorithm, params: Map<String, Any>) { _showDetangleDialog.value = false; runDetangle(alg, params) }
}