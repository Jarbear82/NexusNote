package com.tau.nexus_note.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * JVM implementation for getting the user's home directory.
 */
actual fun getHomeDirectoryPath(): String {
    return System.getProperty("user.home")
}

/**
 * JVM implementation for the directory picker using JFileChooser.
 */
@Composable
actual fun DirectoryPicker(
    show: Boolean,
    title: String,
    initialDirectory: String,
    onResult: (String?) -> Unit
) {
    // This runs on the main (AWT Event Dispatch) thread
    LaunchedEffect(show) {
        if (show) {
            val fileChooser = JFileChooser(initialDirectory).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
                isAcceptAllFileFilterUsed = false
            }

            val result = fileChooser.showOpenDialog(null) // null for parent frame
            if (result == JFileChooser.APPROVE_OPTION) {
                onResult(fileChooser.selectedFile.absolutePath)
            } else {
                onResult(null) // User cancelled
            }
        }
    }
}

/**
 * JVM implementation for listing files with a specific extension.
 * @throws IOException if an error occurs while listing files.
 */
actual fun listFilesWithExtension(path: String, extension: String): List<String> {
    try {
        val dir = File(path)
        if (!dir.exists()) throw IOException("Directory does not exist: $path")
        if (!dir.isDirectory) throw IOException("Path is not a directory: $path")

        return dir.listFiles { file ->
            file.isFile && file.name.endsWith(extension)
        }?.map { it.absolutePath } ?: emptyList()
    } catch (e: SecurityException) {
        // Catch security exceptions and re-throw as IOException
        throw IOException("Permission denied for directory: $path", e)
    } catch (e: Exception) {
        // Catch other potential IO errors
        throw IOException("Error listing files in: $path", e)
    }
}


/**
 * JVM implementation for getting a file name.
 */
actual fun getFileName(path: String): String {
    return File(path).name
}

/**
 * JVM implementation for checking if a file exists.
 */
actual fun fileExists(path: String): Boolean {
    return File(path).exists()
}

/**
 * JVM implementation for deleting a file and its associated .media directory.
 */
actual fun deleteFile(path: String) {
    try {
        val dbFile = File(path)
        val mediaDir = File(dbFile.parent, "${dbFile.nameWithoutExtension}.media")

        if (mediaDir.exists()) {
            if (!mediaDir.deleteRecursively()) {
                println("Warning: Could not delete media directory: ${mediaDir.absolutePath}")
            }
        }

        if (dbFile.exists()) {
            if (!dbFile.delete()) {
                println("Warning: Could not delete database file: ${dbFile.absolutePath}")
            }
        }
    } catch (e: Exception) {
        // Throw an IOException so the ViewModel can catch it and show an error
        throw IOException("Error deleting codex at: $path", e)
    }
}

/**
 * JVM implementation for reading a text file.
 */
actual fun readTextFile(path: String): String {
    return File(path).readText()
}

/**
 * JVM implementation for the file picker using JFileChooser.
 */
@Composable
actual fun FilePicker(
    show: Boolean,
    fileExtensions: List<String>,
    allowMultiple: Boolean,
    onResult: (List<String>) -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            val fileChooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = allowMultiple
                dialogTitle = "Select File(s)"

                if (fileExtensions.isNotEmpty()) {
                    val description = fileExtensions.joinToString(", ") { it.uppercase() }
                    val filter = FileNameExtensionFilter(
                        description,
                        *fileExtensions.toTypedArray()
                    )
                    addChoosableFileFilter(filter)
                    fileFilter = filter
                    isAcceptAllFileFilterUsed = false
                }
            }

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val files = if (allowMultiple) {
                    fileChooser.selectedFiles.map { it.absolutePath }
                } else {
                    listOf(fileChooser.selectedFile.absolutePath)
                }
                onResult(files)
            } else {
                onResult(emptyList()) // User cancelled
            }
        }
    }
}