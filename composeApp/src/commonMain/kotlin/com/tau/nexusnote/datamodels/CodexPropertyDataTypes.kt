package com.tau.nexusnote.datamodels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CodexPropertyDataTypes(val displayName: String) {
    @SerialName("Text")
    TEXT("Text"),

    @SerialName("LongText")
    LONG_TEXT("Long Text"),

    @SerialName("Number")
    NUMBER("Number"),

    @SerialName("Date")
    DATE("Date"),

    @SerialName("Image")
    IMAGE("Image"),

    @SerialName("Audio")
    AUDIO("Audio");

    override fun toString(): String {
        return displayName
    }
}