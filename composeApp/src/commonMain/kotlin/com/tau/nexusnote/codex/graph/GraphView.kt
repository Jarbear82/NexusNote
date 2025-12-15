package com.tau.nexusnote.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexusnote.datamodels.GraphEdge
import com.tau.nexusnote.datamodels.GraphNode
import com.tau.nexusnote.datamodels.NodeContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalGraphicsApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GraphView(
    viewModel: GraphViewmodel,
    nodes: Map<Long, GraphNode>,
    edges: List<GraphEdge>,
    primarySelectedId: Long?,
    secondarySelectedId: Long?,
    onNodeTap: (Long) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit,
    onDetangleClick: () -> Unit
) {
    val transform by viewModel.transform.collectAsState()
    val showFabMenu by viewModel.showFabMenu.collectAsState()
    val isDetangling by viewModel.isDetangling.collectAsState()
    val layoutConfig by viewModel.layoutConfig.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val renderingSettings by viewModel.renderingSettings.collectAsState()
    val isSimulationRunning by viewModel.simulationRunning.collectAsState()

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(density, textMeasurer) {
        viewModel.setNodeSizeCalculator(NodeSizeCalculator(textMeasurer, density))
    }

    val paddingPx = with(density) { 16.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }

    val labelColor = MaterialTheme.colorScheme.onSurface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectionColor = MaterialTheme.colorScheme.primary
    val compoundFillColor = Color.LightGray.copy(alpha = 0.3f)
    val compoundBorderColor = Color.Gray

    var isDraggingNode by remember { mutableStateOf(false) }

    LaunchedEffect(isSimulationRunning) {
        if (isSimulationRunning) {
            viewModel.runSimulationLoop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSimulation() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> isDraggingNode = viewModel.onDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isDraggingNode) viewModel.onDrag(dragAmount) else viewModel.onPan(dragAmount)
                    },
                    onDragEnd = {
                        if (isDraggingNode) viewModel.onDragEnd()
                        isDraggingNode = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset -> viewModel.onTap(offset, onNodeTap) })
            }
            .onPointerEvent(PointerEventType.Scroll) {
                it.changes.firstOrNull()?.let { change ->
                    val zoomDelta = if (change.scrollDelta.y < 0) 1.2f else 0.8f
                    viewModel.onZoom(zoomDelta, change.position)
                    change.consume()
                }
            }
            .onSizeChanged { viewModel.onResize(it) }
    ) {
        val edgeLabelStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = (10.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(8.sp.value, 14.sp.value).sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val worldTopLeft = viewModel.screenToWorld(Offset.Zero)
            val worldBottomRight = viewModel.screenToWorld(Offset(size.width, size.height))
            val visibleWorldRect = Rect(worldTopLeft, worldBottomRight)

            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {
                // 1. Compounds
                nodes.values.filter { it.isCompound }.forEach { node ->
                    val halfW = node.width / 2
                    val halfH = node.height / 2
                    val nodeRect = Rect(node.pos.x - halfW, node.pos.y - halfH, node.pos.x + halfW, node.pos.y + halfH)

                    if (visibleWorldRect.overlaps(nodeRect)) {
                        drawRoundRect(compoundFillColor, nodeRect.topLeft, Size(node.width, node.height), CornerRadius(10f, 10f))
                        drawRoundRect(compoundBorderColor, nodeRect.topLeft, Size(node.width, node.height), CornerRadius(10f, 10f),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                        if (renderingSettings.showNodeLabels) {
                            val labelStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (14.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(10.sp.value, 18.sp.value).sp,
                                color = labelColor
                            )
                            drawText(textMeasurer.measure(AnnotatedString(node.displayProperty), labelStyle), topLeft = nodeRect.topLeft + Offset(10f, 10f))
                        }
                    }
                }

                // 2. Edges
                edges.forEach { edge ->
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA != null && nodeB != null) {
                        val startEdge = getRectIntersection(nodeA.pos, nodeA.width, nodeA.height, nodeB.pos)
                        val endEdge = getRectIntersection(nodeB.pos, nodeB.width, nodeB.height, nodeA.pos)
                        val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)

                        drawLine(color, startEdge, endEdge, strokeWidth = 2f)
                        drawArrowhead(startEdge, endEdge, color, 12f)

                        if (renderingSettings.showEdgeLabels && edge.label.isNotBlank()) {
                            val mid = (startEdge + endEdge) / 2f
                            drawText(textMeasurer.measure(AnnotatedString(edge.label), edgeLabelStyle), topLeft = mid)
                        }
                    }
                }

                // 3. Nodes
                drawNodes(nodes.filterValues { !it.isCompound }, visibleWorldRect, textMeasurer, labelColor, selectionColor, transform.zoom, primarySelectedId, secondarySelectedId, renderingSettings.showNodeLabels, paddingPx, gapPx)
            }

            if(renderingSettings.showCrosshairs) {
                drawLine(crosshairColor, Offset(center.x - 10f, center.y), Offset(center.x + 10f, center.y), 2f)
                drawLine(crosshairColor, Offset(center.x, center.y - 10f), Offset(center.x, center.y + 10f), 2f)
            }
        }

        // --- Controls ---
        AnimatedVisibility(visible = isDetangling, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Organizing Layout...", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Magic Detangle Button (Replaces Detangle Dialog)
        SmallFloatingActionButton(
            onClick = { viewModel.startSimulation(fullPipeline = true) },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 16.dp),
            containerColor = MaterialTheme.colorScheme.secondary
        ) {
            Icon(Icons.Default.AutoFixHigh, "Fix Layout")
        }

        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Settings, "Graph Settings")
        }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(
                options = layoutConfig,
                onDetangleClick = onDetangleClick, // Legacy
                viewModel = viewModel,
                primarySelectedId = primarySelectedId,
                secondarySelectedId = secondarySelectedId
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = showFabMenu) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = onAddNodeClick, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Hub, "Create Node") }
                    SmallFloatingActionButton(onClick = onAddEdgeClick, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Link, "Create Edge") }
                    if (primarySelectedId != null || secondarySelectedId != null) {
                        SmallFloatingActionButton(onClick = { viewModel.groupSelectedNodes(listOfNotNull(primarySelectedId, secondarySelectedId)) }, containerColor = MaterialTheme.colorScheme.secondary) { Icon(Icons.Default.GroupWork, "Group") }
                    }
                }
            }
            FloatingActionButton(onClick = { viewModel.onFabClick() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "Menu")
            }
        }
    }
}

