package com.tau.nexusnote.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines the specific type of content a Node holds.
 * This differentiates how the UI should render the node
 * and which NodeContent subclass is expected.
 */
enum class NodeType {
    MAP, // Generic Key-Value pairs
    TITLE,
    HEADING,
    SHORT_TEXT,
    LONG_TEXT,
    CODE_BLOCK,
    SET,
    UNORDERED_LIST,
    ORDERED_LIST,
    TASK_LIST,
    TABLE,
    IMAGE,
    DATE_TIMESTAMP,
    TAG
}

/**
 * Polymorphic hierarchy for Node Data.
 */
@Serializable
sealed class NodeContent {

    // --- Existing Structure Wrapper ---

    /**
     * Wrapper for the legacy/generic key-value pair structure.
     * Corresponds to NodeType.MAP.
     */
    @Serializable @SerialName("map")
    data class MapContent(val values: Map<String, String>) : NodeContent()

    // --- Text Types ---

    /**
     * Shared content holder for TITLE, HEADING, SHORT_TEXT, LONG_TEXT.
     * The specific rendering style is determined by the NodeType enum.
     */
    @Serializable @SerialName("text")
    data class TextContent(val text: String) : NodeContent()

    /**
     * Updated CodeContent with filename support.
     */
    @Serializable @SerialName("code")
    data class CodeContent(
        val code: String,
        val language: String,
        val filename: String? = null
    ) : NodeContent()

    // --- List Types ---

    /**
     * Content for UNORDERED_LIST and ORDERED_LIST.
     */
    @Serializable @SerialName("list")
    data class ListContent(val items: List<String>) : NodeContent()

    @Serializable @SerialName("task_list")
    data class TaskListContent(val items: List<TaskItem>) : NodeContent()

    @Serializable @SerialName("set")
    data class SetContent(val items: Set<String>) : NodeContent()

    // --- Structured Types ---

    @Serializable @SerialName("table")
    data class TableContent(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : NodeContent()

    @Serializable @SerialName("image")
    data class ImageContent(
        val uri: String,
        val caption: String?
    ) : NodeContent()

    @Serializable @SerialName("date_timestamp")
    data class DateTimestampContent(
        val timestamp: Long,
        val format: String? = null
    ) : NodeContent()

    @Serializable @SerialName("tag")
    data class TagContent(
        val name: String,
        val color: String? = null
    ) : NodeContent()
}

/**
 * Represents a single item in a Task List.
 */
@Serializable
data class TaskItem(
    val text: String,
    val isCompleted: Boolean
)