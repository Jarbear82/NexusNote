package com.tau.nexus_note.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual fun getHomeDirectoryPath(): String {
    return System.getProperty("user.home")
}

@Composable
actual fun DirectoryPicker(
    show: Boolean,
    title: String,
    initialDirectory: String,
    onResult: (String?) -> Unit
) {
    // Note: AWT FileDialog does not support directory selection reliably on all platforms (Windows/Linux).
    // We retain JFileChooser here for robustness when selecting folders.
    LaunchedEffect(show) {
        if (show) {
            val fileChooser = JFileChooser(initialDirectory).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
                isAcceptAllFileFilterUsed = false
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

actual fun listFilesWithExtension(path: String, extension: String): List<String> {
    try {
        val dir = File(path)
        if (!dir.exists()) throw IOException("Directory does not exist: $path")
        if (!dir.isDirectory) throw IOException("Path is not a directory: $path")

        return dir.listFiles { file ->
            file.isFile && file.name.endsWith(extension)
        }?.map { it.absolutePath } ?: emptyList()
    } catch (e: Exception) {
        throw IOException("Error listing files in: $path", e)
    }
}


actual fun getFileName(path: String): String {
    return File(path).name
}

// Added missing implementation
actual fun getParentDirectory(path: String): String {
    return File(path).parent ?: ""
}

actual fun fileExists(path: String): Boolean {
    return File(path).exists()
}

actual fun deleteFile(path: String) {
    try {
        val dbFile = File(path)
        val mediaDir = File(dbFile.parent, "${dbFile.nameWithoutExtension}.media")

        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }

        if (dbFile.exists()) {
            dbFile.delete()
        }
    } catch (e: Exception) {
        throw IOException("Error deleting codex at: $path", e)
    }
}

actual fun readTextFile(path: String): String {
    return File(path).readText()
}

actual fun writeTextFile(path: String, content: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

@Composable
actual fun FilePicker(
    show: Boolean,
    fileExtensions: List<String>,
    allowMultiple: Boolean,
    onResult: (List<String>) -> Unit
) {
    // Phase 5: Replaced Swing JFileChooser with AWT FileDialog for native look & feel
    LaunchedEffect(show) {
        if (show) {
            val dialog = FileDialog(null as Frame?, "Select File(s)", FileDialog.LOAD)
            dialog.isMultipleMode = allowMultiple

            if (fileExtensions.isNotEmpty()) {
                // FileDialog filenameFilter is a bit limited compared to JFileChooser,
                // but allows basic filtering.
                dialog.filenameFilter = java.io.FilenameFilter { _, name ->
                    fileExtensions.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
                }
                // Also helpful to set file string for Windows, though inconsistent
                // dialog.file = fileExtensions.joinToString(";") { "*.$it" }
            }

            dialog.isVisible = true

            if (dialog.directory != null) {
                // If multiple files selected
                if (allowMultiple && dialog.files.isNotEmpty()) {
                    onResult(dialog.files.map { it.absolutePath })
                } else if (dialog.file != null) {
                    // Single file fallback
                    onResult(listOf(dialog.directory + dialog.file))
                } else {
                    onResult(emptyList())
                }
            } else {
                onResult(emptyList())
            }
        }
    }
}

actual fun copyFileToMediaDir(sourcePath: String, targetDirectory: String): String {
    val mediaDir = File(targetDirectory)

    if (!mediaDir.exists()) {
        mediaDir.mkdirs()
    }

    val sourceFile = File(sourcePath)
    val extension = sourceFile.extension
    // Use UUID to prevent name collisions
    val newFileName = "${UUID.randomUUID()}.$extension"
    val destFile = File(mediaDir, newFileName)

    Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

    // Return just filename
    return newFileName
}

actual fun listFilesRecursively(path: String, extensions: List<String>): List<String> {
    val root = File(path)
    if (!root.exists() || !root.isDirectory) return emptyList()

    val normalizedExtensions = extensions.map { it.lowercase() }

    return root.walkTopDown()
        .filter { file ->
            if (file.isDirectory) return@filter false
            val ext = "." + file.extension.lowercase()
            normalizedExtensions.any { reqExt -> ext.endsWith(reqExt) }
        }
        .map { it.absolutePath }
        .toList()
}