// Helper function to find where a line (from center to target) hits the rectangle edge
fun getRectIntersection(center: Offset, width: Float, height: Float, target: Offset): Offset {
    val dx = target.x - center.x
    val dy = target.y - center.y

    if (dx == 0f && dy == 0f) return center

    // Calculate half dimensions
    val hW = width / 2f
    val hH = height / 2f

    // Calculate the scaling factor needed to reach the edge
    // We check both X and Y planes to see which edge we hit first
    val scaleX = if (dx != 0f) hW / abs(dx) else Float.MAX_VALUE
    val scaleY = if (dy != 0f) hH / abs(dy) else Float.MAX_VALUE

    // Use the smaller scale to stay within the box
    val scale = min(scaleX, scaleY)

    return center + Offset(dx * scale, dy * scale)
}

private fun DrawScope.drawArrowhead(from: Offset, to: Offset, color: Color, size: Float) {
    val delta = to - from
    if (delta == Offset.Zero) return

    val angleRad = atan2(delta.y, delta.x)

    val p1 = to + Offset(cos(angleRad + 150f.degToRad()) * size, sin(angleRad + 150f.degToRad()) * size)
    val p2 = to + Offset(cos(angleRad - 150f.degToRad()) * size, sin(angleRad - 150f.degToRad()) * size)

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNodes(
    nodes: Map<Long, GraphNode>,
    visibleWorldRect: Rect,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    selectionColor: Color,
    zoom: Float,
    primarySelectedId: Long?,
    secondarySelectedId: Long?,
    showLabel: Boolean,
    padding: Float, // New param
    gap: Float      // New param
) {
    val minSize = 8.sp
    val maxSize = 14.sp
    val fontSize = ((12.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(minSize.value, maxSize.value)).sp

    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        color = labelColor
    )

    val primaryId = primarySelectedId
    val secondaryId = secondarySelectedId

    for (node in nodes.values) {
        val halfW = node.width / 2
        val halfH = node.height / 2

        val nodeRect = Rect(
            left = node.pos.x - halfW,
            top = node.pos.y - halfH,
            right = node.pos.x + halfW,
            bottom = node.pos.y + halfH
        )
        if (!visibleWorldRect.overlaps(nodeRect)) {
            continue
        }

        val isSelected = node.id == primaryId || node.id == secondaryId
        val borderColor = if(isSelected) selectionColor else node.colorInfo.composeColor
        val bgColor = node.colorInfo.composeColor.copy(alpha = 0.1f)

        if (node.isHyperNode) {
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(node.displayProperty),
                style = style.copy(fontWeight = FontWeight.Bold)
            )

            val topLeft = Offset(
                x = node.pos.x - (textLayoutResult.size.width / 2f),
                y = node.pos.y - (textLayoutResult.size.height / 2f)
            )

            if (isSelected) {
                drawRoundRect(
                    color = selectionColor.copy(alpha = 0.2f),
                    topLeft = topLeft - Offset(4f, 2f),
                    size = Size(
                        textLayoutResult.size.width + 8f,
                        textLayoutResult.size.height + 4f
                    ),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = topLeft,
            )

        } else if (node.content != null) {
            // Render specific node types, driven by config
            when (node.content) {
                is NodeContent.TextContent -> {
                    // drawTextNode is now config-aware and takes padding
                    drawTextNode(node, node.content, textMeasurer, style, bgColor, borderColor, node.config, padding)
                }
                is NodeContent.CodeContent -> {
                    drawCodeNode(node, node.content, textMeasurer, style, Color(0xFF2B2B2B), borderColor, padding)
                }
                is NodeContent.TableContent -> {
                    drawTableNode(node, node.content, textMeasurer, style, bgColor, borderColor, padding)
                }
                is NodeContent.ListContent -> {
                    // New: drawListNode handles tasks/ordered/unordered based on config and uses correct spacing
                    drawListNode(node, node.content, textMeasurer, style, bgColor, borderColor, node.config, padding, gap)
                }
                is NodeContent.MediaContent -> {
                    drawImageNode(node, node.content, textMeasurer, style, bgColor, borderColor, padding)
                }
                else -> {
                    drawDefaultCircleNode(node, isSelected, selectionColor, showLabel, zoom, textMeasurer, style)
                }
            }
        } else {
            drawDefaultCircleNode(node, isSelected, selectionColor, showLabel, zoom, textMeasurer, style)
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawDefaultCircleNode(
    node: GraphNode,
    isSelected: Boolean,
    selectionColor: Color,
    showLabel: Boolean,
    zoom: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle
) {
    drawCircle(
        color = node.colorInfo.composeColor,
        radius = node.radius,
        center = node.pos
    )
    drawCircle(
        color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
        radius = node.radius,
        center = node.pos,
        style = Stroke(width = if (isSelected) 3f else 1f)
    )

    if (showLabel && zoom > 0.5f) {
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(node.displayProperty),
            style = style
        )
        val textPadding = 3f
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x = node.pos.x - (textLayoutResult.size.width / 2f),
                y = node.pos.y + node.radius + textPadding
            )
        )
    }
}

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}

private fun Float.degToRad(): Float {
    return this * (PI.toFloat() / 180f)
}