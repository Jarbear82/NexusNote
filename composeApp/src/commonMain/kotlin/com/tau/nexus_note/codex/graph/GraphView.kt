package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.graph.GraphNode
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

    val layoutMode by viewModel.layoutMode.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val snapEnabled by viewModel.snapEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { viewModel.updateDensity(density) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Parent gestures: Handle Panning (Background Drag) and Zooming
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    viewModel.onPan(dragAmount)
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

        // --- LAYER 1: Edges ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(left = centerX, top = centerY)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {
                edges.forEach { edge ->
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA != null && nodeB != null) {
                        drawRichEdge(nodeA, nodeB, edge)
                    }
                }
            }
        }

        // --- LAYER 2: Nodes ---
        // We render nodes as individual Composables with their own gesture detectors
        nodes.values.forEach { node ->
            val worldX = node.pos.x + transform.pan.x
            val worldY = node.pos.y + transform.pan.y
            val screenX = centerX + (worldX * transform.zoom)
            val screenY = centerY + (worldY * transform.zoom)

            NodeWrapper(
                node = node,
                zoom = transform.zoom,
                screenOffset = Offset(screenX, screenY),
                isSelected = node.id == primarySelectedId,
                onTap = { onNodeTap(node.id) },
                onLongPress = { viewModel.onNodeLockToggle(node.id) },
                onDragStart = { viewModel.onDragStart(node.id) },
                onDrag = { delta -> viewModel.onDrag(delta) },
                onDragEnd = { viewModel.onDragEnd() }
            )
        }

        // --- LAYER 3: UI Controls ---
        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Settings, "Graph Settings") }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(
                layoutMode = layoutMode,
                onLayoutModeChange = viewModel::onLayoutModeChanged,
                physicsOptions = physicsOptions,
                onPhysicsOptionChange = viewModel::updatePhysicsOptions,
                onTriggerLayout = viewModel::onTriggerLayoutAction,
                snapEnabled = snapEnabled,
                onSnapToggle = viewModel::toggleSnap
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
    }
}

@Composable
fun NodeWrapper(
    node: GraphNode,
    zoom: Float,
    screenOffset: Offset,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(screenOffset.x.roundToInt(), screenOffset.y.roundToInt()) }
            // Scale visuals, but keep touch target logic centered
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(-placeable.width / 2, -placeable.height / 2)
                }
            }
            // Gesture Detection specifically for this node
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() }
                )
            }
    ) {
        // Selection visual
        if (isSelected) {
            Box(Modifier.matchParentSize().background(Color.Yellow.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
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
            else -> DefaultNodeView(node as GenericGraphNode)
        }

        // Lock Icon Badge
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

private fun DrawScope.drawRichEdge(nodeA: GraphNode, nodeB: GraphNode, edge: GraphEdge) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.6f)
    val strokeWidth = if (edge.label == "CONTAINS") 4f else 2f

    val start = nodeA.pos
    val end = nodeB.pos

    if (edge.isSelfLoop) {
        val loopRadius = 50f
        val c1 = start + Offset(loopRadius * 2, -loopRadius * 2)
        val c2 = start + Offset(loopRadius * 2, loopRadius * 2)

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
        drawArrow(color, Offset(cx.toFloat(), cy.toFloat()), end)
    }
    else {
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            pathEffect = if(edge.isProxy) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
        )
        drawArrow(color, start, end)
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