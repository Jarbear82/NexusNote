package com.tau.nexusnote.utils

/**
 * Formats a string to PascalCase.
 * Removes all non-alphanumeric characters and capitalizes the first letter of each "word".
 * "my new node" -> "MyNewNode"
 * "my-new-node" -> "MyNewNode"
 * "my_new_node" -> "MyNewNode"
 */
fun String.toPascalCase(): String {
    val words = this.split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
    return words.joinToString("") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

/**
 * Formats a string to camelCase.
 * "my property" -> "myProperty"
 */
fun String.toCamelCase(): String {
    val pascal = this.toPascalCase()
    return pascal.replaceFirstChar { it.lowercase() }
}

/**
 * Formats a string to SCREAMING_SNAKE_CASE.
 * "my new edge" -> "MY_NEW_EDGE"
 * "my-new-edge" -> "MY_NEW_EDGE"
 * "MyNewEdge" -> "MY_NEW_EDGE"
 * "MY_NEW_EDGE" -> "MY_NEW_EDGE"
 */
fun String.toScreamingSnakeCase(): String {
    // 1. Insert a space before any uppercase letter that follows a lowercase letter (handles camelCase)
    // e.g., "MyNewEdge" -> "My New Edge"
    val spacedString = this.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

    // 2. Split on any character that is NOT a letter, number, or underscore
    // This now correctly treats spaces and hyphens as separators, but keeps underscores.
    val words = spacedString.split(Regex("[^a-zA-Z0-9_]")).filter { it.isNotBlank() }

    // 3. Join with underscores and uppercase
    return words.joinToString("_") { it.uppercase() }
}