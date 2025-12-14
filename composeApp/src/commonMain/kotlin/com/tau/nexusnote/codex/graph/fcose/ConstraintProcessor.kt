package com.tau.nexusnote.codex.graph.fcose

import kotlin.math.*
import kotlin.collections.ArrayDeque

/**
 * Handles Phase 2 of FCoSE: Constraint Support & Transformation.
 * Implements Algorithm 1 from the FCoSE paper.
 * Refactored for Task 3.1: "Rigid Block" Constraint Enforcement.
 * Refactored for Task 4.1: Cycle Detection and Handling.
 * Updated Phase 1.2: Safe Constraint Processing (No !! assertions).
 * Updated Phase 5: Decoupled Transformation and Enforcement for step-by-step UI.
 * Phase 6 Update: Removed JVM-specific logging.
 */
class ConstraintProcessor(private val graph: FcGraph) {

    private fun logInfo(msg: String) = println("[ConstraintProcessor] INFO: $msg")
    private fun logWarn(msg: String) = println("[ConstraintProcessor] WARN: $msg")
    private fun logError(msg: String, e: Exception? = null) {
        println("[ConstraintProcessor] ERROR: $msg")
        e?.printStackTrace()
    }

    /**
     * STEP 2A: TRANSFORM
     * Calculates and applies Procrustes Analysis or Majority Reflection.
     * Moves the graph globally to "best fit" constraints, but does not snap nodes.
     */
    fun applyTransformation(
        fixed: List<FixedNodeConstraint>,
        align: List<AlignmentConstraint>,
        relative: List<RelativeConstraint>
    ) {
        logInfo("Phase 2A: Transformation (Procrustes/Reflection).")

        // Safe Filtering
        val activeFixed = fixed.filter { graph.getNode(it.nodeId) != null }
        val activeAlign = align.filter { ac -> ac.nodes.all { graph.getNode(it.id) != null } }
        val activeRelative = relative.filter { rc ->
            val l = graph.getNode(rc.leftNode.id)
            val r = graph.getNode(rc.rightNode.id)
            l != null && r != null
        }

        if (activeFixed.size > 1) {
            logInfo("Transformation based on Fixed Nodes.")
            val (source, target) = buildPointsFromFixed(activeFixed)
            applyProcrustes(source, target)

        } else if (activeFixed.size <= 1 && activeAlign.isNotEmpty()) {
            logInfo("Transformation based on Alignment.")
            val (source, target) = buildPointsFromAlignment(activeAlign)
            applyProcrustes(source, target)

            if (activeRelative.isNotEmpty()) {
                applyMajorityReflection(activeRelative)
            }

        } else if (activeFixed.size <= 1 && activeAlign.isEmpty() && activeRelative.isNotEmpty()) {
            logInfo("Transformation based on Relative Constraints.")
            val handledByProcrustes = attemptRelativeProcrustes(activeRelative, activeAlign)

            if (!handledByProcrustes) {
                applyMajorityReflection(activeRelative)
            }
        }
    }

    /**
     * STEP 2B: ENFORCE
     * Strictly enforces constraints. Snaps nodes to specific X/Y coordinates.
     */
    fun enforceConstraints(
        fixed: List<FixedNodeConstraint>,
        align: List<AlignmentConstraint>,
        relative: List<RelativeConstraint>
    ) {
        logInfo("Phase 2B: Strict Enforcement.")

        val activeFixed = fixed.filter { graph.getNode(it.nodeId) != null }
        val activeAlign = align.filter { ac -> ac.nodes.all { graph.getNode(it.id) != null } }
        val activeRelative = relative.filter { rc ->
            val l = graph.getNode(rc.leftNode.id)
            val r = graph.getNode(rc.rightNode.id)
            l != null && r != null
        }

        enforceConstraintsStrict(activeFixed, activeAlign, activeRelative)

        logInfo("Phase 2 Complete.")
    }

    // =========================================================================
    // Part A: Target Configuration Builders
    // =========================================================================

    private fun buildPointsFromFixed(fixed: List<FixedNodeConstraint>): Pair<List<Point>, List<Point>> {
        val sourcePoints = mutableListOf<Point>()
        val targetPoints = mutableListOf<Point>()

        fixed.forEach { fc ->
            val node = graph.getNode(fc.nodeId)
            if (node != null) {
                sourcePoints.add(Point(node.x + node.width / 2, node.y + node.height / 2))
                targetPoints.add(fc.targetPos)
            }
        }
        return Pair(sourcePoints, targetPoints)
    }

