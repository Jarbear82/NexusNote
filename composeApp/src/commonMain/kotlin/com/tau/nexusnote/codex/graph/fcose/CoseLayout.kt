package com.tau.nexusnote.codex.graph.fcose

import kotlin.math.*
import kotlinx.coroutines.*

/**
 * The Force-Directed layout algorithm (Compound Spring Embedder).
 * Phase 5 Fixes: Corrected Force Magnitudes and RectangularSpring Logic.
 */
class CoseLayout : LayoutPhase {

    private var cellSize = 150.0

    override suspend fun run(graph: FcGraph, config: LayoutConfig) = coroutineScope {
        // Retrieve constraints directly from the graph
        val fixed = graph.fixedConstraints
        val align = graph.alignConstraints
        val relative = graph.relativeConstraints

        // Dynamic Grid Sizing
        updateGridSettings(config)

        var temp = config.initialTemp

        // Pre-process alignment groups
        val verticalGroups = buildAlignmentGroups(graph, align, AlignmentDirection.VERTICAL)
        val horizontalGroups = buildAlignmentGroups(graph, align, AlignmentDirection.HORIZONTAL)

        for (i in 0 until config.maxIterations) {
            // 1. Calculate Forces (Heavy Math on Background Thread)
            calculateRepulsionOptimized(graph, config)
            calculateSpringForces(graph, config)
            calculateGravity(graph, config)

            // 2. Apply Forces & Constraints (Synchronized on Main Thread)
            var maxDisplacement = 0.0

            withContext(Dispatchers.Main) {
                // Intercept & Limit Displacements
                applyRigidStickLogic(verticalGroups, horizontalGroups)
                applyRelativeDisplacementLimits(graph, relative)
                applyFixedNodeLocks(graph, fixed)

                // Move Nodes & Resize Parents
                maxDisplacement = moveNodes(graph, temp)
                updateCompoundNodes(graph)
            }

            // 3. Stabilization Check
            if (maxDisplacement < config.energyThreshold && temp < (config.initialTemp * 0.5)) {
                println("[CoSE] Stabilized at iteration $i (Max Disp: $maxDisplacement)")
                break
            }

            // 4. Cool Down
            if (temp < config.minTemp) {
                break
            }

            temp *= config.coolingFactor

            // Yield occasionally to prevent UI freeze during heavy calculation
            if (i % 4 == 0) yield()
        }
    }

    private fun updateGridSettings(config: LayoutConfig) {
        if (config.gridCellSize != null) {
            cellSize = config.gridCellSize!!
            return
        }
        cellSize = max(config.idealEdgeLength * 4.0, 100.0)
    }

    // --- Forces ---

    private suspend fun calculateRepulsionOptimized(graph: FcGraph, config: LayoutConfig) = coroutineScope {
        val nodes = graph.nodes
        val grid = mutableMapOf<String, MutableList<FcNode>>()

        // Populate Grid
        nodes.forEach { node ->
            val cellKey = getCellKey(node.x, node.y)
            grid.computeIfAbsent(cellKey) { mutableListOf() }.add(node)
        }

        val cpuCount = 4 // Approx or use Runtime
        val chunkSize = max(10, nodes.size / cpuCount)

        nodes.chunked(chunkSize).forEach { chunk ->
            launch {
                chunk.forEach { n1 ->
                    processNodeRepulsion(n1, grid, config)
                }
            }
        }
    }

    private fun processNodeRepulsion(n1: FcNode, grid: Map<String, List<FcNode>>, config: LayoutConfig) {
        val (gx, gy) = getGridCoords(n1.x, n1.y)

        // Check neighboring cells
        for (x in gx - 1..gx + 1) {
            for (y in gy - 1..gy + 1) {
                val key = "${x}_${y}"
                val neighbors = grid[key] ?: continue

                neighbors.forEach { n2 ->
                    if (n1 != n2) {
                        // Don't repel parent/child pairs
                        if (n1.parent != n2 && n2.parent != n1) {
                            applyRepulsionForce(n1, n2, config)
                        }
                    }
                }
            }
        }
    }

    private fun getGridCoords(x: Double, y: Double): Pair<Int, Int> {
        return Pair(floor(x / cellSize).toInt(), floor(y / cellSize).toInt())
    }

    private fun getCellKey(x: Double, y: Double): String {
        val (gx, gy) = getGridCoords(x, y)
        return "${gx}_${gy}"
    }

    private fun applyRepulsionForce(n1: FcNode, n2: FcNode, config: LayoutConfig) {
        val (c1x, c1y) = n1.getCenter()
        val (c2x, c2y) = n2.getCenter()

        var dx = c1x - c2x
        var dy = c1y - c2y
        var dist = sqrt(dx * dx + dy * dy)

        if (dist == 0.0) {
            dx = config.repulsionJitter
            dy = config.repulsionJitter
            dist = config.repulsionJitterDistance
        }

        // Use Diagonal Radius (Circumscribed Circle) to ensure wide rectangles don't clip
        val r1 = sqrt(n1.width * n1.width + n1.height * n1.height) / 2.0
        val r2 = sqrt(n2.width * n2.width + n2.height * n2.height) / 2.0

        // The distance between the *edges* of the bounding circles
        val separation = dist - (r1 + r2)

        // 1. Hard Repulsion for Overlap (Collision prevention)
        if (separation < 0) {
            // FIX: Magnitude was previously too low compared to standard repulsion.
            // Using squared constant ensures consistent force strength.
            val overlap = -separation
            val force = (config.repulsionConstant * config.repulsionConstant) * (overlap + 0.1)

            val fx = (dx / dist) * force
            val fy = (dy / dist) * force

            if (!n1.isFixed) { n1.dispX += fx; n1.dispY += fy }
        }
        // 2. Standard Repulsion (Gravity-like falloff)
        else if (separation < cellSize) {
            val effectiveDist = max(separation, 1.0)
            val force = (config.repulsionConstant * config.repulsionConstant) / effectiveDist

            val fx = (dx / dist) * force
            val fy = (dy / dist) * force

            if (!n1.isFixed) { n1.dispX += fx; n1.dispY += fy }
        }
    }

