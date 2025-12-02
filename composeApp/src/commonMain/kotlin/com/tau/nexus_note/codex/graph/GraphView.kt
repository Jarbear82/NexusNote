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
import androidx.compose.material3.*
import com.tau.nexus_note.ui.components.Icon
import com.tau.nexus_note.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.settings.GraphLayoutMode
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val layoutDirection by viewModel.layoutDirection.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val renderingSettings by viewModel.renderingSettings.collectAsState()
    val snapEnabled by viewModel.snapEnabled.collectAsState()

    val selectionRect by viewModel.selectionRect.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val pendingEdgeSourceId by viewModel.pendingEdgeSourceId.collectAsState()
    val dimmedNodeIds by viewModel.dimmedNodeIds.collectAsState()

    // --- Local UI State for Dialogs ---
    var showDetangleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(primarySelectedId) {
        viewModel.setSelectedNode(primarySelectedId)
    }

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { viewModel.updateDensity(density) }

    val velocityTracker = remember { VelocityTracker() }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0))
            .pointerInput(isSelectionMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        velocityTracker.resetTracking()
                        if (isSelectionMode) viewModel.onSelectionDragStart(offset)
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
                        if (isSelectionMode) viewModel.onSelectionDrag(change.position) else viewModel.onPan(dragAmount)
                    }
                )
            }
            .pointerInput(isEditMode) {
                detectTapGestures { offset -> if (isEditMode) viewModel.onBackgroundTap(offset) }
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
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f

        // --- Throttled Visibility Calculation (Phase 4) ---
        // Instead of calculating visibleNodes every frame, we pad the viewport significantly
        // and only recalculate when the camera moves across coarse chunk boundaries.

        val chunkX = (transform.pan.x / 2000f).roundToInt()
        val chunkY = (transform.pan.y / 2000f).roundToInt()
        val currentZoomKey = (transform.zoom * 10).roundToInt() // Re-cull on significant zoom change

        val visibleNodes by remember(nodes, chunkX, chunkY, currentZoomKey) {
            derivedStateOf {
                val buffer = 2000f // Large buffer
                val visibleMinX = ((0f - centerX) / transform.zoom) - transform.pan.x - buffer
                val visibleMinY = ((0f - centerY) / transform.zoom) - transform.pan.y - buffer
                val visibleMaxX = ((constraints.maxWidth.toFloat() - centerX) / transform.zoom) - transform.pan.x + buffer
                val visibleMaxY = ((constraints.maxHeight.toFloat() - centerY) / transform.zoom) - transform.pan.y + buffer
                val expandedBounds = Rect(visibleMinX, visibleMinY, visibleMaxX, visibleMaxY)

                nodes.values.filter { expandedBounds.contains(it.pos) }
            }
        }

        val isLowDetail = transform.zoom < renderingSettings.lodThreshold

        // --- Global Transformation Container (Phase 3) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Apply Pan and Zoom on the GPU to the entire container
                    scaleX = transform.zoom
                    scaleY = transform.zoom
                    translationX = transform.pan.x * transform.zoom + centerX
                    translationY = transform.pan.y * transform.zoom + centerY
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            // 1. Edges (Canvas)
            // Canvas inside a graphicsLayer inherits the transform!
            // So we just draw edges in World Coordinates (node.pos).
            Canvas(modifier = Modifier.fillMaxSize()) {
                edges.forEach { edge ->
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    // Basic culling for edges
                    // Note: Since we are in the transformed container, we just check if nodes exist
                    if (nodeA != null && nodeB != null) {
                        // Draw line from A to B (World Coords)
                        drawRichEdge(nodeA, nodeB, edge, isLowDetail, layoutMode)
                    }
                }
            }

            // 2. Nodes (Low Detail Mode)
            if (isLowDetail) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    visibleNodes.forEach { node ->
                        val isDimmed = dimmedNodeIds.contains(node.id)
                        val color = node.colorInfo.composeColor.copy(alpha = if (isDimmed) 0.2f else 1.0f)
                        drawCircle(color = color, radius = node.radius, center = node.pos)
                    }
                }
            }

            // 3. Nodes (Interactive Mode) - Phase 2 Optimization
            if (!isLowDetail) {
                visibleNodes.forEach { node ->
                    val isSelected = node.id == primarySelectedId
                    val isPendingSource = node.id == pendingEdgeSourceId
                    val isDimmed = dimmedNodeIds.contains(node.id)

                    key(node.id) {
                        NodeWrapper(
                            node = node,
                            zoom = transform.zoom, // Passed for gesture scaling
                            isSelected = isSelected,
                            isPendingSource = isPendingSource,
                            isDimmed = isDimmed,
                            onTap = { if (isEditMode) viewModel.onNodeTap(node.id) else onNodeTap(node.id) },
                            onLongPress = { viewModel.onNodeLockToggle(node.id) },
                            onDragStart = { viewModel.onDragStart(node.id) },
                            onDrag = { delta -> viewModel.onDrag(delta) },
                            onDragEnd = { viewModel.onDragEnd() }
                        )
                    }
                }
            }
        }

        // 4. Selection Box (Overlay - Untransformed space)
        if (selectionRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Blue.copy(alpha = 0.2f), topLeft = selectionRect!!.topLeft, size = selectionRect!!.size)
                drawRect(color = Color.Blue.copy(alpha = 0.5f), topLeft = selectionRect!!.topLeft, size = selectionRect!!.size, style = Stroke(width = 2f))
            }
        }

        // 5. UI Controls & Overlay
        // Standard Controls
        SmallFloatingActionButton(onClick = { viewModel.toggleSettings() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Settings, "Graph Settings") }

        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).height(300.dp)) {
            Slider(
                value = transform.zoom, onValueChange = { viewModel.setZoom(it) }, valueRange = 0.1f..5.0f,
                modifier = Modifier.graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0.5f, 0.5f) }.layout { measurable, constraints -> val placeable = measurable.measure(constraints); layout(placeable.height, placeable.width) { placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2) } }.width(300.dp)
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
                onTriggerLayout = {
                    if (layoutMode == GraphLayoutMode.HIERARCHICAL) {
                        viewModel.onTriggerLayoutAction()
                    } else {
                        showDetangleDialog = true
                    }
                },
                snapEnabled = snapEnabled,
                onSnapToggle = viewModel::toggleSnap,
                lodThreshold = renderingSettings.lodThreshold,
                onLodThresholdChange = viewModel::updateLodThreshold,
                isEditMode = isEditMode,
                onToggleEditMode = viewModel::toggleEditMode,
                isSelectionMode = isSelectionMode,
                onToggleSelectionMode = viewModel::toggleSelectionMode,
                onFitToScreen = viewModel::fitToScreen
            )
        }

        // --- Detangle Dialog Overlay ---
        if (showDetangleDialog) {
            DetangleSettingsDialog(
                onDismiss = { showDetangleDialog = false },
                onDetangle = { algorithm, params ->
                    showDetangleDialog = false
                    viewModel.runDetangle(algorithm, params)
                }
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

        FloatingActionButton(onClick = { viewModel.fitToScreen() }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Default.CenterFocusStrong, "Fit to Screen")
        }

        // Processing Overlay
        if(isProcessing) Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

// --- Phase 2: Optimizing NodeWrapper ---
@Composable
fun NodeWrapper(
    node: GraphNode,
    zoom: Float,
    isSelected: Boolean,
    isPendingSource: Boolean,
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit, onLongPress: () -> Unit, onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit
) {
    // 1. Isolate Stable Properties
    // This allows the inner content to skip recomposition when 'pos' changes.
    val stableProps = remember(node.id, node.label, node.style, node.width, node.height, node.colorInfo, node.backgroundImagePath) {
        val propsMap = when(node) {
            is LongTextGraphNode -> mapOf("content" to node.content)
            is CodeBlockGraphNode -> mapOf("content" to node.code, "language" to node.language)
            is MapGraphNode -> mapOf("data" to com.tau.nexus_note.utils.PropertySerialization.serializeMap(node.data))
            is SetGraphNode -> mapOf("items" to com.tau.nexus_note.utils.PropertySerialization.serializeList(node.items))
            is UnorderedListGraphNode -> mapOf("items" to com.tau.nexus_note.utils.PropertySerialization.serializeList(node.items))
            is OrderedListGraphNode -> mapOf("items" to com.tau.nexus_note.utils.PropertySerialization.serializeList(node.items))
            is TableGraphNode -> mapOf("headers" to com.tau.nexus_note.utils.PropertySerialization.serializeList(node.headers), "data" to com.tau.nexus_note.utils.PropertySerialization.serializeListOfMaps(node.rows), "caption" to (node.caption ?: ""))
            is ImageGraphNode -> mapOf("uri" to node.uri, "altText" to node.altText)
            is HeadingGraphNode -> mapOf("level" to node.level.toString())
            else -> emptyMap()
        }

        StableNodeProps(
            id = node.id,
            label = node.label,
            displayProperty = when(node) {
                is TitleGraphNode -> node.title
                is HeadingGraphNode -> node.text
                is ShortTextGraphNode -> node.text
                is TagGraphNode -> node.name
                else -> node.label
            },
            style = node.style,
            colorInfo = node.colorInfo,
            width = node.width,
            height = node.height,
            properties = propsMap,
            backgroundImagePath = node.backgroundImagePath
        )
    }

    // FIX: Explicitly track the *latest* zoom and callback in a State holder.
    // The pointerInput block is suspended and does NOT restart when 'zoom' changes.
    // By using rememberUpdatedState, the lambda inside detectDragGestures always
    // sees the current value of zoom, ensuring drag physics match visual scale.
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = modifier
            // 2. Use GraphicsLayer for GPU positioning (Relative to World Container)
            // Since the parent box is already transformed by pan/zoom, we just place the node at its world (x,y)
            .graphicsLayer {
                translationX = node.pos.x - (node.width / 2f)
                translationY = node.pos.y - (node.height / 2f)
            }
            // Layout size explicitly to avoid measuring
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(node.width.roundToInt(), node.height.roundToInt())
                )
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // FIX: Multiply by currentZoom (updated state) to convert local scaled delta back to screen pixels.
                        currentOnDrag(dragAmount * currentZoom)
                    },
                    onDragEnd = { onDragEnd() }
                )
            }
            .alpha(if (isDimmed) 0.2f else 1.0f)
    ) {
        if (isSelected) Box(Modifier.matchParentSize().background(Color.Yellow.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
        if (isPendingSource) Box(Modifier.matchParentSize().background(Color.Green.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape).border(2.dp, Color.Green, androidx.compose.foundation.shape.CircleShape))

        // 3. Delegate to Stable Content
        StableNodeContent(props = stableProps)

        if (node.isLocked) Icon(Icons.Default.Lock, "Locked", modifier = Modifier.align(Alignment.TopStart).size(16.dp).offset(x = 4.dp, y = (-4).dp), tint = MaterialTheme.colorScheme.error)
    }
}

private fun DrawScope.drawRichEdge(
    nodeA: GraphNode, nodeB: GraphNode, edge: GraphEdge, isLowDetail: Boolean, layoutMode: GraphLayoutMode
) {
    val color = edge.colorInfo.composeColor.copy(alpha = if (isLowDetail) 0.4f else 0.6f)
    val strokeWidth = if (edge.label == StandardSchemas.EDGE_CONTAINS) 4f else 2f
    val start = nodeA.pos
    val end = nodeB.pos

    if (layoutMode == GraphLayoutMode.HIERARCHICAL) {
        val offset = (edge.sourceId.hashCode() % 10) * 5f - 25f
        val midY = (start.y + end.y) / 2f + offset
        val path = Path().apply { moveTo(start.x, start.y); lineTo(start.x, midY); lineTo(end.x, midY); lineTo(end.x, end.y) }
        drawPath(path, color, style = Stroke(strokeWidth))
        if (!isLowDetail) drawArrow(color, Offset(end.x, midY), end)
        return
    }

    if (edge.isSelfLoop) {
        val path = Path().apply { moveTo(start.x, start.y); cubicTo(start.x + 50f, start.y - 150f, start.x + 150f, start.y - 50f, start.x, start.y) }
        drawPath(path, color, style = Stroke(strokeWidth))
    } else if (edge.isBidirectional) {
        val angle = atan2(end.y - start.y, end.x - start.x)
        val curveHeight = 40f
        val midX = (start.x + end.x) / 2; val midY = (start.y + end.y) / 2
        val cx = midX + curveHeight * cos(angle + Math.PI/2)
        val cy = midY + curveHeight * sin(angle + Math.PI/2)
        val path = Path().apply { moveTo(start.x, start.y); quadraticBezierTo(cx.toFloat(), cy.toFloat(), end.x, end.y) }
        drawPath(path, color, style = Stroke(strokeWidth))
        if (!isLowDetail) drawArrow(color, Offset(cx.toFloat(), cy.toFloat()), end)
    } else {
        drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, pathEffect = if(edge.isProxy) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null)
        if (!isLowDetail) drawArrow(color, start, end)
    }
}

private fun DrawScope.drawArrow(color: Color, start: Offset, end: Offset) {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = 20f
    val arrowAngle = Math.toRadians(25.0)
    // Back off slightly from the center node position so the arrow isn't buried
    val stopX = end.x - 30f * cos(angle)
    val stopY = end.y - 30f * sin(angle)
    val tip = Offset(stopX.toFloat(), stopY.toFloat())
    val x1 = tip.x - arrowLength * cos(angle - arrowAngle); val y1 = tip.y - arrowLength * sin(angle - arrowAngle)
    val x2 = tip.x - arrowLength * cos(angle + arrowAngle); val y2 = tip.y - arrowLength * sin(angle + arrowAngle)
    drawLine(color, tip, Offset(x1.toFloat(), y1.toFloat()), strokeWidth = 3f)
    drawLine(color, tip, Offset(x2.toFloat(), y2.toFloat()), strokeWidth = 3f)
}