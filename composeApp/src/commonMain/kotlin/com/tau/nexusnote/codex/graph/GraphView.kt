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
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexusnote.datamodels.GraphEdge
import com.tau.nexusnote.datamodels.GraphNode
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

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectionColor = MaterialTheme.colorScheme.primary
    val compoundFillColor = Color.LightGray.copy(alpha = 0.3f)
    val compoundBorderColor = Color.Gray

    var isDraggingNode by remember { mutableStateOf(false) }

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

                // 1. Draw Compound Nodes
                nodes.values.filter { it.isCompound }.forEach { node ->
                    val halfW = node.width / 2
                    val halfH = node.height / 2
                    val nodeRect = Rect(node.pos.x - halfW, node.pos.y - halfH, node.pos.x + halfW, node.pos.y + halfH)

                    if (visibleWorldRect.overlaps(nodeRect)) {
                        drawRoundRect(compoundFillColor, nodeRect.topLeft, Size(node.width, node.height), CornerRadius(10f, 10f))
                        drawRoundRect(compoundBorderColor, nodeRect.topLeft, Size(node.width, node.height), CornerRadius(10f, 10f), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))

                        if (renderingSettings.showNodeLabels) {
                            drawText(textMeasurer, node.displayProperty, nodeRect.topLeft + Offset(10f, 10f), TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = labelColor))
                        }
                    }
                }

                // 2. Draw Edges
                val edgesByPair = edges.groupBy { Pair(it.sourceId, it.targetId) }
                val pairDrawIndex = mutableMapOf<Pair<Long, Long>, Int>()
                val selfLoopDrawIndex = mutableMapOf<Long, Int>()

                edges.forEach { edge ->
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA != null && nodeB != null) {
                        if (nodeA.id == nodeB.id) {
                            val idx = selfLoopDrawIndex.getOrPut(nodeA.id) { 0 }
                            drawSelfLoop(nodeA, edge, idx, textMeasurer, edgeLabelStyle, renderingSettings.showEdgeLabels)
                            selfLoopDrawIndex[nodeA.id] = idx + 1
                        } else {
                            val pair = Pair(nodeA.id, nodeB.id)
                            val idx = pairDrawIndex.getOrPut(pair) { 0 }
                            // Count total edges between these two (undirected) to curve them
                            val reversePair = Pair(nodeB.id, nodeA.id)
                            val total = (edgesByPair[pair]?.size ?: 0) + (edgesByPair[reversePair]?.size ?: 0)

                            drawCurvedEdge(nodeA, nodeB, edge, idx, total, textMeasurer, edgeLabelStyle, renderingSettings.showEdgeLabels)
                            pairDrawIndex[pair] = idx + 1
                        }
                    }
                }

                // 3. Draw Nodes
                nodes.filterValues { !it.isCompound }.values.forEach { node ->
                    val isSelected = node.id == primarySelectedId || node.id == secondarySelectedId

                    if (node.isHyperNode) {
                        // Hypernodes (Edges): Draw Text Only
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(node.displayProperty),
                            style = edgeLabelStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, background = surfaceColor.copy(alpha = 0.7f))
                        )
                        val topLeft = Offset(
                            x = node.pos.x - (textLayoutResult.size.width / 2f),
                            y = node.pos.y - (textLayoutResult.size.height / 2f)
                        )
                        drawText(textLayoutResult, topLeft = topLeft)
                    } else {
                        // Draw Circle for standard Nodes
                        drawCircle(node.colorInfo.composeColor, node.radius, node.pos)
                        drawCircle(if (isSelected) selectionColor else node.colorInfo.composeFontColor, node.radius, node.pos, style = Stroke(if (isSelected) 3f else 1f))
                    }

                    if (renderingSettings.showNodeLabels && transform.zoom > 0.5f) {
                        val measured = textMeasurer.measure(AnnotatedString(node.displayProperty), edgeLabelStyle)
                        drawText(measured, topLeft = Offset(node.pos.x - measured.size.width/2f, node.pos.y + node.radius + 3f))
                    }
                }
            }

            if(renderingSettings.showCrosshairs) {
                drawLine(crosshairColor, Offset(center.x - 10f, center.y), Offset(center.x + 10f, center.y), 2f)
                drawLine(crosshairColor, Offset(center.x, center.y - 10f), Offset(center.x, center.y + 10f), 2f)
            }
        }

        // Overlay & Controls (Unchanged)
        AnimatedVisibility(visible = isDetangling, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.7f)).pointerInput(Unit){}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("Detangling...") }
            }
        }

        SmallFloatingActionButton(onClick = { viewModel.toggleSettings() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Settings, "Graph Settings") }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(physicsOptions, onDetangleClick, viewModel = viewModel)
        }

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(visible = showFabMenu) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = onAddNodeClick, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Node") }
                    SmallFloatingActionButton(onClick = onAddEdgeClick, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Edge") } // Should represent link
                    if (primarySelectedId != null || secondarySelectedId != null) {
                        SmallFloatingActionButton(onClick = { viewModel.groupSelectedNodes(listOfNotNull(primarySelectedId, secondarySelectedId)) }, containerColor = MaterialTheme.colorScheme.secondary) { Icon(Icons.Default.GroupWork, "Group") }
                    }
                }
            }
            FloatingActionButton(onClick = { viewModel.onFabClick() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "Menu") }
        }
    }
}

