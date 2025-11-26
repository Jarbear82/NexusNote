package com.tau.nexus_note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A responsive layout that shows two panes side-by-side on wide screens (with resize),
 * or uses a BottomSheetScaffold on narrow screens (peeking).
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
        val isWideScreen = maxWidth > 700.dp

        if (isWideScreen) {
            // --- Desktop Layout: Side-by-Side with Resize ---
            var sidebarWidth by remember { mutableStateOf(400.dp) }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    listContent()
                }

                // Draggable Handle
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Dragging left increases width
                                val newWidth = (sidebarWidth - dragAmount.x.toDp()).coerceIn(200.dp, 600.dp)
                                sidebarWidth = newWidth
                            }
                        }
                )

                // Sidebar
                Column(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    detailContent()
                }
            }
        } else {
            // --- Mobile Layout: Bottom Sheet Scaffold (Peeking) ---
            val scaffoldState = rememberBottomSheetScaffoldState()

            // If showDetailOnMobile is true, we want the sheet expanded.
            // If false, we want it collapsed (peeking).
            LaunchedEffect(showDetailOnMobile) {
                if (showDetailOnMobile) {
                    scaffoldState.bottomSheetState.expand()
                } else {
                    // scaffoldState.bottomSheetState.partialExpand() or hide depending on logic
                    // If we want it purely peeking when "closed":
                    scaffoldState.bottomSheetState.partialExpand()
                }
            }

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 64.dp, // Always peek 64dp as per design
                sheetContent = {
                    // Constrain height to avoid taking up full screen if content is short,
                    // but allow scrolling if content is long.
                    Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                        detailContent()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                content = { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        listContent()
                    }
                }
            )
        }
    }
}