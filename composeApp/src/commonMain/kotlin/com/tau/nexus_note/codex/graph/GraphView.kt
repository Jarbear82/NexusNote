package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.tau.nexus_note.ui.components.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.datamodels.GraphEdge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphView(
    viewModel: GraphViewmodel,
    nodes: Map<Long, GraphNode>,
    edges: List<GraphEdge>,
    primarySelectedId: Long?,
    secondarySelectedId: Long?,
    onNodeTap: (Long) -> Unit,
    onEdgeTap: (Long) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit,
    onDetangleClick: () -> Unit
) {
    val transform by viewModel.transform.collectAsState()
    val showFabMenu by viewModel.showFabMenu.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val isProcessing by viewModel.isProcessingLayout.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    val layoutMode by viewModel.layoutMode.collectAsState()
    val layoutDirection by viewModel.layoutDirection.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val renderingSettings by viewModel.renderingSettings.collectAsState()
    val snapEnabled by viewModel.snapEnabled.collectAsState()

    // Phase 4 States
    val selectionRect by viewModel.selectionRect.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val pendingEdgeSourceId by viewModel.pendingEdgeSourceId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { viewModel.updateDensity(density) }

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSelectionMode) {
                // Main Input Handler
                detectDragGestures(
                    onDragStart = { offset ->
                        if (isSelectionMode) {
                            viewModel.onSelectionDragStart(offset)
                        }
                    },
                    onDragEnd = {
                        if (isSelectionMode) {
                            viewModel.onSelectionDragEnd()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isSelectionMode) {
                            viewModel.onSelectionDrag(change.position)
                        } else {
                            viewModel.onPan(dragAmount)
                        }
                    }
                )
            }
            .pointerInput(isEditMode) {
                detectTapGestures { offset ->
                    if (isEditMode) {
                        viewModel.onBackgroundTap(offset)
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll) {
                it.changes.firstOrNull()?.let { change ->
                    val zoomDelta = if (change.scrollDelta.y < 0) 1.2f else 0.8f
                    viewModel.onZoom(zoomDelta, change.position)
                    change.consume()
                }
            }
            .onSizeChanged { viewModel.onResize(it) }
            .background(Color(0xFFE0E0E0))
    ) {
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f

        // --- Culling & LOD Calculation ---
        // 1. Calculate World Viewport
        // ScreenPos = Center + (WorldPos + Pan) * Zoom
        // WorldPos = (ScreenPos - Center) / Zoom - Pan
        val buffer = 500f // Buffer pixels to avoid popping
        val visibleMinX = ((0f - centerX) / transform.zoom) - transform.pan.x - buffer
        val visibleMinY = ((0f - centerY) / transform.zoom) - transform.pan.y - buffer
        val visibleMaxX = ((constraints.maxWidth.toFloat() - centerX) / transform.zoom) - transform.pan.x + buffer
        val visibleMaxY = ((constraints.maxHeight.toFloat() - centerY) / transform.zoom) - transform.pan.y + buffer

        val visibleBounds = Rect(visibleMinX, visibleMinY, visibleMaxX, visibleMaxY)

        // 2. Filter Nodes
        val visibleNodes = remember(nodes, visibleBounds) {
            nodes.values.filter { visibleBounds.contains(it.pos) }
        }

        // 3. LOD Switch
        val isLowDetail = transform.zoom < renderingSettings.lodThreshold

        // --- LAYER 1: Edges (Always Canvas) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(left = centerX, top = centerY)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {
                edges.forEach { edge ->
                    // Optimization: Only draw edges if at least one node is visible
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA != null && nodeB != null) {
                        val aVisible = visibleBounds.contains(nodeA.pos)
                        val bVisible = visibleBounds.contains(nodeB.pos)
                        if (aVisible || bVisible) {
                            drawRichEdge(nodeA, nodeB, edge, isLowDetail)
                        }
                    }
                }
            }
        }

        // --- LAYER 2: Nodes ---
        if (isLowDetail) {
            // LOD MODE: Draw Simple Circles via Canvas (Bypasses Composition)
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(left = centerX, top = centerY)
                    scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                    translate(left = transform.pan.x, top = transform.pan.y)
                }) {
                    visibleNodes.forEach { node ->
                        // Simple dot
                        drawCircle(
                            color = node.colorInfo.composeColor,
                            radius = node.radius,
                            center = node.pos
                        )
                        // Simple text (optional, only if zoom isn't TINY)
                        if (transform.zoom > 0.2f) {
                            val textResult = textMeasurer.measure(
                                text = node.label,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 20.sp,
                                    color = Color.Black
                                )
                            )
                            drawText(
                                textLayoutResult = textResult,
                                topLeft = node.pos + Offset(-textResult.size.width / 2f, node.radius + 5f)
                            )
                        }
                    }
                }
            }
        } else {
            // FULL MODE: Render Composables
            visibleNodes.forEach { node ->
                val worldX = node.pos.x + transform.pan.x
                val worldY = node.pos.y + transform.pan.y
                val screenX = centerX + (worldX * transform.zoom)
                val screenY = centerY + (worldY * transform.zoom)

                val isSelected = node.id == primarySelectedId
                val isPendingSource = node.id == pendingEdgeSourceId

                // Use key to prevent unnecessary recomposition during filter changes
                key(node.id) {
                    NodeWrapper(
                        node = node,
                        zoom = transform.zoom,
                        screenOffset = Offset(screenX, screenY),
                        isSelected = isSelected,
                        isPendingSource = isPendingSource,
                        onTap = {
                            if (isEditMode) {
                                viewModel.onNodeTap(node.id)
                            } else {
                                onNodeTap(node.id)
                            }
                        },
                        onLongPress = { viewModel.onNodeLockToggle(node.id) },
                        onDragStart = { viewModel.onDragStart(node.id) },
                        onDrag = { delta -> viewModel.onDrag(delta) },
                        onDragEnd = { viewModel.onDragEnd() },
                        onSizeChanged = { size -> viewModel.onNodeSizeChanged(node.id, size) }
                    )
                }
            }
        }

        // --- LAYER 3: Selection Rect ---
        if (selectionRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Blue.copy(alpha = 0.2f),
                    topLeft = selectionRect!!.topLeft,
                    size = selectionRect!!.size
                )
                drawRect(
                    color = Color.Blue.copy(alpha = 0.5f),
                    topLeft = selectionRect!!.topLeft,
                    size = selectionRect!!.size,
                    style = Stroke(width = 2f)
                )
            }
        }

        // --- LAYER 4: UI Controls ---
        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Settings, "Graph Settings") }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(
                layoutMode = layoutMode,
                onLayoutModeChange = viewModel::onLayoutModeChanged,
                layoutDirection = layoutDirection,
                onLayoutDirectionChange = viewModel::onLayoutDirectionChanged,
                physicsOptions = physicsOptions,
                onPhysicsOptionChange = viewModel::updatePhysicsOptions,
                onTriggerLayout = viewModel::onTriggerLayoutAction,
                snapEnabled = snapEnabled,
                onSnapToggle = viewModel::toggleSnap,
                onClusterOutliers = viewModel::clusterOutliers,
                onClusterHubs = { viewModel.clusterByHubSize(5) },
                onClearClustering = viewModel::clearClustering,
                lodThreshold = renderingSettings.lodThreshold,
                onLodThresholdChange = viewModel::updateLodThreshold,
                // Phase 4 Controls
                isEditMode = isEditMode,
                onToggleEditMode = viewModel::toggleEditMode,
                isSelectionMode = isSelectionMode,
                onToggleSelectionMode = viewModel::toggleSelectionMode,
                onFitToScreen = viewModel::fitToScreen
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(visible = showFabMenu) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { onAddNodeClick() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Hub, "Create Node") }
                    SmallFloatingActionButton(onClick = { onAddEdgeClick() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Link, "Create Edge") }
                }
            }
            FloatingActionButton(onClick = { viewModel.onFabClick() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "Toggle Create Menu")
            }
        }

        if(isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // --- Loading / Stabilization Overlay ---
        loadingProgress?.let { progress ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(progress, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun NodeWrapper(
    node: GraphNode,
    zoom: Float,
    screenOffset: Offset,
    isSelected: Boolean,
    isPendingSource: Boolean, // For Edit Mode Highlighting
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSizeChanged: (IntSize) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(screenOffset.x.roundToInt(), screenOffset.y.roundToInt()) }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .onSizeChanged { onSizeChanged(it) }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(-placeable.width / 2, -placeable.height / 2)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(zoom) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount * zoom)
                    },
                    onDragEnd = { onDragEnd() }
                )
            }
    ) {
        if (isSelected) {
            Box(Modifier.matchParentSize().background(Color.Yellow.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
        }
        if (isPendingSource) {
            // Highlight source node for edge creation
            Box(Modifier.matchParentSize().background(Color.Green.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.matchParentSize().border(2.dp, Color.Green, androidx.compose.foundation.shape.CircleShape))
        }

        // Render content
        when(node) {
            is SectionGraphNode -> SectionNodeView(node)
            is DocumentGraphNode -> DocumentNodeView(node)
            is BlockGraphNode -> BlockNodeView(node)
            is CodeBlockGraphNode -> CodeBlockView(node)
            is TableGraphNode -> TableNodeView(node)
            is TagGraphNode -> TagNodeView(node)
            is AttachmentGraphNode -> AttachmentNodeView(node)
            is ClusterNode -> ClusterNodeView(node)
            else -> DefaultNodeView(node as GenericGraphNode)
        }

        if (node.isLocked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp).offset(x = 4.dp, y = (-4).dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun DrawScope.drawRichEdge(nodeA: GraphNode, nodeB: GraphNode, edge: GraphEdge, isLowDetail: Boolean) {
    val color = edge.colorInfo.composeColor.copy(alpha = if (isLowDetail) 0.4f else 0.6f)
    val strokeWidth = if (edge.label == "CONTAINS") 4f else 2f

    val start = nodeA.pos
    val end = nodeB.pos

    if (edge.isSelfLoop) {
        val loopRadius = 50f
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                start.x + 50f, start.y - 150f,
                start.x + 150f, start.y - 50f,
                start.x, start.y
            )
        }
        drawPath(path, color, style = Stroke(strokeWidth))
    }
    else if (edge.isBidirectional) {
        val angle = atan2(end.y - start.y, end.x - start.x)
        val curveHeight = 40f
        val midX = (start.x + end.x) / 2
        val midY = (start.y + end.y) / 2
        val cx = midX + curveHeight * cos(angle + Math.PI/2)
        val cy = midY + curveHeight * sin(angle + Math.PI/2)

        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(cx.toFloat(), cy.toFloat(), end.x, end.y)
        }
        drawPath(path, color, style = Stroke(strokeWidth))
        if (!isLowDetail) {
            drawArrow(color, Offset(cx.toFloat(), cy.toFloat()), end)
        }
    }
    else {
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            pathEffect = if(edge.isProxy) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
        )
        if (!isLowDetail) {
            drawArrow(color, start, end)
        }
    }
}

private fun DrawScope.drawArrow(color: Color, start: Offset, end: Offset) {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = 20f
    val arrowAngle = Math.toRadians(25.0)
    val stopX = end.x - 30f * cos(angle)
    val stopY = end.y - 30f * sin(angle)
    val tip = Offset(stopX.toFloat(), stopY.toFloat())

    val x1 = tip.x - arrowLength * cos(angle - arrowAngle)
    val y1 = tip.y - arrowLength * sin(angle - arrowAngle)
    val x2 = tip.x - arrowLength * cos(angle + arrowAngle)
    val y2 = tip.y - arrowLength * sin(angle + arrowAngle)

    drawLine(color, tip, Offset(x1.toFloat(), y1.toFloat()), strokeWidth = 3f)
    drawLine(color, tip, Offset(x2.toFloat(), y2.toFloat()), strokeWidth = 3f)
}