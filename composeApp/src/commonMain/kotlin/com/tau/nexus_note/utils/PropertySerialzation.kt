package com.tau.nexus_note.utils

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Helper object to handle the JSON serialization of complex property types
 * (List and Map) for storage in the SQLite text column.
 */
object PropertySerialization {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // --- List<String> ---

    fun serializeList(list: List<String>): String {
        return try {
            json.encodeToString(ListSerializer(String.serializer()), list)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeList(data: String): List<String> {
        return try {
            if (data.isBlank()) return emptyList()
            json.decodeFromString(ListSerializer(String.serializer()), data)
        } catch (e: Exception) {
            // Fallback: treat as a single item list if parsing fails
            listOf(data)
        }
    }

    // --- Map<String, String> ---

    fun serializeMap(map: Map<String, String>): String {
        return try {
            json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun deserializeMap(data: String): Map<String, String> {
        return try {
            if (data.isBlank()) return emptyMap()
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), data)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}