package com.tau.nexus_note.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens

/**
 * A generic wrapper around OutlinedTextField that automatically applies
 * the current density tokens (font size, height).
 */
@Composable
fun CodexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val density = LocalDensityTokens.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            // We use defaultMinHeight to allow it to shrink for compact mode
            // but grow if multiline.
            .defaultMinSize(minHeight = density.textFieldHeight)
            .then(if (singleLine) Modifier.heightIn(max = density.textFieldHeight) else Modifier),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = LocalTextStyle.current.copy(fontSize = density.bodyFontSize),
        label = {
            // Apply density to label as well
            if (label != null) {
                androidx.compose.material3.ProvideTextStyle(value = TextStyle(fontSize = density.bodyFontSize)) {
                    label()
                }
            }
        },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        colors = colors
    )
}