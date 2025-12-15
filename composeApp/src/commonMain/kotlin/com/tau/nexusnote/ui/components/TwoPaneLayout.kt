package com.tau.nexusnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A responsive layout that shows two panes side-by-side on wide screens,
 * or uses a draggable BottomSheet on narrow screens.
 *
 * @param listContent The primary content (Graph or List). Always visible.
 * @param detailContent The inspector/editor panel.
 * @param showDetailOnMobile Whether the detail sheet is open on mobile.
 * @param onDismissRequest Callback to close the detail sheet on mobile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoPaneLayout(
    listContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    showDetailOnMobile: Boolean,
    onDismissRequest: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Threshold for switching to mobile layout
        val isWideScreen = maxWidth > 840.dp // Expanded Window Class threshold

        if (isWideScreen) {
            // --- Desktop Layout: Side-by-Side ---
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.7f)) {
                    listContent()
                }
                // Responsive Sidebar
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .widthIn(min = 320.dp, max = 500.dp) // Constrain width
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    detailContent()
                }
            }
        } else {
            // --- Mobile Layout: Bottom Sheet ---

            // 1. Main Content (Always Visible)
            Box(modifier = Modifier.fillMaxSize()) {
                listContent()
            }

            // 2. Draggable Drawer (Bottom Sheet)
            if (showDetailOnMobile) {
                ModalBottomSheet(
                    onDismissRequest = onDismissRequest,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    content = {
                        // Constrain height to avoid taking up full screen if content is short,
                        // but allow scrolling if content is long.
                        Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                            detailContent()
                        }
                    }
                )
            }
        }
    }
}