// Drawing helpers (Simplified for brevity, same logic as before)
private fun DrawScope.drawSelfLoop(node: GraphNode, edge: GraphEdge, idx: Int, tm: TextMeasurer, style: TextStyle, show: Boolean) {
    val r = 25f + idx*12f
    val center = node.pos + Offset(node.radius, -node.radius) // approximate
    drawCircle(edge.colorInfo.composeColor, r, center, style = Stroke(2f))
    if(show) drawText(tm, edge.label, center, style)
}

private fun DrawScope.drawCurvedEdge(from: GraphNode, to: GraphNode, edge: GraphEdge, idx: Int, total: Int, tm: TextMeasurer, style: TextStyle, show: Boolean) {

    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)

    val arrowSize = 15f

    val labelText = edge.roleLabel ?: edge.label



    if(total == 1) {

        val delta = to.pos - from.pos

        val dist = delta.getDistance()

        val end = if (dist > to.radius) to.pos - (delta / dist) * to.radius else to.pos

        

        drawLine(color, from.pos, end, strokeWidth = 2f)

        drawArrowhead(from.pos, end, color, arrowSize)

        

        if(show) drawText(tm, labelText, (from.pos + to.pos)/2f, style)

    } else {

        // Simple curve logic

        val mid = (from.pos + to.pos) / 2f

        val normal = (to.pos - from.pos).let { Offset(-it.y, it.x) }

        val curve = normal * (if(idx%2==0) 1f else -1f) * 0.2f * ((idx+1)/2).toFloat() // simplified

        val control = mid + curve

        

        val tangent = to.pos - control

        val tDist = tangent.getDistance()

        val end = if (tDist > to.radius) to.pos - (tangent / tDist) * to.radius else to.pos



        val path = Path().apply { moveTo(from.pos.x, from.pos.y); quadraticTo(control.x, control.y, end.x, end.y) }

        drawPath(path, color, style = Stroke(2f))

        drawArrowhead(control, end, color, arrowSize)

        

        if(show) drawText(tm, labelText, control, style)

    }

}



private fun DrawScope.drawArrowhead(from: Offset, to: Offset, color: Color, size: Float) {

    val delta = to - from

    if (delta.getDistance() == 0f) return

    val angle = atan2(delta.y, delta.x)

    val p1 = to - Offset(cos(angle + PI/6).toFloat() * size, sin(angle + PI/6).toFloat() * size)

    val p2 = to - Offset(cos(angle - PI/6).toFloat() * size, sin(angle - PI/6).toFloat() * size)



    val path = Path().apply {

        moveTo(to.x, to.y)

        lineTo(p1.x, p1.y)

        lineTo(p2.x, p2.y)

        close()

    }

    drawPath(path, color)

}
