package com.tau.nexus_note.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.codex.graph.GraphNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Runs a Fruchterman-Reingold static layout algorithm.
 * Emits the state of the graph at each iteration (tick).
 */
fun runFRLayout(
    nodes: Map<Long, GraphNode>,
    edges: List<GraphEdge>,
    params: Map<String, Any>
): Flow<Map<Long, GraphNode>> = flow {
    if (nodes.isEmpty()) {
        emit(emptyMap())
        return@flow
    }

    val iterations = (params["iterations"] as? Number)?.toInt() ?: 500
    val areaInput = (params["area"] as? Number)?.toFloat() ?: 1.0f
    val area = areaInput * 35000f
    val gravity = (params["gravity"] as? Number)?.toFloat() ?: 0.1f
    val speed = 2.0f

    val k = sqrt(area / nodes.size)

    // --- 2. Initialize Node Positions (Spiral) ---
    var spiralIndex = 0
    var currentNodes = nodes.mapValues { (_, node) ->
        val copy = node.copyNode()

        // Phyllotaxis Spiral
        val angle = spiralIndex * 0.5f
        val radius = 50f * sqrt(spiralIndex.toFloat())
        spiralIndex++

        copy.pos = Offset(
            radius * cos(angle),
            radius * sin(angle)
        )
        copy
    }

    // --- 3. Run Iterations ---
    for (i in 0 until iterations) {
        val forces = mutableMapOf<Long, Offset>()
        val nextNodeMap = currentNodes.mapValues { it.value.copyNode() }

        for (node in nextNodeMap.values) {
            forces[node.id] = Offset.Zero
        }

        // Calculate Repulsion
        val nodeList = nextNodeMap.values.toList()
        for (idxA in nodeList.indices) {
            for (idxB in (idxA + 1) until nodeList.size) {
                val nodeA = nodeList[idxA]
                val nodeB = nodeList[idxB]

                val dx = nodeA.pos.x - nodeB.pos.x
                val dy = nodeA.pos.y - nodeB.pos.y
                val absDx = abs(dx)
                val absDy = abs(dy)

                // --- COLLISION LOGIC (Hard) ---
                val spacing = 50f
                val minW = (nodeA.width / 2f) + (nodeB.width / 2f) + spacing
                val minH = (nodeA.height / 2f) + (nodeB.height / 2f) + spacing

                if (absDx < minW && absDy < minH) {
                    // Collision detected - Strong Repulsion
                    val overlapX = minW - absDx
                    val overlapY = minH - absDy
                    val repulsionStrength = k * 1000f

                    val forceVec = if (overlapX < overlapY) {
                        val sign = if (dx > 0) 1f else -1f
                        Offset(sign * repulsionStrength, 0f)
                    } else {
                        val sign = if (dy > 0) 1f else -1f
                        Offset(0f, sign * repulsionStrength)
                    }

                    forces[nodeA.id] = forces[nodeA.id]!! + forceVec
                    forces[nodeB.id] = forces[nodeB.id]!! - forceVec
                } else {
                    // Standard Repulsion
                    val delta = nodeA.pos - nodeB.pos
                    var dist = delta.getDistance()
                    if (dist == 0f) dist = 0.01f

                    val repulsionForce = (k * k) / dist
                    val forceVec = delta.normalized() * repulsionForce

                    forces[nodeA.id] = forces[nodeA.id]!! + forceVec
                    forces[nodeB.id] = forces[nodeB.id]!! - forceVec
                }
            }
        }

        // Attraction
        for (edge in edges) {
            val nodeA = nextNodeMap[edge.sourceId] ?: continue
            val nodeB = nextNodeMap[edge.targetId] ?: continue

            val delta = nodeA.pos - nodeB.pos
            var dist = delta.getDistance()
            if (dist == 0f) dist = 0.01f

            val attractionForce = (dist * dist) / k
            val forceVec = delta.normalized() * attractionForce

            forces[nodeA.id] = forces[nodeA.id]!! - forceVec
            forces[nodeB.id] = forces[nodeB.id]!! + forceVec
        }

        // Gravity
        for (node in nextNodeMap.values) {
            val gravityForce = -node.pos.normalized() * gravity * k
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // Apply Forces
        val coolingFactor = 1.0f - (i.toFloat() / iterations.toFloat())
        for (node in nextNodeMap.values) {
            val force = forces[node.id]!!
            val forceMag = force.getDistance()
            val displacement = force.normalized() * (forceMag * 0.05f * speed).coerceAtMost(coolingFactor * 200f)
            node.pos += displacement
        }

        currentNodes = nextNodeMap
        emit(currentNodes)
    }
}

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}