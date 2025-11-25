package com.tau.nexus_note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tau.nexus_note.datamodels.CodexItem
import com.tau.nexus_note.codex.CodexViewModel
import com.tau.nexus_note.settings.SettingsData
import com.tau.nexus_note.settings.SettingsRepository
import com.tau.nexus_note.settings.SettingsViewModel
import com.tau.nexus_note.settings.createDataStore
import com.tau.nexus_note.utils.deleteFile
import com.tau.nexus_note.utils.getFileName
import com.tau.nexus_note.utils.getHomeDirectoryPath
import com.tau.nexus_note.utils.listFilesWithExtension
import com.tau.nexus_note.utils.toPascalCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.tau.nexus_note.doc_parser.MarkdownParser
import com.tau.nexus_note.utils.readTextFile

enum class Screen {
    NEXUS,
    CODEX,
    SETTINGS
}

class MainViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _selectedScreen = MutableStateFlow(Screen.NEXUS)
    val selectedScreen = _selectedScreen.asStateFlow()

    private val _codexViewModel = MutableStateFlow<CodexViewModel?>(null)
    val codexViewModel = _codexViewModel.asStateFlow()

    // --- Error State Flow ---
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    // --- Settings ---
    private val dataStore = createDataStore()
    private val settingsRepository = SettingsRepository(dataStore)

    private val _appSettings = MutableStateFlow(SettingsData.Default)
    val appSettings: StateFlow<SettingsData> = _appSettings.asStateFlow()

    val settingsViewModel = SettingsViewModel(
        settingsFlow = appSettings,
        onUpdateSettings = { newSettings ->
            _appSettings.value = newSettings
        }
    )

    private val _showImportFilePicker = MutableStateFlow(false)
    val showImportFilePicker = _showImportFilePicker.asStateFlow()

    fun clearError() {
        _errorFlow.value = null
    }

    // --- Codex Management State ---

    private val _codexBaseDirectory = MutableStateFlow(getHomeDirectoryPath())
    val codexBaseDirectory = _codexBaseDirectory.asStateFlow()

    private val _codices = MutableStateFlow<List<CodexItem>>(emptyList())
    val codices = _codices.asStateFlow()

    private val _showBaseDirPicker = MutableStateFlow(false)
    val showBaseDirPicker = _showBaseDirPicker.asStateFlow()

    // --- UPDATED: State for Naming (no dialog) ---
    private val _newCodexName = MutableStateFlow("")
    val newCodexName = _newCodexName.asStateFlow()

    private val _codexNameError = MutableStateFlow<String?>(null)
    val codexNameError = _codexNameError.asStateFlow()

    // --- NEW: State for Deletion Dialog ---
    private val _codexToDelete = MutableStateFlow<CodexItem?>(null)
    val codexToDelete = _codexToDelete.asStateFlow()

    // --- Track currently open codex ---
    private val _openedCodexItem = MutableStateFlow<CodexItem?>(null)
    val openedCodexItem = _openedCodexItem.asStateFlow()

    init {
        loadCodices()

        viewModelScope.launch {
            _appSettings.value = settingsRepository.settings.first()

            @OptIn(FlowPreview::class)
            _appSettings
                .drop(1)
                .debounce(1000L)
                .collect { settingsToSave ->
                    settingsRepository.saveSettings(settingsToSave)
                }
        }
    }

    /**
     * Scans the base directory for valid SQLiteDB files (.sqlite).
     */
    fun loadCodices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = listFilesWithExtension(_codexBaseDirectory.value, ".sqlite")
                val graphs = files.map {
                    CodexItem(getFileName(it), it)
                }

                viewModelScope.launch(Dispatchers.Main) {
                    _codices.value = graphs
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    _errorFlow.value = "Error loading codex list: ${e.message}"
                }
            }
        }
    }

    /**
     * Opens the directory picker to change the base storage directory.
     */
    fun onChangeBaseDirectoryClicked() {
        _showBaseDirPicker.value = true
    }

    /**
     * Callback for when the user selects a new base directory.
     */
    fun onBaseDirectorySelected(path: String?) {
        _showBaseDirPicker.value = false
        path?.let {
            _codexBaseDirectory.value = it
            loadCodices()
        }
    }

    // --- REMOVED: onCreateNewCodexClicked() ---

    // --- UPDATED: Naming Logic ---

    /**
     * Called on every keystroke in the NexusView text field.
     * Validates the name and updates the error state.
     */
    fun validateCodexName(name: String) {
        val pascalName = name.toPascalCase()
        _newCodexName.value = pascalName

        if (pascalName.isBlank()) {
            _codexNameError.value = "Name cannot be blank."
            return
        }

        val finalName = if (pascalName.endsWith(".sqlite")) pascalName else "$pascalName.sqlite"
        val exists = _codices.value.any { it.name.equals(finalName, ignoreCase = true) }
        _codexNameError.value = if (exists) "A codex with this name already exists." else null
    }

    /**
     * Callback when the user confirms a name for a new codex.
     * This creates and opens the new database.
     */
    fun onCodexNameConfirmed() {
        val name = _newCodexName.value
        if (name.isBlank() || _codexNameError.value != null) return

        val finalName = if (name.endsWith(".sqlite")) name else "$name.sqlite"
        val newPath = "${_codexBaseDirectory.value}/$finalName"
        val newItem = CodexItem(finalName, newPath)

        openCodex(newItem)
        _codices.update { (it + newItem).distinctBy { it.path } }

        // Clear the name fields
        _newCodexName.value = ""
        _codexNameError.value = null
    }

    /**
     * Clears the codex name fields.
     */
    fun clearCodexName() {
        _newCodexName.value = ""
        _codexNameError.value = null
    }

    // --- END: Naming Logic ---

    // --- NEW: Deletion Logic ---

    /**
     * Shows the delete confirmation dialog for the selected codex.
     */
    fun requestDeleteCodex(item: CodexItem) {
        _codexToDelete.value = item
    }

    /**
     * Hides the delete confirmation dialog.
     */
    fun cancelDeleteCodex() {
        _codexToDelete.value = null
    }

    /**
     * Confirms deletion, closes the codex if it's open,
     * deletes the file from disk, and refreshes the list.
     */
    fun confirmDeleteCodex() {
        viewModelScope.launch {
            val itemToDelete = _codexToDelete.value ?: return@launch
            cancelDeleteCodex() // Close dialog immediately

            // If the codex to delete is the one that's open, close it first.
            if (itemToDelete.path == _openedCodexItem.value?.path) {
                closeCodex()
            }

            try {
                // Perform file I/O on the IO dispatcher
                withContext(Dispatchers.IO) {
                    deleteFile(itemToDelete.path)
                }
                // Refresh the list
                loadCodices()
            } catch (e: Exception) {
                _errorFlow.value = "Error deleting file: ${e.message}"
            }
        }
    }

    // --- END: Deletion Logic ---

    /**
     * Opens a terminal session for a specific on-disk codex.
     */
    fun openCodex(item: CodexItem) {
        viewModelScope.launch {
            try {
                clearCodexName() // Clear create field before opening
                _codexViewModel.value?.onCleared() // Close previous one
                val newService = SqliteDbService()
                newService.initialize(item.path) // Initialize with file path

                _codexViewModel.value = CodexViewModel(newService, appSettings)
                _openedCodexItem.value = item // Track open codex
                _selectedScreen.value = Screen.CODEX
            } catch (e: Exception) {
                _errorFlow.value = "Failed to open codex '${item.path}': ${e.message}"
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _selectedScreen.value = screen
    }

    /**
     * Opens a terminal session for an in-memory database.
     */
    fun openInMemoryTerminal() {
        viewModelScope.launch {
            try {
                clearCodexName() // Clear create field before opening
                _codexViewModel.value?.onCleared()
                val newService = SqliteDbService()
                newService.initialize(":memory:")

                _codexViewModel.value = CodexViewModel(newService, appSettings)
                _openedCodexItem.value = null // Not an on-disk codex
                _selectedScreen.value = Screen.CODEX
            } catch (e: Exception) {
                _errorFlow.value = "Failed to open in-memory database: ${e.message}"
            }
        }
    }

    fun closeCodex() {
        viewModelScope.launch {
            _codexViewModel.value?.onCleared()
            _codexViewModel.value = null
            _openedCodexItem.value = null // Clear tracked codex
            _selectedScreen.value = Screen.NEXUS
            clearCodexName() // Clear create field
            // Refresh the list in case a new DB was created
            loadCodices()
        }
    }

    fun onDispose() {
        // This will be called from the main App composable's onDispose
        _codexViewModel.value?.onCleared()
    }

    // --- Import Documents ---
    fun onImportDocumentsClicked() {
        _showImportFilePicker.value = true
    }

    fun onImportFilesSelected(paths: List<String>) {
        _showImportFilePicker.value = false
        if (paths.isEmpty()) return

        viewModelScope.launch {
            try {
                // 1. Initialize In-Memory DB
                clearCodexName()
                _codexViewModel.value?.onCleared()
                val newService = SqliteDbService()
                newService.initialize(":memory:")

                // 2. Setup Repository & Parser
                val tempRepo = CodexRepository(newService, viewModelScope)
                val parser = MarkdownParser(tempRepo)

                // 3. Bootstrap Schemas
                tempRepo.bootstrapDocumentSchemas()

                // 4. Parse Files
                // We assume all selected are MD for now, but can check extension
                paths.forEach { path ->
                    val content = withContext(Dispatchers.IO) { readTextFile(path) }
                    parser.parse(path, content, tempRepo)
                }

                // 5. Finalize Setup
                tempRepo.refreshAll()
                _codexViewModel.value = CodexViewModel(newService, appSettings)
                _openedCodexItem.value = null // It's in-memory
                _selectedScreen.value = Screen.CODEX

            } catch (e: Exception) {
                _errorFlow.value = "Import failed: ${e.message}"
                e.printStackTrace()
            }
        }
    }
}

// Helper for remembering the ViewModel
@Composable
fun rememberMainViewModel(): MainViewModel {
    return remember { MainViewModel() }
}