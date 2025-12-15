package com.tau.nexusnote.codex.metadata

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.datamodels.EdgeDisplayItem
import com.tau.nexusnote.datamodels.NodeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MetadataViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope
) {

    // --- State observed from the repository (Full lists for graph, filtering) ---
    val nodeList = repository.nodeList
    val edgeList = repository.edgeList

    // --- NEW: State for Paginated ListView ---
    private val _paginatedNodes = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val paginatedNodes = _paginatedNodes.asStateFlow()

    private val _paginatedEdges = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val paginatedEdges = _paginatedEdges.asStateFlow()

    private var nodePage = 0L
    private var edgePage = 0L
    private val pageSize = 30L
    private var isNodeLoading = false
    private var isEdgeLoading = false
    private var allNodesLoaded = false
    private var allEdgesLoaded = false
// --- END NEW PAGINATION STATE ---

    // --- UI State ---
    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()

    // This state is just a marker for *what* to edit, used by EditCreateViewModel
    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    // --- ADDED: Search State ---
    private val _nodeSearchText = MutableStateFlow("")
    val nodeSearchText = _nodeSearchText.asStateFlow()

    private val _edgeSearchText = MutableStateFlow("")
    val edgeSearchText = _edgeSearchText.asStateFlow()

    // --- ADDED: Visibility State ---
    private val _nodeVisibility = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val nodeVisibility = _nodeVisibility.asStateFlow()

    private val _edgeVisibility = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val edgeVisibility = _edgeVisibility.asStateFlow()

// --- Public API ---

    fun listNodes() {
        // (FIXED) Launch a coroutine to call the suspend function
        viewModelScope.launch {
            repository.refreshNodes()
        }
    }

    fun listEdges() {
        // (FIXED) Launch a coroutine to call the suspend function
        viewModelScope.launch {
            repository.refreshEdges()
        }
    }

    fun listAll() {
        // (FIXED) Launch a coroutine to call the suspend function
        viewModelScope.launch {
            repository.refreshAll()
        }
        // Also reset paginated lists
        refreshPaginatedLists()
    }

    // --- NEW: Pagination Functions ---
    init {
        // Load the first page
        loadMoreNodes()
        loadMoreEdges()
    }

    fun refreshPaginatedLists() {
        viewModelScope.launch {
            // Reset nodes
            nodePage = 0
            allNodesLoaded = false
            isNodeLoading = true
            val initialNodes = repository.getNodesPaginated(0, pageSize)
            _paginatedNodes.value = initialNodes
            if (initialNodes.size < pageSize) allNodesLoaded = true
            isNodeLoading = false

            // Reset edges
            edgePage = 0
            allEdgesLoaded = false
            isEdgeLoading = true
            val initialEdges = repository.getEdgesPaginated(0, pageSize)
            _paginatedEdges.value = initialEdges
            if (initialEdges.size < pageSize) allEdgesLoaded = true
            isEdgeLoading = false
        }
    }

    fun loadMoreNodes() {
        if (isNodeLoading || allNodesLoaded) return
        isNodeLoading = true
        viewModelScope.launch {
            val offset = nodePage * pageSize
            val newNodes = repository.getNodesPaginated(offset, pageSize)
            if (newNodes.isNotEmpty()) {
                _paginatedNodes.update { it + newNodes }
                nodePage++
            } else {
                allNodesLoaded = true
            }
            isNodeLoading = false
        }
    }

    fun loadMoreEdges() {
        if (isEdgeLoading || allEdgesLoaded) return
        isEdgeLoading = true
        viewModelScope.launch {
            val offset = edgePage * pageSize
            val newEdges = repository.getEdgesPaginated(offset, pageSize)
            if (newEdges.isNotEmpty()) {
                _paginatedEdges.update { it + newEdges }
                edgePage++
            } else {
                allEdgesLoaded = true
            }
            isEdgeLoading = false
        }
    }
// --- END: Pagination Functions ---

    fun setItemToEdit(item: Any): Any? {
        // This just stores the item now, EditCreateViewModel will fetch full state
        _itemToEdit.value = item
        return item
    }

    fun selectItem(item: Any) {
        val currentPrimary = _primarySelectedItem.value
        val currentSecondary = _secondarySelectedItem.value

        when (item) {
            is NodeDisplayItem -> {
                if (item == currentPrimary) {
                    _primarySelectedItem.value = null
                } else if (item == currentSecondary) {
                    _secondarySelectedItem.value = null
                } else if (currentPrimary == null) {
                    _primarySelectedItem.value = item
                } else if (currentSecondary == null) {
                    _secondarySelectedItem.value = item
                } else {
                    _primarySelectedItem.value = item
                    _secondarySelectedItem.value = null
                }
            }
            is EdgeDisplayItem -> {
                // Phase 3 N-nary Update: Edges are now distinct items, so we select the edge itself
                // rather than trying to select its "source" and "target".
                _primarySelectedItem.value = item
                _secondarySelectedItem.value = null
            }
            else -> { // Includes SchemaDefinitionItem
                _primarySelectedItem.value = item
                _secondarySelectedItem.value = null
            }
        }
    }

    fun deleteDisplayItem(item: Any) {
        // Delegate to repository
        when (item) {
            is NodeDisplayItem -> repository.deleteNode(item.id)
            is EdgeDisplayItem -> repository.deleteEdge(item.id)
        }
        // Deletion will cause a mismatch, refresh the lists
        refreshPaginatedLists()
    }

    fun clearSelectedItem() {
        _itemToEdit.value = null
        _primarySelectedItem.value = null
        _secondarySelectedItem.value = null
    }

    // --- ADDED: Search Handlers ---
    fun onNodeSearchChange(text: String) {
        _nodeSearchText.value = text
    }

    fun onEdgeSearchChange(text: String) {
        _edgeSearchText.value = text
    }

    // --- ADDED: Visibility Toggle Functions ---
    fun toggleNodeVisibility(id: Long) {
        _nodeVisibility.update {
            val newMap = it.toMutableMap()
            newMap[id] = !(it[id] ?: true) // Default to visible
            newMap
        }
    }

    fun toggleEdgeVisibility(id: Long) {
        _edgeVisibility.update {
            val newMap = it.toMutableMap()
            newMap[id] = !(it[id] ?: true) // Default to visible
            newMap
        }
    }

    // --- ADDED: Bulk update functions ---
    fun setNodeVisibilityForSchema(schemaId: Long, isVisible: Boolean) {
        val affectedNodeIds = nodeList.value.filter { it.schemaId == schemaId }.map { it.id }
        _nodeVisibility.update {
            val newMap = it.toMutableMap()
            for (id in affectedNodeIds) {
                newMap[id] = isVisible
            }
            newMap
        }
    }

    fun setEdgeVisibilityForSchema(schemaId: Long, isVisible: Boolean) {
        val affectedEdgeIds = edgeList.value.filter { it.schemaId == schemaId }.map { it.id }
        _edgeVisibility.update {
            val newMap = it.toMutableMap()
            for (id in affectedEdgeIds) {
                newMap[id] = isVisible
            }
            newMap
        }
    }

    // --- NEW: Collapse Toggle Function ---
    fun toggleNodeCollapsed(id: Long) {
        val node = nodeList.value.find { it.id == id } ?: return
        viewModelScope.launch {
            repository.setNodeCollapsed(id, !node.isCollapsed)
        }
    }

}