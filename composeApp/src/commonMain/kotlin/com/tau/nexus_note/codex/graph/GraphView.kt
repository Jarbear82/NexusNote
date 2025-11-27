package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.tau.nexus_note.ui.components.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.datamodels.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File
import kotlin.math.*

@OptIn(ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
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
    val isDetangling by viewModel.isDetangling.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val renderingSettings by viewModel.renderingSettings.collectAsState()
    val isSimulationRunning by viewModel.simulationRunning.collectAsState()

    val textMeasurer = rememberTextMeasurer()
    var isDraggingNode by remember { mutableStateOf(false) }
    var activeProxyEdge by remember { mutableStateOf<GraphEdge?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isSimulationRunning) {
        if (isSimulationRunning) viewModel.runSimulationLoop()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSimulation() }
    }

    BoxWithConstraints(
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
                detectTapGestures(
                    onTap = { offset ->
                        val worldPos = viewModel.screenToWorld(offset)
                        val hitEdge = edges.find { edge ->
                            val nodeA = nodes[edge.sourceId]
                            val nodeB = nodes[edge.targetId]
                            if(nodeA != null && nodeB != null) {
                                val midPoint = (nodeA.pos + nodeB.pos) / 2f
                                (worldPos - midPoint).getDistance() < 15f
                            } else false
                        }

                        if (hitEdge != null) {
                            if (hitEdge.isProxy) {
                                activeProxyEdge = hitEdge
                            } else {
                                onEdgeTap(hitEdge.id)
                            }
                        } else {
                            viewModel.onTap(offset, onNodeTap)
                        }
                    },
                    onLongPress = { offset ->
                        val worldPos = viewModel.screenToWorld(offset)
                        val tappedNode = nodes.values.reversed().find { node ->
                            (worldPos - node.pos).getDistance() < node.radius
                        }
                        if (tappedNode != null) {
                            viewModel.toggleNodeCollapse(tappedNode.id)
                        }
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
            .onSizeChanged { viewModel.onResize(it) }
    ) {
        val scope = this

        // --- 1. Canvas Layer (Edges & Basic Nodes) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {
                edges.forEach { edge ->
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA != null && nodeB != null) {
                        drawGraphEdge(nodeA, nodeB, edge, textMeasurer, renderingSettings.showEdgeLabels)
                    }
                }

                nodes.values.forEach { node ->
                    drawGraphNode(
                        node = node,
                        primaryId = primarySelectedId,
                        secondaryId = secondarySelectedId,
                        textMeasurer = textMeasurer,
                        showLabel = renderingSettings.showNodeLabels,
                        zoom = transform.zoom,
                        selectionColor = selectionColor
                    )
                }
            }
        }

        // --- 2. Composable Layer (Images) ---
        val maxWidthVal = this.constraints.maxWidth.toFloat()
        val maxHeightVal = this.constraints.maxHeight.toFloat()
        val density = LocalDensity.current.density

        nodes.values.forEach { node ->
            if (node.backgroundImagePath != null) {
                val screenPos = (node.pos * transform.zoom) + transform.pan * transform.zoom + Offset(maxWidthVal / 2f, maxHeightVal / 2f)

                if (screenPos.x > -100 && screenPos.x < maxWidthVal + 100 &&
                    screenPos.y > -100 && screenPos.y < maxHeightVal + 100) {

                    val sizePx = (node.radius * 2 * transform.zoom).dp

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (screenPos.x / density).dp - (sizePx/2),
                                y = (screenPos.y / density).dp - (sizePx/2)
                            )
                            .size(sizePx)
                            .clip(CircleShape)
                    ) {
                        // FIX: Use URI string for robust loading
                        val file = File(node.backgroundImagePath!!)
                        KamelImage(
                            resource = { scope.asyncPainterResource(data = file.toURI().toString()) },
                            contentDescription = node.label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onFailure = {
                                // Fallback or debug visual if image fails on graph
                                Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha=0.3f)))
                            }
                        )
                    }
                }
            }
        }

        // --- 3. UI Overlays ---
        if (activeProxyEdge != null) {
            AlertDialog(
                onDismissRequest = { activeProxyEdge = null },
                title = { Text("Aggregated Connections") },
                text = {
                    Column {
                        activeProxyEdge!!.representedConnections.forEach { conn ->
                            Text(conn)
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { activeProxyEdge = null }) { Text("Close") } }
            )
        }

        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Settings, "Graph Settings") }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(options = physicsOptions, onDetangleClick = onDetangleClick)
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

        if(isDetangling) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).pointerInput(Unit){}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Detangling...") }
            }
        }
    }
}

private fun DrawScope.drawGraphNode(
    node: GraphNode,
    primaryId: Long?,
    secondaryId: Long?,
    textMeasurer: TextMeasurer,
    showLabel: Boolean,
    zoom: Float,
    selectionColor: Color
) {
    val isSelected = node.id == primaryId || node.id == secondaryId

    drawCircle(color = node.colorInfo.composeColor, radius = node.radius, center = node.pos)

    if (node.isCollapsed) {
        drawCircle(color = Color.White, radius = node.radius + 4f, center = node.pos, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)))
    }

    drawCircle(
        color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
        radius = node.radius,
        center = node.pos,
        style = Stroke(width = if (isSelected) 4f else 1f)
    )

    if (showLabel && zoom > 0.5f) {
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (12.sp.value / zoom).coerceIn(8f, 24f).sp, color = Color.Black)
        val result = textMeasurer.measure(node.displayProperty, style)
        drawText(result, topLeft = Offset(node.pos.x - result.size.width / 2f, node.pos.y + node.radius + 4f))
    }
}

private fun DrawScope.drawGraphEdge(
    nodeA: GraphNode,
    nodeB: GraphNode,
    edge: GraphEdge,
    textMeasurer: TextMeasurer,
    showLabel: Boolean
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.8f)
    val strokeWidth = if(edge.isProxy) 3f else 2f
    val pathEffect = if(edge.isProxy) PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null

    drawLine(
        color = color,
        start = nodeA.pos,
        end = nodeB.pos,
        strokeWidth = strokeWidth,
        pathEffect = pathEffect
    )

    val mid = (nodeA.pos + nodeB.pos) / 2f

    if (edge.isProxy) {
        drawCircle(Color.White, radius = 8f, center = mid)
        drawCircle(color, radius = 8f, center = mid, style = Stroke(1f))
        val count = edge.representedConnections.size
        if(count > 1) {
            val style = TextStyle(fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            val res = textMeasurer.measure(count.toString(), style)
            drawText(res, topLeft = mid - Offset(res.size.width/2f, res.size.height/2f))
        }
    } else if (showLabel) {
        // IMPROVED: Smaller font and background box
        val style = TextStyle(fontSize = 10.sp, color = Color.Black)
        val res = textMeasurer.measure(edge.label, style)

        // Background box for readability
        val bgRect = Rect(mid - Offset(res.size.width/2f + 2f, res.size.height/2f + 1f), androidx.compose.ui.geometry.Size(res.size.width + 4f, res.size.height + 2f))
        drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = bgRect.topLeft, size = bgRect.size)

        drawText(res, topLeft = mid - Offset(res.size.width/2f, res.size.height/2f))
    }
}