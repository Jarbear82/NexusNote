package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexus_note.codex.graph.GraphNode
import kotlin.random.Random
import kotlin.math.max

class QuadTree(val boundary: Rect, private val depth: Int = 0) {
    var totalMass: Float = 0f
        private set
    var centerOfMass: Offset = Offset.Zero
        private set
    private var node: GraphNode? = null
    private var children: Array<QuadTree>? = null
    private var isSubdivided = false
    private val MAX_DEPTH = 30

    fun insert(newNode: GraphNode): Boolean {
        if (depth > MAX_DEPTH) return true
        if (!newNode.pos.x.isFinite() || !newNode.pos.y.isFinite()) return false
        if (!boundary.contains(newNode.pos)) return false

        if (node == null && !isSubdivided) {
            node = newNode
            totalMass = newNode.mass
            centerOfMass = newNode.pos
            return true
        }

        if (!isSubdivided) {
            if (node != null) {
                val dist = (node!!.pos - newNode.pos).getDistance()
                if (dist < 0.1f) {
                    val jitter = Offset((Random.nextFloat() - 0.5f) * 1.0f, (Random.nextFloat() - 0.5f) * 1.0f)
                    newNode.pos += jitter
                }
            }
            subdivide()
            val originalNode = node!!
            node = null
            if (children!!.none { it.insert(originalNode) }) {
                node = originalNode
            }
        }

        centerOfMass = (centerOfMass * totalMass) + (newNode.pos * newNode.mass)
        totalMass += newNode.mass
        centerOfMass /= totalMass
        return children!!.any { it.insert(newNode) }
    }

    private fun subdivide() {
        isSubdivided = true
        val halfWidth = boundary.width / 2f
        val halfHeight = boundary.height / 2f
        val left = boundary.left
        val top = boundary.top
        children = arrayOf(
            QuadTree(Rect(left, top, left + halfWidth, top + halfHeight), depth + 1),
            QuadTree(Rect(left + halfWidth, top, boundary.right, top + halfHeight), depth + 1),
            QuadTree(Rect(left, top + halfHeight, left + halfWidth, boundary.bottom), depth + 1),
            QuadTree(Rect(left + halfWidth, top + halfHeight, boundary.right, boundary.bottom), depth + 1)
        )
    }

    fun applyRepulsion(targetNode: GraphNode, options: PhysicsOptions, theta: Float): Offset {
        var netForce = Offset.Zero
        if (node != null && node!!.id != targetNode.id) {
            netForce += calculateForce(targetNode, node!!, options)
        } else if (isSubdivided) {
            val s = boundary.width
            val d = (targetNode.pos - centerOfMass).getDistance()
            if (d == 0f) return Offset.Zero
            if ((s / d) < theta) {
                val delta = centerOfMass - targetNode.pos
                val dist = delta.getDistance()
                if (dist > 0) {
                    netForce += -delta.normalized() * (options.repulsion * targetNode.mass * totalMass) / dist
                }
            } else {
                for (child in children!!) {
                    netForce += child.applyRepulsion(targetNode, options, theta)
                }
            }
        }
        return netForce
    }

    // FIX: Hard Collision Logic
    private fun calculateForce(nodeA: GraphNode, nodeB: GraphNode, options: PhysicsOptions): Offset {
        val delta = nodeB.pos - nodeA.pos
        val realDist = delta.getDistance()

        // Calculate effective distance by subtracting radii (overlap avoidance)
        // Use minDistance as a buffer
        val effectiveDist = max(0.1f, realDist - (nodeA.radius + nodeB.radius + options.minDistance))

        // Check for hard collision (Overlap)
        // If real distance is less than sum of radii, they are touching/overlapping
        val minAllowable = nodeA.radius + nodeB.radius + (options.minDistance * 2)

        if (realDist < minAllowable) {
            // HARD COLLISION: Exponential force to separate
            val overlap = minAllowable - realDist
            // 100x multiplier acts as a solid wall
            return -delta.normalized() * overlap * options.repulsion * 100f
        }

        // Standard Far-Field Repulsion (using center-to-center distance is fine here, or effectiveDist)
        // Vis.js uses effective distance for this too.
        val repulsionForce = -delta.normalized() * (options.repulsion * nodeA.mass * nodeB.mass) / effectiveDist
        return repulsionForce
    }
}

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}