package com.tau.nexus_note.utils

import androidx.compose.runtime.Composable

/**
 * Gets the path to the user's home directory.
 */
expect fun getHomeDirectoryPath(): String

/**
 * A Composable function that shows a platform-native directory picker.
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
 */
@Composable
expect fun FilePicker(
    show: Boolean,
    fileExtensions: List<String>,
    allowMultiple: Boolean,
    onResult: (List<String>) -> Unit
)

expect fun listFilesWithExtension(path: String, extension: String): List<String>

expect fun getFileName(path: String): String

// Added missing function
expect fun getParentDirectory(path: String): String

expect fun fileExists(path: String): Boolean

expect fun deleteFile(path: String)

expect fun readTextFile(path: String): String

// New function for Export
expect fun writeTextFile(path: String, content: String)

/**
 * Copies a source file to the specified target directory.
 * Returns the simple filename (e.g. "uuid.png") that was saved.
 *
 * @param sourcePath Absolute path of the file to import.
 * @param targetDirectory Absolute path of the directory to save into.
 * @return The new filename.
 */
expect fun copyFileToMediaDir(sourcePath: String, targetDirectory: String): String

expect fun listFilesRecursively(path: String, extensions: List<String>): List<String>