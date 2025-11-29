package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import com.tau.nexus_note.ui.components.Icon
import com.tau.nexus_note.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.tau.nexus_note.settings.GraphLayoutMode
import kotlinx.coroutines.launch
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

    val selectionRect by viewModel.selectionRect.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val pendingEdgeSourceId by viewModel.pendingEdgeSourceId.collectAsState()
    val dimmedNodeIds by viewModel.dimmedNodeIds.collectAsState()

    LaunchedEffect(primarySelectedId) {
        viewModel.setSelectedNode(primarySelectedId)
    }

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { viewModel.updateDensity(density) }

    val textMeasurer = rememberTextMeasurer()
    // Inject TextMeasurer for squarifier logic
    LaunchedEffect(textMeasurer) { viewModel.updateTextMeasurer(textMeasurer) }

    val velocityTracker = remember { VelocityTracker() }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSelectionMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        velocityTracker.resetTracking()
                        if (isSelectionMode) {
                            viewModel.onSelectionDragStart(offset)
                        }
                    },
                    onDragEnd = {
                        if (isSelectionMode) {
                            viewModel.onSelectionDragEnd()
                        } else {
                            val velocity = velocityTracker.calculateVelocity()
                            val velocityOffset = Offset(velocity.x, velocity.y)

                            coroutineScope.launch {
                                var prevValue = Offset.Zero
                                Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(
                                    initialVelocity = velocityOffset,
                                    animationSpec = exponentialDecay(frictionMultiplier = 2.0f)
                                ) {
                                    val delta = value - prevValue
                                    viewModel.onPan(delta)
                                    prevValue = value
                                }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

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

        val buffer = 500f
        val visibleMinX = ((0f - centerX) / transform.zoom) - transform.pan.x - buffer
        val visibleMinY = ((0f - centerY) / transform.zoom) - transform.pan.y - buffer
        val visibleMaxX = ((constraints.maxWidth.toFloat() - centerX) / transform.zoom) - transform.pan.x + buffer
        val visibleMaxY = ((constraints.maxHeight.toFloat() - centerY) / transform.zoom) - transform.pan.y + buffer

        val visibleBounds = Rect(visibleMinX, visibleMinY, visibleMaxX, visibleMaxY)

        val visibleNodes = remember(nodes, visibleBounds) {
            nodes.values.filter { visibleBounds.contains(it.pos) }
        }

        val isLowDetail = transform.zoom < renderingSettings.lodThreshold

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
                        val aVisible = visibleBounds.contains(nodeA.pos)
                        val bVisible = visibleBounds.contains(nodeB.pos)
                        if (aVisible || bVisible) {
                            drawRichEdge(nodeA, nodeB, edge, isLowDetail, layoutMode)
                        }
                    }
                }
            }
        }

        // --- LAYER 2: Nodes ---
        if (isLowDetail) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(left = centerX, top = centerY)
                    scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                    translate(left = transform.pan.x, top = transform.pan.y)
                }) {
                    visibleNodes.forEach { node ->
                        val isDimmed = dimmedNodeIds.contains(node.id)
                        val color = node.colorInfo.composeColor.copy(alpha = if (isDimmed) 0.2f else 1.0f)

                        drawCircle(
                            color = color,
                            radius = node.radius,
                            center = node.pos
                        )
                        if (transform.zoom > 0.2f) {
                            val textResult = textMeasurer.measure(
                                text = node.label,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 20.sp,
                                    color = Color.Black.copy(alpha = if(isDimmed) 0.2f else 1.0f)
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
            visibleNodes.forEach { node ->
                val worldX = node.pos.x + transform.pan.x
                val worldY = node.pos.y + transform.pan.y
                val screenX = centerX + (worldX * transform.zoom)
                val screenY = centerY + (worldY * transform.zoom)

                val isSelected = node.id == primarySelectedId
                val isPendingSource = node.id == pendingEdgeSourceId
                val isDimmed = dimmedNodeIds.contains(node.id)

                key(node.id) {
                    NodeWrapper(
                        node = node,
                        zoom = transform.zoom,
                        screenOffset = Offset(screenX, screenY),
                        isSelected = isSelected,
                        isPendingSource = isPendingSource,
                        isDimmed = isDimmed,
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
                        onSizeChanged = { size -> viewModel.onNodeSizeChanged(node.id, size) },
                        onToggleExpand = { viewModel.toggleNodeExpansion(node.id) }
                    )
                }
            }
        }

        // --- LAYER 3: Selection ---
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

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .height(300.dp)
        ) {
            Slider(
                value = transform.zoom,
                onValueChange = { viewModel.setZoom(it) },
                valueRange = 0.1f..5.0f,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                -placeable.height / 2 + placeable.width / 2
                            )
                        }
                    }
                    .width(300.dp)
            )
        }

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

        FloatingActionButton(
            onClick = { viewModel.fitToScreen() },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(Icons.Default.CenterFocusStrong, "Fit to Screen")
        }

        if(isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

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
    isPendingSource: Boolean,
    isDimmed: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    onToggleExpand: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(screenOffset.x.roundToInt(), screenOffset.y.roundToInt()) }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .alpha(if (isDimmed) 0.2f else 1.0f)
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
            Box(Modifier.matchParentSize().background(Color.Green.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.matchParentSize().border(2.dp, Color.Green, androidx.compose.foundation.shape.CircleShape))
        }

        // Render the specific Node Content
        when(node) {
            is SectionGraphNode -> SectionNodeView(node)
            is DocumentGraphNode -> DocumentNodeView(node)
            is BlockGraphNode -> BlockNodeView(node)
            is CodeBlockGraphNode -> CodeBlockView(node)
            is TableGraphNode -> TableNodeView(node)
            is TagGraphNode -> TagNodeView(node)
            is AttachmentGraphNode -> AttachmentNodeView(node)
            is ClusterNode -> ClusterNodeView(node)
            is ListGraphNode -> ListNodeView(node)
            else -> DefaultNodeView(node as GenericGraphNode)
        }

        // Lock Icon (Top Left)
        if (node.isLocked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.align(Alignment.TopStart).size(16.dp).offset(x = 4.dp, y = (-4).dp),
                tint = MaterialTheme.colorScheme.error
            )
        }

        // Expand/Collapse Icon (Bottom Right) -- FIXED LOCATION
        val showExpand = node is BlockGraphNode || node is CodeBlockGraphNode || node is ListGraphNode || node is TableGraphNode
        if (showExpand) {
            val icon = if (node.isExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .align(Alignment.BottomEnd) // Moved from TopEnd
                    .offset(x = 8.dp, y = 8.dp) // Adjusted offset for bottom corner
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    icon,
                    contentDescription = if(node.isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun DrawScope.drawRichEdge(
    nodeA: GraphNode,
    nodeB: GraphNode,
    edge: GraphEdge,
    isLowDetail: Boolean,
    layoutMode: GraphLayoutMode
) {
    val color = edge.colorInfo.composeColor.copy(alpha = if (isLowDetail) 0.4f else 0.6f)
    val strokeWidth = if (edge.label == "CONTAINS") 4f else 2f

    val start = nodeA.pos
    val end = nodeB.pos

    if (layoutMode == GraphLayoutMode.HIERARCHICAL) {
        val offset = (edge.sourceId.hashCode() % 10) * 5f - 25f
        val midY = (start.y + end.y) / 2f + offset

        val path = Path().apply {
            moveTo(start.x, start.y)
            lineTo(start.x, midY)
            lineTo(end.x, midY)
            lineTo(end.x, end.y)
        }

        drawPath(path, color, style = Stroke(strokeWidth))

        if (!isLowDetail) {
            drawArrow(color, Offset(end.x, midY), end)
        }
        return
    }

    if (edge.isSelfLoop) {
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