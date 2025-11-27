package com.tau.nexus_note.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens

/**
 * A generic wrapper around BasicTextField that automatically applies
 * the current density tokens (font size, height, padding).
 *
 * implemented using BasicTextField + DecorationBox to allow custom contentPadding
 * which isn't always available on the standard OutlinedTextField in all M3 versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val interactionSource = remember { MutableInteractionSource() }

    // Merge style with density font size and correct color
    val mergedTextStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontSize = density.bodyFontSize,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .defaultMinSize(minHeight = density.textFieldHeight)
            .then(if (singleLine) Modifier.heightIn(max = density.textFieldHeight) else Modifier),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label?.let { lbl ->
                    @Composable {
                        ProvideTextStyle(value = TextStyle(fontSize = density.bodyFontSize)) {
                            lbl()
                        }
                    }
                },
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = density.inputVerticalPadding
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(density.cornerRadius)
                    )
                }
            )
        }
    )
}