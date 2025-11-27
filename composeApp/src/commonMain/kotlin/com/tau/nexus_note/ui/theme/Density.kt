package com.tau.nexus_note.ui.theme

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
    val buttonHeight: Dp,
    val inputVerticalPadding: Dp,
    val tabHeight: Dp,
    val textFieldHeight: Dp,
    val navRailWidth: Dp
)

val CompactTokens = DensityTokens(
    listHeight = 48.dp,
    cornerRadius = 8.dp,
    iconSize = 18.dp,
    bodyFontSize = 10.sp,
    titleFontSize = 12.sp,
    contentPadding = 4.dp,
    buttonHeight = 32.dp,
    inputVerticalPadding = 1.dp,
    tabHeight = 32.dp,
    textFieldHeight = 48.dp,
    navRailWidth = 80.dp
)

val ComfortableTokens = DensityTokens(
    listHeight = 66.dp,
    cornerRadius = 12.dp,
    iconSize = 36.dp,
    bodyFontSize = 14.sp,
    titleFontSize = 16.sp,
    contentPadding = 8.dp,
    buttonHeight = 40.dp,
    inputVerticalPadding = 16.dp,
    tabHeight = 48.dp,
    textFieldHeight = 56.dp, // Standard M3 height
    navRailWidth = 80.dp
)

val LargeTokens = DensityTokens(
    listHeight = 96.dp,
    cornerRadius = 16.dp,
    iconSize = 32.dp,
    bodyFontSize = 16.sp,
    titleFontSize = 22.sp,
    contentPadding = 24.dp,
    buttonHeight = 56.dp,
    inputVerticalPadding = 20.dp,
    tabHeight = 64.dp,
    textFieldHeight = 72.dp,
    navRailWidth = 100.dp
)

val LocalDensityTokens = staticCompositionLocalOf { ComfortableTokens }