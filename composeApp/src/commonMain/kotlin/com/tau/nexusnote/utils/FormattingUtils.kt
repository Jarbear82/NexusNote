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

/**
 * Converts a 0-based index to an Alphabetic indicator (A, B, C, ... AA, AB).
 */
fun Int.toAlphaIndex(upperCase: Boolean = true): String {
    val sb = StringBuilder()
    var n = this
    while (n >= 0) {
        sb.append(('A'.code + (n % 26)).toChar())
        n = (n / 26) - 1
    }
    val res = sb.reverse().toString()
    return if (upperCase) res else res.lowercase()
}

/**
 * Converts a 0-based index to a Roman numeral (I, II, III, IV...).
 * Supports up to 3999 (standard Roman limit). Returns numeric fallback if OOB.
 */
fun Int.toRomanIndex(upperCase: Boolean = true): String {
    val num = this + 1 // Roman numerals start at 1
    if (num <= 0 || num >= 4000) return num.toString()

    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romanLiterals = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

    val roman = StringBuilder()
    var temp = num
    for (i in values.indices) {
        while (temp >= values[i]) {
            temp -= values[i]
            roman.append(romanLiterals[i])
        }
    }
    val res = roman.toString()
    return if (upperCase) res else res.lowercase()
}