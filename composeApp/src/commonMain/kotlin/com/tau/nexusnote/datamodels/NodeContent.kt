package com.tau.nexusnote.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines the generic structural primitive of the node.
 * This separates storage format from semantic meaning (which is handled by SchemaConfig).
 */
enum class NodeType {
    TEXT,
    MAP,
    LIST,
    TABLE,
    CODE,
    MEDIA,
    TIMESTAMP
}

/**
 * Polymorphic hierarchy for Node Data.
 * Consolidates storage into generic primitives.
 */
@Serializable
sealed class NodeContent {

    /**
     * Primitive: TEXT
     * Stores any text-based content (Titles, Headings, Notes, Long form).
     */
    @Serializable @SerialName("text")
    data class TextContent(val value: String) : NodeContent()

    /**
     * Primitive: MAP
     * Stores generic key-value pairs.
     */
    @Serializable @SerialName("map")
    data class MapContent(val values: Map<String, String>) : NodeContent()

    /**
     * Primitive: LIST
     * Stores a list of items, optionally with completion state.
     * Unifies Ordered Lists, Unordered Lists, Sets, and Task Lists.
     */
    @Serializable @SerialName("list")
    data class ListContent(val items: List<ListItem>) : NodeContent()

    /**
     * Primitive: TABLE
     * Stores structured grid data.
     */
    @Serializable @SerialName("table")
    data class TableContent(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : NodeContent()

    /**
     * Primitive: CODE
     * Stores source code with language metadata.
     */
    @Serializable @SerialName("code")
    data class CodeContent(
        val code: String,
        val language: String,
        val filename: String? = null
    ) : NodeContent()

    /**
     * Primitive: MEDIA
     * Stores references to external media files (Images, Audio, Video).
     * Renamed from ImageContent to generic MediaContent.
     */
    @Serializable @SerialName("media")
    data class MediaContent(
        val uri: String,
        val caption: String?
    ) : NodeContent()

    /**
     * Primitive: TIMESTAMP
     * Stores temporal data.
     */
    @Serializable @SerialName("timestamp")
    data class TimestampContent(
        val timestamp: Long,
        val format: String? = null
    ) : NodeContent()
}

/**
 * Generic item for list primitives.
 * @param text The content of the list item.
 * @param isCompleted True if this item represents a completed task. Ignored for standard lists.
 */
@Serializable
data class ListItem(
    val text: String,
    val isCompleted: Boolean = false
)