package com.tau.nexus_note.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CodexPropertyDataTypes(val displayName: String) {
    @SerialName("Text")
    TEXT("Text"),

    @SerialName("LongText")
    LONG_TEXT("Long Text"),

    @SerialName("Markdown")
    MARKDOWN("Markdown"),

    @SerialName("Number")
    NUMBER("Number"),

    @SerialName("Boolean")
    BOOLEAN("Checkbox"),

    @SerialName("Date")
    DATE("Date"),

    @SerialName("Color")
    COLOR("Color"),

    @SerialName("List")
    LIST("List (Text)"),

    @SerialName("Map")
    MAP("Map (Key-Value)"),

    @SerialName("Image")
    IMAGE("Image"),

    @SerialName("Audio")
    AUDIO("Audio");

    override fun toString(): String {
        return displayName
    }
}