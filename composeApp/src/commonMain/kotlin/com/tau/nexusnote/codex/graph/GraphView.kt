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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GroupWork
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.GraphNode
import com.tau.nexusnote.datamodels.GraphEdge
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()

    val renderingSettings by viewModel.renderingSettings.collectAsState()

    val isSimulationRunning by viewModel.simulationRunning.collectAsState()

    val textMeasurer = rememberTextMeasurer()
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
        onDispose {
            viewModel.stopSimulation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDraggingNode = viewModel.onDragStart(offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isDraggingNode) {
                            viewModel.onDrag(dragAmount)
                        } else {
                            viewModel.onPan(dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (isDraggingNode) {
                            viewModel.onDragEnd()
                        }
                        isDraggingNode = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        viewModel.onTap(offset, onNodeTap)
                    }
                )
            }
            .onPointerEvent(PointerEventType.Scroll) {
                it.changes.firstOrNull()?.let { change ->
                    val zoomDelta = if (change.scrollDelta.y < 0) 1.2f else 0.8f
                    viewModel.onZoom(zoomDelta, change.position)
                    change.consume()
                }
            }
            .onSizeChanged {
                viewModel.onResize(it)
            }
    ) {
        // --- UPDATED: Explicitly use FontFamily.Monospace ---
        val edgeLabelStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = (10.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(8.sp.value, 14.sp.value).sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calculate the visible rectangle in world coordinates
            val worldTopLeft = viewModel.screenToWorld(Offset.Zero)
            val worldBottomRight = viewModel.screenToWorld(Offset(size.width, size.height))
            val visibleWorldRect = Rect(worldTopLeft, worldBottomRight)

            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {

                // --- 1. Draw Compound Nodes (Backgrounds) ---
                nodes.values.filter { it.isCompound }.forEach { node ->
                    // Culling approximation
                    val halfW = node.width / 2
                    val halfH = node.height / 2
                    val nodeRect = Rect(
                        left = node.pos.x - halfW,
                        top = node.pos.y - halfH,
                        right = node.pos.x + halfW,
                        bottom = node.pos.y + halfH
                    )

                    if (visibleWorldRect.overlaps(nodeRect)) {
                        drawRoundRect(
                            color = compoundFillColor,
                            topLeft = nodeRect.topLeft,
                            size = Size(node.width, node.height),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = compoundBorderColor,
                            topLeft = nodeRect.topLeft,
                            size = Size(node.width, node.height),
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )

                        // Label at Top-Left
                        if (renderingSettings.showNodeLabels) {
                            val labelStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (14.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(10.sp.value, 18.sp.value).sp,
                                color = labelColor
                            )
                            val measured = textMeasurer.measure(AnnotatedString(node.displayProperty), labelStyle)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = nodeRect.topLeft + Offset(10f, 10f)
                            )
                        }
                    }
                }

                // --- 2. Draw Edges ---
                val edgesByPair = edges.groupBy { Pair(it.sourceId, it.targetId) }
                val linkCounts = mutableMapOf<Pair<Long, Long>, Int>()
                val uniquePairs = mutableSetOf<Pair<Long, Long>>()

                for (edge in edges) {
                    if (edge.sourceId == edge.targetId) continue
                    val pair = if (edge.sourceId < edge.targetId) Pair(edge.sourceId, edge.targetId) else Pair(edge.targetId, edge.sourceId)
                    uniquePairs.add(pair)
                }

                for (pair in uniquePairs) {
                    val count = (edgesByPair[pair]?.size ?: 0) + (edgesByPair[Pair(pair.second, pair.first)]?.size ?: 0)
                    linkCounts[pair] = count
                }

                val pairDrawIndex = mutableMapOf<Pair<Long, Long>, Int>()
                val selfLoopDrawIndex = mutableMapOf<Long, Int>()

                for (edge in edges) {
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA == null || nodeB == null) continue

                    // --- CULLING CHECK ---
                    val cullingRect = visibleWorldRect.inflate(200f / transform.zoom)
                    if (!cullingRect.contains(nodeA.pos) && !cullingRect.contains(nodeB.pos)) {
                        continue
                    }

                    if (nodeA.id == nodeB.id) {
                        val index = selfLoopDrawIndex.getOrPut(nodeA.id) { 0 }
                        drawSelfLoop(
                            node = nodeA,
                            edge = edge,
                            index = index,
                            textMeasurer = textMeasurer,
                            style = edgeLabelStyle,
                            showLabel = renderingSettings.showEdgeLabels
                        )
                        selfLoopDrawIndex[nodeA.id] = index + 1
                    } else {
                        val pair = Pair(nodeA.id, nodeB.id)
                        val undirectedPair = if (nodeA.id < nodeB.id) Pair(nodeA.id, nodeB.id) else Pair(nodeB.id, nodeA.id)

                        val total = linkCounts[undirectedPair] ?: 1
                        val index = pairDrawIndex.getOrPut(pair) { 0 }

                        // Note: For Hyperedges (Phase 5), arrow logic is standard:
                        // ViewModel creates spokes as Hypernode -> Participant.
                        // drawCurvedEdge puts arrow at 'to' (Participant).
                        // This matches requirements for N-nary visual representation.

                        drawCurvedEdge(
                            from = nodeA,
                            to = nodeB,
                            edge = edge,
                            index = index,
                            total = total,
                            textMeasurer = textMeasurer,
                            style = edgeLabelStyle,
                            showLabel = renderingSettings.showEdgeLabels
                        )

                        pairDrawIndex[pair] = index + 1
                    }
                }

                // --- 3. Draw Nodes (Foreground) ---
                drawNodes(
                    nodes = nodes.filterValues { !it.isCompound }, // Only simple nodes
                    visibleWorldRect = visibleWorldRect,
                    textMeasurer = textMeasurer,
                    labelColor = labelColor,
                    selectionColor = selectionColor,
                    zoom = transform.zoom,
                    primarySelectedId = primarySelectedId,
                    secondarySelectedId = secondarySelectedId,
                    showLabel = renderingSettings.showNodeLabels
                )
            }

            // --- Draw UI Elements (outside transform) ---
            val crosshairSize = 10f

            if(renderingSettings.showCrosshairs) {
                drawLine(
                    color = crosshairColor,
                    start = Offset(center.x - crosshairSize, center.y),
                    end = Offset(center.x + crosshairSize, center.y),
                    strokeWidth = 2f
                )
                drawLine(
                    color = crosshairColor,
                    start = Offset(center.x, center.y - crosshairSize),
                    end = Offset(center.x, center.y + crosshairSize),
                    strokeWidth = 2f
                )
            }
        }

        // --- Detangling Lockout Overlay ---
        AnimatedVisibility(
            visible = isDetangling,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    // This empty pointerInput consumes all gestures, "locking" the UI
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Detangling Graph... (Interaction Disabled)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }


        // --- Settings Toggle and Panel ---
        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Settings, "Graph Settings")
        }

        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
        ) {
            GraphSettingsView(
                options = physicsOptions,
                onDetangleClick = onDetangleClick,
                viewModel = viewModel,
                primarySelectedId = primarySelectedId,
                secondarySelectedId = secondarySelectedId
            )
        }


        // --- Floating Action Button Menu ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = showFabMenu) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Create Node
                    SmallFloatingActionButton(
                        onClick = { onAddNodeClick() },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = "Create Node")
                    }
                    // Create Edge
                    SmallFloatingActionButton(
                        onClick = { onAddEdgeClick() },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Create Edge")
                    }
                    // Group Selected (Only visible if nodes are selected)
                    if (primarySelectedId != null || secondarySelectedId != null) {
                        SmallFloatingActionButton(
                            onClick = {
                                val ids = listOfNotNull(primarySelectedId, secondarySelectedId)
                                viewModel.groupSelectedNodes(ids)
                            },
                            containerColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Icon(Icons.Default.GroupWork, contentDescription = "Group Selected")
                        }
                    }
                }
            }
            // Main FAB
            FloatingActionButton(
                onClick = { viewModel.onFabClick() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Toggle Create Menu"
                )
            }
        }
    }


}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSelfLoop(
    node: GraphNode,
    edge: GraphEdge,
    index: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    showLabel: Boolean
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)
    val strokeWidth = 2f
    val arrowSize = 12f
    val loopRadius = 25f + (index * 12f)
    val loopSeparation = node.radius + 5f
    val startAngle = (45f).degToRad()
    val endAngle = (-30f).degToRad()

    val p1 = node.pos + Offset(cos(startAngle) * node.radius, sin(startAngle) * node.radius)
    val p4 = node.pos + Offset(cos(endAngle) * node.radius, sin(endAngle) * node.radius)

    val controlOffset = loopSeparation + loopRadius
    val p2 = p1 + Offset(cos(startAngle) * controlOffset, sin(startAngle) * controlOffset)
    val p3 = p4 + Offset(cos(endAngle) * controlOffset, sin(endAngle) * controlOffset)

    val path = Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
    }
    drawPath(path, color, style = Stroke(strokeWidth))

    drawArrowhead(p3, p4, color, arrowSize)

    if (showLabel) {
        val labelPos = (p2 + p3) / 2f
        val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = labelPos - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
            color = style.color
        )
    }


}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCurvedEdge(
    from: GraphNode,
    to: GraphNode,
    edge: GraphEdge,
    index: Int,
    total: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    showLabel: Boolean
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)
    val strokeWidth = 2f
    val arrowSize = 12f

    val start = from.pos
    val end = to.pos
    val delta = end - start
    val midPoint = (start + end) / 2f

    val isStraight = (total == 1)

    // For Hypernodes (Phase 5), we visually start/end at the radius boundary.
    // However, since Hypernodes don't draw a circle, the radius acts as a text padding boundary.
    // This logic remains consistent.

    if (isStraight) {
        val startWithRadius = from.pos + delta.normalized() * from.radius
        val endWithRadius = to.pos - delta.normalized() * to.radius

        drawLine(color, startWithRadius, endWithRadius, strokeWidth)
        drawArrowhead(startWithRadius, endWithRadius, color, arrowSize)

        if (showLabel) {
            val labelOffset = Offset(0f, -10f)
            val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = midPoint + labelOffset - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
                color = style.color
            )
        }
    } else {
        val normal = Offset(-delta.y, delta.x).normalized()
        val baseCurvature = 30f
        val curveSign = if (index % 2 == 0) -1 else 1
        val curveMagnitude = (index + 1) / 2
        val curveOffset = curveSign * curveMagnitude * (baseCurvature * 0.75f)
        val controlPoint = midPoint + normal * (baseCurvature + curveOffset)
        val startWithRadius = from.pos + (controlPoint - from.pos).normalized() * from.radius
        val endWithRadius = to.pos + (controlPoint - to.pos).normalized() * to.radius

        val path = Path().apply {
            moveTo(startWithRadius.x, startWithRadius.y)
            quadraticTo(controlPoint.x, controlPoint.y, endWithRadius.x, endWithRadius.y)
        }
        drawPath(path, color, style = Stroke(strokeWidth))

        val tangent = (endWithRadius - controlPoint).normalized()
        drawArrowhead(endWithRadius - (tangent * arrowSize * 2f), endWithRadius, color, arrowSize)

        if (showLabel) {
            val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = controlPoint - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
                color = style.color
            )
        }
    }


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
    showLabel: Boolean
) {
    val minSize = 8.sp
    val maxSize = 14.sp
    val fontSize = ((12.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(minSize.value, maxSize.value)).sp

    // --- UPDATED: Explicitly use FontFamily.Monospace ---
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        color = labelColor
    )

    val primaryId = primarySelectedId
    val secondaryId = secondarySelectedId

    for (node in nodes.values) {
        // --- CULLING CHECK ---
        val nodeRect = Rect(
            left = node.pos.x - node.radius,
            top = node.pos.y - node.radius,
            right = node.pos.x + node.radius,
            bottom = node.pos.y + node.radius
        )
        if (!visibleWorldRect.overlaps(nodeRect)) {
            continue
        }

        val isSelected = node.id == primaryId || node.id == secondaryId

        if (node.isHyperNode) {
            // --- Phase 5: Hypernodes (Text Only) ---
            // Draw just the display text centered at the node position.
            // We use the edge label (displayProperty) for this.
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(node.displayProperty),
                style = style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )

            // Center the text
            val topLeft = Offset(
                x = node.pos.x - (textLayoutResult.size.width / 2f),
                y = node.pos.y - (textLayoutResult.size.height / 2f)
            )

            // Optional: Draw a subtle background for readability if selected
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
                // color = if (isSelected) selectionColor else node.colorInfo.composeFontColor
            )

        } else {
            // --- Standard Nodes (Circle + Label) ---
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
    }
}

// --- Math Helpers ---

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}

private fun Float.degToRad(): Float {
    return this * (PI.toFloat() / 180f)
}