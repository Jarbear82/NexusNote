package com.tau.nexusnote.codex.graph.fcose

import kotlin.uuid.Uuid

// --- Graph Data Structures ---
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class FcGraph {
    val nodes = mutableListOf<FcNode>()
    val edges = mutableListOf<FcEdge>()
    private val nodeMap = mutableMapOf<String, FcNode>()

    // Constraints are stored within the graph model
    val fixedConstraints = mutableListOf<FixedNodeConstraint>()
    val alignConstraints = mutableListOf<AlignmentConstraint>()
    val relativeConstraints = mutableListOf<RelativeConstraint>()

    fun addNode(
        id: String = Uuid.random().toString(),
        parentId: String? = null,
        isDummy: Boolean = false,
        isRepresentative: Boolean = false
    ): FcNode {
        val node = FcNode(id)
        node.isDummy = isDummy
        node.isRepresentative = isRepresentative

        nodes.add(node)
        nodeMap[id] = node

        if (parentId != null) {
            val parent = nodeMap[parentId]
            if (parent != null) {
                node.parent = parent
                parent.children.add(node)
            }
        }
        return node
    }

    fun addEdge(sourceId: String, targetId: String, isDummy: Boolean = false): FcEdge? {
        val source = nodeMap[sourceId]
        val target = nodeMap[targetId]
        if (source != null && target != null) {
            val edge = FcEdge(source, target, isDummy)
            edges.add(edge)
            source.edges.add(edge)
            target.edges.add(edge)
            return edge
        }
        return null
    }

    fun addEdge(edge: FcEdge) {
        edges.add(edge)
        edge.source.edges.add(edge)
        edge.target.edges.add(edge)
    }

    fun removeNode(node: FcNode) {
        nodes.remove(node)
        nodeMap.remove(node.id)
        node.parent?.children?.remove(node)

        val incidentEdges = node.edges.toList()
        incidentEdges.forEach { edge ->
            edges.remove(edge)
            val other = if (edge.source == node) edge.target else edge.source
            other.edges.remove(edge)
        }
    }

    fun removeEdge(edge: FcEdge) {
        edges.remove(edge)
        edge.source.edges.remove(edge)
        edge.target.edges.remove(edge)
    }

    fun getNode(id: String): FcNode? = nodeMap[id]

    fun resetState() {
        nodes.forEach { it.resetPhysics() }
    }

    fun getGraphBounds(): Rect? {
        val visibleNodes = nodes.filter { !it.isDummy && !it.isRepresentative }
        if (visibleNodes.isEmpty()) return null

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        visibleNodes.forEach {
            if (it.x < minX) minX = it.x
            if (it.y < minY) minY = it.y
            if (it.x + it.width > maxX) maxX = it.x + it.width
            if (it.y + it.height > maxY) maxY = it.y + it.height
        }

        if (minX == Double.MAX_VALUE) return null
        return Rect(minX, minY, maxX - minX, maxY - minY)
    }
}

data class Rect(var x: Double, var y: Double, var w: Double, var h: Double) {
    fun getCenterX() = x + w / 2
    fun getCenterY() = y + h / 2
}

class FcNode(val id: String) {
    // Geometry
    var x: Double = 0.0
    var y: Double = 0.0
    var width: Double = 50.0
    var height: Double = 50.0

    // Hierarchy
    var parent: FcNode? = null
    val children = mutableListOf<FcNode>()

    // Topology
    val edges = mutableListOf<FcEdge>()

    // Physics / Layout State
    var dispX: Double = 0.0
    var dispY: Double = 0.0
    var isFixed: Boolean = false

    // UI Payload (New)
    var data: Any? = null

    // Flags
    var isDummy: Boolean = false
    var isRepresentative: Boolean = false

    fun isCompound(): Boolean = children.isNotEmpty()
    fun isLeaf(): Boolean = children.isEmpty()

    fun resetPhysics() {
        dispX = 0.0
        dispY = 0.0
    }

    fun getCenter(): Pair<Double, Double> {
        return Pair(x + width / 2, y + height / 2)
    }

    fun setCenter(cx: Double, cy: Double) {
        x = cx - width / 2
        y = cy - height / 2
    }

    fun getDegree(): Int = edges.count { !it.isDummy }

    fun updateBoundsFromChildren() {
        if (isLeaf()) return

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var hasValidChild = false

        children.forEach { child ->
            if (!child.isDummy && !child.isRepresentative) {
                child.updateBoundsFromChildren()
                if (child.x < minX) minX = child.x
                if (child.y < minY) minY = child.y
                if (child.x + child.width > maxX) maxX = child.x + child.width
                if (child.y + child.height > maxY) maxY = child.y + child.height
                hasValidChild = true
            }
        }

        if (hasValidChild) {
            val padding = 10.0
            this.x = minX - padding
            this.y = minY - padding
            this.width = (maxX - minX) + (padding * 2)
            this.height = (maxY - minY) + (padding * 2)
        }
    }
}

class FcEdge(val source: FcNode, val target: FcNode, var isDummy: Boolean = false) {
    val id = "${source.id}-${target.id}"
}