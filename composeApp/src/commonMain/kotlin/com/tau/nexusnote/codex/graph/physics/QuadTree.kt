package com.tau.nexusnote.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.nexusnote.datamodels.GraphNode

/**
 * Implements a QuadTree for Barnes-Hut optimization.
 * This class recursively subdivides a 2D space to quickly
 * approximate forces from distant groups of nodes.
 *
 * @param boundary The rectangular region this QuadTree node represents.
 */
class QuadTree(val boundary: Rect) {
    // Stores the (degree + 1) mass from ForceAtlas2
    var totalMass: Float = 0f
        private set

    // The mass-weighted center of this quadrant
    var centerOfMass: Offset = Offset.Zero
        private set

    // A leaf node stores a single body.
    private var node: GraphNode? = null

    // Child quadrants
    private var children: Array<QuadTree>? = null
    private var isSubdivided = false

    /**
     * Inserts a node into this QuadTree.
     * @return true if the node was successfully inserted into this quadrant or a child,
     * false if it was outside the boundary.
     */
    fun insert(newNode: GraphNode): Boolean {
        if (!boundary.contains(newNode.pos)) {
            return false // Node is not in this quadrant
        }

        if (node == null && !isSubdivided) {
            // This is an empty leaf, store the node here
            node = newNode
            totalMass = newNode.mass
            // The centerOfMass is (pos * mass), but we divide by totalMass later.
            // For a single node, this simplifies to just the position.
            centerOfMass = newNode.pos
            return true
        }

        // This is an internal node, or a leaf that needs to be subdivided
        if (!isSubdivided) {
            subdivide()
            // Move the original node (which was at this leaf) down into a child
            val originalNode = node!!
            node = null // This is no longer a leaf node

            // Failsafe: if the original node can't be inserted into a child, something is wrong
            // This can happen if its position is exactly on a boundary line.
            if (children!!.none { it.insert(originalNode) }) {
                // For now, we just re-assign it here. This is rare.
                node = originalNode
            }
        }

        // Update this node's mass-weighted center
        // (currentCOM * currentMass) + (newPos * newMass)
        centerOfMass = (centerOfMass * totalMass) + (newNode.pos * newNode.mass)
        totalMass += newNode.mass
        centerOfMass /= totalMass // Divide by new total mass

        // Insert new node into the correct child
        return children!!.any { it.insert(newNode) }
    }

    private fun subdivide() {
        isSubdivided = true
        val halfWidth = boundary.width / 2f
        val halfHeight = boundary.height / 2f
        val left = boundary.left
        val top = boundary.top

        children = arrayOf(
            QuadTree(Rect(left, top, left + halfWidth, top + halfHeight)), // NW
            QuadTree(Rect(left + halfWidth, top, boundary.right, top + halfHeight)), // NE
            QuadTree(Rect(left, top + halfHeight, left + halfWidth, boundary.bottom)), // SW
            QuadTree(Rect(left + halfWidth, top + halfHeight, boundary.right, boundary.bottom))  // SE
        )
    }

    /**
     * Recursively calculates the total repulsion force on a target node.
     */
    fun applyRepulsion(targetNode: GraphNode, options: PhysicsOptions, theta: Float): Offset {
        var netForce = Offset.Zero

        if (node != null && node!!.id != targetNode.id) {
            // Case 1: This is a leaf node with a single, different node. Calculate direct force.
            netForce += calculateForce(targetNode, node!!, options)

        } else if (isSubdivided) {
            // Case 2: This is an internal node (a group).
            val s = boundary.width // Width of the region
            val d = (targetNode.pos - centerOfMass).getDistance() // Distance to group's center of mass

            if (d == 0f) return Offset.Zero // Should not happen, but a failsafe

            // The Barnes-Hut approximation test
            if ((s / d) < theta) {
                // Sufficiently far away: APPROXIMATE
                // Treat this whole quadrant as a single "super-node"
                val delta = centerOfMass - targetNode.pos
                val dist = delta.getDistance()
                if (dist > 0) {
                    // Use totalMass and centerOfMass
                    // This is the ForceAtlas2 1/d repulsion formula
                    netForce += -delta.normalized() * (options.repulsion * targetNode.mass * totalMass) / dist
                }
            } else {
                // Too close: RECURSE
                // Sum forces from all children
                for (child in children!!) {
                    netForce += child.applyRepulsion(targetNode, options, theta)
                }
            }
        }
        // Case 3: Empty leaf (node == null && !isSubdivided) or leaf with the *same* node
        // In this case, force is zero, so we do nothing.

        return netForce
    }

    /**
     * Helper to calculate direct node-to-node force, including collision.
     */
    private fun calculateForce(nodeA: GraphNode, nodeB: GraphNode, options: PhysicsOptions): Offset {
        val delta = nodeB.pos - nodeA.pos
        var dist = delta.getDistance()
        if (dist == 0f) dist = 0.1f // Avoid division by zero

        // Collision detection
        val minAllowableDist = nodeA.radius + nodeB.radius + options.minDistance
        var collisionForce = Offset.Zero
        if (dist < minAllowableDist) {
            val overlap = minAllowableDist - dist
            collisionForce = -delta.normalized() * overlap * options.repulsion * 10f // Stronger force
        }

        // Standard ForceAtlas2 repulsion (1/d)
        val repulsionForce = -delta.normalized() * (options.repulsion * nodeA.mass * nodeB.mass) / dist

        return collisionForce + repulsionForce
    }
}

/**
 * Helper to normalize offset
 */
private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}
