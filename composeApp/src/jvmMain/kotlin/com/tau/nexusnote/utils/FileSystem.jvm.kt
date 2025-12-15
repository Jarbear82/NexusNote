package com.tau.nexusnote.utils

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
 * JVM implementation for the file picker using JFileChooser.
 */
@Composable
actual fun FilePicker(
    show: Boolean,
    title: String,
    fileExtensions: List<String>,
    onResult: (String?) -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            val fileChooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                dialogTitle = title
                if (fileExtensions.isNotEmpty()) {
                    val filter = FileNameExtensionFilter(
                        "Allowed Files (${fileExtensions.joinToString(", ")})",
                        *fileExtensions.toTypedArray()
                    )
                    addChoosableFileFilter(filter)
                    fileFilter = filter
                }
            }

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onResult(fileChooser.selectedFile.absolutePath)
            } else {
                onResult(null)
            }
        }
    }
}

/**
 * JVM implementation for listing files with a specific extension.
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
        throw IOException("Permission denied for directory: $path", e)
    } catch (e: Exception) {
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
        throw IOException("Error deleting codex at: $path", e)
    }
}