    private fun buildPointsFromAlignment(align: List<AlignmentConstraint>): Pair<List<Point>, List<Point>> {
        val sourcePoints = mutableListOf<Point>()
        val targetPoints = mutableListOf<Point>()

        align.forEach { ac ->
            val nodes = ac.nodes.mapNotNull { graph.getNode(it.id) }
            if (nodes.isNotEmpty()) {
                if (ac.direction == AlignmentDirection.HORIZONTAL) {
                    val avgY = nodes.map { it.y + it.height / 2 }.average()
                    nodes.forEach { node ->
                        sourcePoints.add(Point(node.x + node.width / 2, node.y + node.height / 2))
                        targetPoints.add(Point(node.x + node.width / 2, avgY))
                    }
                } else {
                    val avgX = nodes.map { it.x + it.width / 2 }.average()
                    nodes.forEach { node ->
                        sourcePoints.add(Point(node.x + node.width / 2, node.y + node.height / 2))
                        targetPoints.add(Point(avgX, node.y + node.height / 2))
                    }
                }
            }
        }
        return Pair(sourcePoints, targetPoints)
    }

    private fun attemptRelativeProcrustes(
        relative: List<RelativeConstraint>,
        align: List<AlignmentConstraint>
    ): Boolean {
        val allNodes = mutableSetOf<FcNode>()
        val adj = mutableMapOf<FcNode, MutableList<FcNode>>()

        relative.forEach { rc ->
            val u = graph.getNode(rc.leftNode.id)
            val v = graph.getNode(rc.rightNode.id)

            if (u != null && v != null) {
                allNodes.add(u)
                allNodes.add(v)
                adj.computeIfAbsent(u) { mutableListOf() }.add(v)
                adj.computeIfAbsent(v) { mutableListOf() }.add(u)
            }
        }

        if (allNodes.isEmpty()) return false

        val visited = mutableSetOf<FcNode>()
        var maxComponent = listOf<FcNode>()

        allNodes.forEach { node ->
            if (node !in visited) {
                val comp = bfsComponent(node, adj)
                visited.addAll(comp)
                if (comp.size > maxComponent.size) {
                    maxComponent = comp
                }
            }
        }

        if (maxComponent.size < allNodes.size / 2.0) {
            logInfo("Largest relative component too small for Procrustes.")
            return false
        }

        try {
            val (sourcePoints, targetPoints) = buildTargetFromRelativeDAG(maxComponent, relative, align)
            applyProcrustes(sourcePoints, targetPoints)
            return true
        } catch (e: IllegalStateException) {
            logError("Cycle detected in relative constraints during Procrustes attempt. Skipping.", e)
            return false
        }
    }

    private fun bfsComponent(start: FcNode, adj: Map<FcNode, List<FcNode>>): List<FcNode> {
        val comp = mutableListOf<FcNode>()
        val q = ArrayDeque<FcNode>()
        val seen = mutableSetOf<FcNode>()

        q.add(start)
        seen.add(start)
        while (q.isNotEmpty()) {
            val u = q.removeFirst()
            comp.add(u)
            adj[u]?.forEach { v ->
                if (v !in seen) {
                    seen.add(v)
                    q.add(v)
                }
            }
        }
        return comp
    }

    private fun buildTargetFromRelativeDAG(
        component: List<FcNode>,
        allConstraints: List<RelativeConstraint>,
        align: List<AlignmentConstraint>
    ): Pair<List<Point>, List<Point>> {
        val compSet = component.toSet()

        val relevant = allConstraints.filter {
            val u = graph.getNode(it.leftNode.id)
            val v = graph.getNode(it.rightNode.id)
            u != null && v != null && u in compSet && v in compSet
        }

        val hConstraints = relevant.filter { it.direction == AlignmentDirection.HORIZONTAL }
        val vConstraints = relevant.filter { it.direction == AlignmentDirection.VERTICAL }

        val xCoords = calculateDagCoordinates(component, hConstraints, isHorizontal = true, align)
        val yCoords = calculateDagCoordinates(component, vConstraints, isHorizontal = false, align)

        val source = mutableListOf<Point>()
        val target = mutableListOf<Point>()

        component.forEach { node ->
            source.add(Point(node.x + node.width / 2, node.y + node.height / 2))
            target.add(Point(xCoords[node] ?: 0.0, yCoords[node] ?: 0.0))
        }

        return Pair(source, target)
    }

