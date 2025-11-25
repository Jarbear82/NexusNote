package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository

/**
 * Common interface for parsing external documents (MD, PDF, DOCX)
 * into the Codex Graph structure.
 */
interface DocumentParser {

    /**
     * List of file extensions this parser supports (e.g., "md", "markdown")
     */
    val supportedExtensions: List<String>

    /**
     * Parses the file content and injects the resulting Nodes and Edges
     * directly into the provided [repository].
     * * @param fileUri The identifier for the file (path or name).
     * @param content The raw string content of the file.
     * @param repository The target repository to insert the graph into.
     * @return Result.success if parsing and insertion succeeded, failure otherwise.
     */
    suspend fun parse(
        fileUri: String,
        content: String,
        repository: CodexRepository
    ): Result<Unit>
}