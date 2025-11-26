package com.tau.nexus_note.ui.components.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.hexToColor

@OptIn(ExperimentalStdlibApi::class)
@Composable
fun ColorPropertyEditor(
    label: String,
    currentValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (currentValue.isBlank()) Color.White else hexToColor(currentValue)
    val hex = "#" + color.toArgb().toHexString(HexFormat.UpperCase).substring(2)
    val density = LocalDensityTokens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = density.bodyFontSize)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(density.buttonHeight) // Use button height for swatch size
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = hex,
                onValueChange = { newHex ->
                    if (newHex.startsWith("#") && newHex.length <= 9) {
                        onValueChange(newHex)
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Hex Code") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
            )
        }
        Spacer(Modifier.height(4.dp))
        // Simple RGB sliders
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Slider(
                value = color.red,
                onValueChange = { onValueChange("#" + color.copy(red = it).toArgb().toHexString(HexFormat.UpperCase).substring(2)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
            )
            Slider(
                value = color.green,
                onValueChange = { onValueChange("#" + color.copy(green = it).toArgb().toHexString(HexFormat.UpperCase).substring(2)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
            )
            Slider(
                value = color.blue,
                onValueChange = { onValueChange("#" + color.copy(blue = it).toArgb().toHexString(HexFormat.UpperCase).substring(2)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
            )
        }
    }
}