    private fun calculateDagCoordinates(
        nodes: List<FcNode>,
        constraints: List<RelativeConstraint>,
        isHorizontal: Boolean,
        align: List<AlignmentConstraint>
    ): Map<FcNode, Double> {
        val alignmentDir = if (isHorizontal) AlignmentDirection.VERTICAL else AlignmentDirection.HORIZONTAL

        val nodeToBlock = mutableMapOf<FcNode, Int>()
        val blockNodes = mutableMapOf<Int, MutableList<FcNode>>()
        var blockCounter = 0

        nodes.forEach {
            nodeToBlock[it] = blockCounter
            blockNodes[blockCounter] = mutableListOf(it)
            blockCounter++
        }

        val relevantAlign = align.filter { it.direction == alignmentDir }
        relevantAlign.forEach { ac ->
            val participants = ac.nodes.mapNotNull { n -> nodes.find { it.id == n.id } }

            if (participants.size > 1) {
                val rootNode = participants[0]
                val rootBlockId = nodeToBlock[rootNode] ?: return@forEach

                for (i in 1 until participants.size) {
                    val otherNode = participants[i]
                    val otherBlockId = nodeToBlock[otherNode] ?: continue

                    if (rootBlockId != otherBlockId) {
                        val nodesToMove = blockNodes[otherBlockId] ?: continue
                        nodesToMove.forEach { n -> nodeToBlock[n] = rootBlockId }

                        blockNodes[rootBlockId]?.addAll(nodesToMove)
                        blockNodes.remove(otherBlockId)
                    }
                }
            }
        }

        val activeBlockIds = blockNodes.keys
        val blockAdj = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        val blockInDegree = activeBlockIds.associateWith { 0 }.toMutableMap()
        activeBlockIds.forEach { blockAdj[it] = mutableListOf() }

        constraints.forEach { c ->
            val u = nodes.find { it.id == c.leftNode.id }
            val v = nodes.find { it.id == c.rightNode.id }

            if (u != null && v != null) {
                val bU = nodeToBlock[u]
                val bV = nodeToBlock[v]

                if (bU != null && bV != null && bU != bV) {
                    val weight = c.minGap + (if (isHorizontal) u.width else u.height)
                    blockAdj[bU]?.add(bV to weight)
                    val currentDeg = blockInDegree[bV] ?: 0
                    blockInDegree[bV] = currentDeg + 1
                }
            }
        }

        val q = ArrayDeque<Int>()
        blockInDegree.forEach { (bId, deg) -> if (deg == 0) q.add(bId) }

        val topoOrder = mutableListOf<Int>()
        while (q.isNotEmpty()) {
            val u = q.removeFirst()
            topoOrder.add(u)
            blockAdj[u]?.forEach { (v, _) ->
                val d = blockInDegree[v] ?: 0
                blockInDegree[v] = d - 1
                if (blockInDegree[v] == 0) q.add(v)
            }
        }

        if (topoOrder.size < activeBlockIds.size) {
            val directionStr = if (isHorizontal) "Horizontal" else "Vertical"
            val cyclicBlocks = activeBlockIds.filter { !topoOrder.contains(it) }
            val cyclicNodeIds = cyclicBlocks.flatMap { blockNodes[it] ?: emptyList() }
                .map { it.id }
                .sorted()
                .joinToString(", ")

            val errorMsg = "Cycle detected in $directionStr relative constraints. Involved Nodes: [$cyclicNodeIds]."
            throw IllegalStateException(errorMsg)
        }

        val blockCoords = activeBlockIds.associateWith { 0.0 }.toMutableMap()

        activeBlockIds.forEach { bId ->
            val bNodes = blockNodes[bId] ?: emptyList()
            if (bNodes.isNotEmpty()) {
                val avgPos = if (isHorizontal) {
                    bNodes.map { it.x + it.width / 2 }.average()
                } else {
                    bNodes.map { it.y + it.height / 2 }.average()
                }
                blockCoords[bId] = avgPos
            }
        }

        topoOrder.forEach { bU ->
            val currentVal = blockCoords[bU] ?: 0.0
            blockAdj[bU]?.forEach { (bV, weight) ->
                val prevVal = blockCoords[bV] ?: 0.0
                if (prevVal < currentVal + weight) {
                    blockCoords[bV] = currentVal + weight
                }
            }
        }

        val nodeCoords = mutableMapOf<FcNode, Double>()
        nodes.forEach { node ->
            val bid = nodeToBlock[node]
            if (bid != null) {
                nodeCoords[node] = blockCoords[bid] ?: 0.0
            }
        }

        return nodeCoords
    }