    private fun calculateSpringForces(graph: FcGraph, config: LayoutConfig) {
        graph.edges.forEach { edge ->
            val source = edge.source
            val target = edge.target

            if (source.parent == target || target.parent == source) return@forEach

            val (sX, sY) = source.getCenter()
            val (tX, tY) = target.getCenter()

            var dx = tX - sX
            var dy = tY - sY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist == 0.0) return@forEach

            // FIX: Use Max Dimension or Diagonal instead of Min.
            // Previously min(w,h) caused wide nodes to be pulled too close.
            val r1 = max(source.width, source.height) / 2.0
            val r2 = max(target.width, target.height) / 2.0

            // Adjust ideal length so the spring target includes the node bodies
            val effectiveIdeal = config.idealEdgeLength + r1 + r2

            // Hooke's Law: force = displacement * k
            // CoSE Approximation: (dist / ideal)^2 roughly
            val force = (dist * dist) / effectiveIdeal

            val fx = (dx / dist) * force
            val fy = (dy / dist) * force

            if (!source.isFixed) {
                source.dispX += fx; source.dispY += fy
            }
            if (!target.isFixed) {
                target.dispX -= fx; target.dispY -= fy
            }
        }
    }

    private fun calculateGravity(graph: FcGraph, config: LayoutConfig) {
        val nodes = graph.nodes
        nodes.forEach { node ->
            if (!node.isFixed) {
                val (cx, cy) = node.getCenter()
                val dist = sqrt(cx * cx + cy * cy)

                if (dist > 0.0) {
                    val force = dist * config.gravityConstant
                    val fx = -(cx / dist) * force
                    val fy = -(cy / dist) * force
                    node.dispX += fx
                    node.dispY += fy
                }
            }
        }
    }

    // --- Constraints (Unchanged) ---

    private fun applyRigidStickLogic(vGroups: List<List<FcNode>>, hGroups: List<List<FcNode>>) {
        vGroups.forEach { group ->
            val nonFixed = group.filter { !it.isFixed }
            if (nonFixed.isNotEmpty()) {
                val avgDispX = nonFixed.map { it.dispX }.average()
                group.forEach { if (!it.isFixed) it.dispX = avgDispX }
            }
        }
        hGroups.forEach { group ->
            val nonFixed = group.filter { !it.isFixed }
            if (nonFixed.isNotEmpty()) {
                val avgDispY = nonFixed.map { it.dispY }.average()
                group.forEach { if (!it.isFixed) it.dispY = avgDispY }
            }
        }
    }

    private fun applyRelativeDisplacementLimits(graph: FcGraph, relative: List<RelativeConstraint>) {
        val passes = 2
        repeat(passes) {
            relative.forEach { rc ->
                val u = graph.getNode(rc.leftNode.id)
                val v = graph.getNode(rc.rightNode.id)
                if (u != null && v != null) {
                    val uNextX = u.x + u.dispX; val uNextY = u.y + u.dispY
                    val vNextX = v.x + v.dispX; val vNextY = v.y + v.dispY

                    if (rc.direction == AlignmentDirection.HORIZONTAL) {
                        val boundary = uNextX + u.width + rc.minGap
                        if (boundary > vNextX) {
                            val overlap = boundary - vNextX
                            if (!u.isFixed && !v.isFixed) { u.dispX -= overlap / 2; v.dispX += overlap / 2 }
                            else if (!u.isFixed) u.dispX -= overlap
                            else if (!v.isFixed) v.dispX += overlap
                        }
                    } else {
                        val boundary = uNextY + u.height + rc.minGap
                        if (boundary > vNextY) {
                            val overlap = boundary - vNextY
                            if (!u.isFixed && !v.isFixed) { u.dispY -= overlap / 2; v.dispY += overlap / 2 }
                            else if (!u.isFixed) u.dispY -= overlap
                            else if (!v.isFixed) v.dispY += overlap
                        }
                    }
                }
            }
        }
    }

    private fun applyFixedNodeLocks(graph: FcGraph, fixed: List<FixedNodeConstraint>) {
        fixed.forEach { fc ->
            val node = graph.getNode(fc.nodeId)
            if (node != null) {
                node.dispX = 0.0
                node.dispY = 0.0
            }
        }
    }

    // --- Helpers ---

    private fun buildAlignmentGroups(
        graph: FcGraph,
        align: List<AlignmentConstraint>,
        dir: AlignmentDirection
    ): List<List<FcNode>> {
        val relevant = align.filter { it.direction == dir }
        return relevant.map { ac -> ac.nodes.mapNotNull { graph.getNode(it.id) } }
    }

    private fun moveNodes(graph: FcGraph, temp: Double): Double {
        var maxDisplacement = 0.0
        graph.nodes.forEach { node ->
            if (!node.isFixed) {
                val dist = sqrt(node.dispX * node.dispX + node.dispY * node.dispY)
                if (dist > 0) {
                    val limitedDist = min(dist, temp)
                    node.x += (node.dispX / dist) * limitedDist
                    node.y += (node.dispY / dist) * limitedDist
                    if (limitedDist > maxDisplacement) maxDisplacement = limitedDist
                }
                node.resetPhysics()
            }
        }
        return maxDisplacement
    }

    private fun updateCompoundNodes(graph: FcGraph) {
        graph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
    }
}