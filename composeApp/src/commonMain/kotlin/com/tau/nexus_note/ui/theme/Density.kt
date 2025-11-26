package com.tau.nexus_note.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DensityTokens(
    val listHeight: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    val bodyFontSize: TextUnit,
    val titleFontSize: TextUnit,
    val contentPadding: Dp,
    // NEW: Specific tokens for interactables
    val buttonHeight: Dp,
    val inputVerticalPadding: Dp // Controls TextField height indirectly
)

val CompactTokens = DensityTokens(
    listHeight = 50.dp,
    cornerRadius = 8.dp,
    iconSize = 18.dp,
    bodyFontSize = 10.sp,
    titleFontSize = 12.sp,
    contentPadding = 8.dp,
    buttonHeight = 32.dp,
    inputVerticalPadding = 2.dp
)

val ComfortableTokens = DensityTokens(
    listHeight = 56.dp,
    cornerRadius = 16.dp,
    iconSize = 24.dp,
    bodyFontSize = 16.sp,
    titleFontSize = 18.sp,
    contentPadding = 16.dp,
    buttonHeight = 40.dp,
    inputVerticalPadding = 16.dp
)

val LargeTokens = DensityTokens(
    listHeight = 72.dp,
    cornerRadius = 24.dp,
    iconSize = 32.dp,
    bodyFontSize = 18.sp,
    titleFontSize = 22.sp,
    contentPadding = 24.dp,
    buttonHeight = 56.dp,
    inputVerticalPadding = 20.dp
)

val LocalDensityTokens = staticCompositionLocalOf { ComfortableTokens }