    // =========================================================================
    // Part B: Majority Reflection
    // =========================================================================

    private fun applyMajorityReflection(relative: List<RelativeConstraint>) {
        val bounds = graph.getGraphBounds()
        if (bounds == null) {
            logWarn("Skipping Majority Reflection: Graph bounds are invalid.")
            return
        }

        var hViolations = 0
        var hSatisfied = 0
        var vViolations = 0
        var vSatisfied = 0

        relative.forEach { rc ->
            val u = graph.getNode(rc.leftNode.id)
            val v = graph.getNode(rc.rightNode.id)

            if (u != null && v != null) {
                if (rc.direction == AlignmentDirection.HORIZONTAL) {
                    if (u.x > v.x) hViolations++ else hSatisfied++
                } else {
                    if (u.y > v.y) vViolations++ else vSatisfied++
                }
            }
        }

        val cx = bounds.getCenterX()
        val cy = bounds.getCenterY()

        if (hViolations > hSatisfied) {
            logInfo("Applying Majority Reflection on X-axis.")
            graph.nodes.forEach { node ->
                if (!node.isDummy) {
                    val oldDist = node.x + node.width / 2 - cx
                    val newCenter = cx - oldDist
                    node.setCenter(newCenter, node.y + node.height / 2)
                }
            }
        }

        if (vViolations > vSatisfied) {
            logInfo("Applying Majority Reflection on Y-axis.")
            graph.nodes.forEach { node ->
                if (!node.isDummy) {
                    val oldDist = node.y + node.height / 2 - cy
                    val newCenter = cy - oldDist
                    node.setCenter(node.x + node.width / 2, newCenter)
                }
            }
        }

        graph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
    }

    // =========================================================================
    // Part C: Orthogonal Procrustes Implementation
    // =========================================================================

    private fun applyProcrustes(source: List<Point>, target: List<Point>) {
        if (source.isEmpty()) return
        val cSource = getCentroid(source)
        val cTarget = getCentroid(target)

        val srcCentered = source.map { Point(it.x - cSource.x, it.y - cSource.y) }
        val tgtCentered = target.map { Point(it.x - cTarget.x, it.y - cTarget.y) }

        val R = calculateRotationMatrix(srcCentered, tgtCentered)
        applyTransformationToGraph(R, cSource, cTarget)
    }

    private fun getCentroid(points: List<Point>): Point {
        val cx = points.map { it.x }.average()
        val cy = points.map { it.y }.average()
        return Point(cx, cy)
    }

    private fun calculateRotationMatrix(A: List<Point>, B: List<Point>): List<DoubleArray> {
        var hxx = 0.0; var hxy = 0.0; var hyx = 0.0; var hyy = 0.0
        for (i in A.indices) {
            hxx += A[i].x * B[i].x
            hxy += A[i].x * B[i].y
            hyx += A[i].y * B[i].x
            hyy += A[i].y * B[i].y
        }

        val numer = hxy - hyx
        val denom = hxx + hyy
        val theta = atan2(numer, denom)

        val cosT = cos(theta); val sinT = sin(theta)
        val scoreRot = hxx * cosT - hxy * sinT + hyx * sinT + hyy * cosT

        val numerRef = (-hxy) - hyx
        val denomRef = (-hxx) + hyy
        val thetaRef = atan2(numerRef, denomRef)
        val cosRef = cos(thetaRef); val sinRef = sin(thetaRef)
        val scoreRef = hxx * (-cosRef) + hxy * (-sinRef) + hyx * (-sinRef) + hyy * (cosRef)

        return if (scoreRot >= scoreRef) {
            listOf(doubleArrayOf(cosT, -sinT), doubleArrayOf(sinT, cosT))
        } else {
            listOf(doubleArrayOf(-cosRef, -sinRef), doubleArrayOf(-sinRef, cosRef))
        }
    }

