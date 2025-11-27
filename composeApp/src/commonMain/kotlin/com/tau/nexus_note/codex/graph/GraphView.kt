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
// Use new GraphNode
import com.tau.nexus_note.codex.graph.GraphNode
import com.tau.nexus_note.datamodels.GraphEdge
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
    val isDetangling by viewModel.isDetangling.collectAsState()
    val isSimulationRunning by viewModel.simulationRunning.collectAsState()

    var isDraggingNode by remember { mutableStateOf(false) }

    LaunchedEffect(isSimulationRunning) {
        if (isSimulationRunning) viewModel.runSimulationLoop()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSimulation() }
    }

    val density = LocalDensity.current.density

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
                        // Hit test edges first (approximated)
                        val worldPos = viewModel.screenToWorld(offset)
                        // ... edge hit testing logic ...
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
            .onSizeChanged { viewModel.onResize(it) }
            .background(Color(0xFFE0E0E0)) // Light background for the "board"
    ) {
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f

        // --- LAYER 1: Edges (Canvas) ---
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

        // --- LAYER 2: Nodes (Composables) ---
        // We use a Box scope and absolute offsets based on physics position + transform
        nodes.values.forEach { node ->
            // Calculate screen position
            val worldX = node.pos.x + transform.pan.x
            val worldY = node.pos.y + transform.pan.y

            // Only render if within viewport bounds (culling)
            // Note: Simplistic culling for performance

            val screenX = centerX + (worldX * transform.zoom)
            val screenY = centerY + (worldY * transform.zoom)

            NodeWrapper(
                node = node,
                zoom = transform.zoom,
                screenOffset = Offset(screenX, screenY),
                isSelected = node.id == primarySelectedId
            )
        }

        // --- LAYER 3: UI Overlays ---
        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Settings, "Graph Settings") }

        AnimatedVisibility(visible = showSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)) {
            GraphSettingsView(
                options = com.tau.nexus_note.codex.graph.physics.PhysicsOptions(), // Just defaults for viewing
                onDetangleClick = onDetangleClick
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

        if(isDetangling) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
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
    isSelected: Boolean
) {
    Box(
        modifier = Modifier
            // 1. Move the Top-Left of the box to the physics coordinate
            .offset { IntOffset(screenOffset.x.roundToInt(), screenOffset.y.roundToInt()) }
            // 2. Scale based on zoom, PIVOTING AROUND (0,0) (The Anchor/Physics Point)
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }
            // 3. Center the content dynamically relative to the coordinate
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    // Place the center of the item at (0,0) relative to the offset
                    placeable.placeRelative(-placeable.width / 2, -placeable.height / 2)
                }
            }
    ) {
        // Apply selection border if needed
        if (isSelected) {
            // Selection visual
        }

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
    }
}

private fun DrawScope.drawRichEdge(nodeA: GraphNode, nodeB: GraphNode, edge: GraphEdge) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.6f)
    val stroke = if (edge.label == "CONTAINS") 4f else 2f

    // Draw simple line for now. Bezier curves are better for hierarchy.
    drawLine(
        color = color,
        start = nodeA.pos,
        end = nodeB.pos,
        strokeWidth = stroke,
        pathEffect = if(edge.isProxy) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
    )
}