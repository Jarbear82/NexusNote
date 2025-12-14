package com.tau.nexusnote.datamodels

/**
 * Represents an on-disk codex database.
 * @param name The display name (the filename, e.g., "my_db.sqlite").
 * @param path The absolute path to the database file.
 */
data class CodexItem(
    val name: String,
    val path: String
)

/**
 * Data class for Database Metadata.
 * (Currently unused).
 */
data class DBMetaData(
    val name: String,
    val version: String,
    val storage: String
)