    private fun applyTransformationToGraph(
        R: List<DoubleArray>,
        cSource: Point,
        cTarget: Point
    ) {
        graph.nodes.forEach { node ->
            if (!node.isDummy) {
                val x0 = (node.x + node.width / 2) - cSource.x
                val y0 = (node.y + node.height / 2) - cSource.y

                val x1 = x0 * R[0][0] + y0 * R[0][1]
                val y1 = x0 * R[1][0] + y0 * R[1][1]

                node.setCenter(x1 + cTarget.x, y1 + cTarget.y)
            }
        }
        graph.nodes.filter { it.isCompound() }.forEach { it.updateBoundsFromChildren() }
    }


    // =========================================================================
    // Part D: Strict Constraint Enforcement
    // =========================================================================

    private fun enforceConstraintsStrict(
        fixed: List<FixedNodeConstraint>,
        align: List<AlignmentConstraint>,
        relative: List<RelativeConstraint>
    ) {
        // 1. Enforce Fixed Nodes
        fixed.forEach { fc ->
            graph.getNode(fc.nodeId)?.let { node ->
                node.setCenter(fc.targetPos.x, fc.targetPos.y)
                node.isFixed = true
            }
        }

        // 2. Enforce Alignment
        align.forEach { ac ->
            val nodes = ac.nodes.mapNotNull { graph.getNode(it.id) }
            if (nodes.isNotEmpty()) {
                if (ac.direction == AlignmentDirection.HORIZONTAL) {
                    val avgY = nodes.map { it.y + it.height / 2 }.average()
                    nodes.forEach { it.setCenter(it.x + it.width / 2, avgY) }
                } else {
                    val avgX = nodes.map { it.x + it.width / 2 }.average()
                    nodes.forEach { it.setCenter(avgX, it.y + it.height / 2) }
                }
            }
        }

        // 3. Enforce Relative
        val hRel = relative.filter { it.direction == AlignmentDirection.HORIZONTAL }
        val vRel = relative.filter { it.direction == AlignmentDirection.VERTICAL }

        if (hRel.isNotEmpty()) applyDagEnforcement(hRel, isHorizontal = true, align)
        if (vRel.isNotEmpty()) applyDagEnforcement(vRel, isHorizontal = false, align)
    }

    private fun applyDagEnforcement(
        constraints: List<RelativeConstraint>,
        isHorizontal: Boolean,
        align: List<AlignmentConstraint>
    ) {
        val initialNodes = mutableSetOf<FcNode>()
        constraints.forEach {
            graph.getNode(it.leftNode.id)?.let { n -> initialNodes.add(n) }
            graph.getNode(it.rightNode.id)?.let { n -> initialNodes.add(n) }
        }
        if (initialNodes.isEmpty()) return

        val expandedNodes = expandNodesWithAlignment(initialNodes, align, isHorizontal)

        try {
            val coords = calculateDagCoordinates(expandedNodes, constraints, isHorizontal, align)

            expandedNodes.forEach { node ->
                if (coords.containsKey(node)) {
                    if (isHorizontal) {
                        if (!node.isFixed) {
                            node.setCenter(coords[node]!!, node.y + node.height / 2)
                        }
                    } else {
                        if (!node.isFixed) {
                            node.setCenter(node.x + node.width / 2, coords[node]!!)
                        }
                    }
                }
            }
        } catch (e: IllegalStateException) {
            val dirStr = if (isHorizontal) "Horizontal" else "Vertical"
            logError("Cycle detected in $dirStr relative constraints enforcement. Skipping strict enforcement for this group.", e)
        }
    }

    private fun expandNodesWithAlignment(
        seeds: Set<FcNode>,
        align: List<AlignmentConstraint>,
        isHorizontal: Boolean
    ): List<FcNode> {
        val dir = if (isHorizontal) AlignmentDirection.VERTICAL else AlignmentDirection.HORIZONTAL
        val relevantAlign = align.filter { it.direction == dir }

        val result = seeds.toMutableSet()
        val queue = ArrayDeque(seeds)
        val processed = seeds.toMutableSet()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            relevantAlign.forEach { ac ->
                if (ac.nodes.any { it.id == current.id }) {
                    ac.nodes.forEach { alignedNode ->
                        val realNode = graph.getNode(alignedNode.id)
                        if (realNode != null && realNode !in processed) {
                            processed.add(realNode)
                            result.add(realNode)
                            queue.add(realNode)
                        }
                    }
                }
            }
        }
        return result.toList()
    }
}