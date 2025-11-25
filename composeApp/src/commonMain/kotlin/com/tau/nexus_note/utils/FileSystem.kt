package com.tau.nexus_note.utils

import androidx.compose.runtime.Composable

/**
 * Gets the path to the user's home directory.
 */
expect fun getHomeDirectoryPath(): String

/**
 * A Composable function that shows a platform-native directory picker.
 *
 * @param show Whether to show the picker.
 * @param title The title for the picker window.
 * @param initialDirectory The directory to open the picker in.
 * @param onResult A callback that returns the selected directory path, or null if cancelled.
 */
@Composable
expect fun DirectoryPicker(
    show: Boolean,
    title: String,
    initialDirectory: String,
    onResult: (String?) -> Unit
)

/**
 * A Composable function that shows a platform-native file picker.
 *
 * @param show Whether to show the picker.
 * @param fileExtensions List of allowed extensions (e.g., ["md", "txt"]). Empty list allows all.
 * @param allowMultiple Whether to allow selecting multiple files.
 * @param onResult Callback with list of selected file paths. Empty if cancelled.
 */
@Composable
expect fun FilePicker(
    show: Boolean,
    fileExtensions: List<String>,
    allowMultiple: Boolean,
    onResult: (List<String>) -> Unit
)

/**
 * Lists all files within a given directory path that have a specific extension.
 * Returns a list of absolute paths.
 */
expect fun listFilesWithExtension(path: String, extension: String): List<String>

/**
 * Gets the display name of a file from its path.
 */
expect fun getFileName(path: String): String

/**
 * Checks if a file or directory exists at the given path.
 */
expect fun fileExists(path: String): Boolean

/**
 * Deletes a file at the given path.
 * Implementations should also attempt to delete associated data (e.g., .media folder).
 */
expect fun deleteFile(path: String)

/**
 * Reads the full text content of a file.
 */
expect fun readTextFile(path: String): String