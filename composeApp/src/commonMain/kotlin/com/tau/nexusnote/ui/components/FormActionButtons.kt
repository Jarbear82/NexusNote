package com.tau.nexusnote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FormActionRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    onSecondaryClick: () -> Unit,
    secondaryLabel: String = "Cancel",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled
        ) {
            Text(primaryLabel)
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onSecondaryClick) {
            Text(secondaryLabel)
        }
    }
}