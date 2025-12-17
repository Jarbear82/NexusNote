package com.tau.nexusnote.codex.graph.physics

import androidx.compose.ui.geometry.Offset
import com.tau.nexusnote.datamodels.GraphEdge
import com.tau.nexusnote.datamodels.GraphNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Engine for running various detangling algorithms (Layouts) in a coroutine-friendly way.
 */
object DetangleEngine {

    /**
     * Runs a Fruchterman-Reingold static layout algorithm.
     * Emits the state of the graph at each iteration (tick).
     *
     * @param nodes The initial map of nodes. Compounds should be filtered out by caller if algorithm is leaf-only.
     * @param edges The list of edges.
     * @param params A map containing parameters like "iterations", "area", and "gravity".
     * @return A flow that emits the updated node map for each iteration.
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

        // --- 1. Get Parameters ---
        val iterations = params["iterations"] as? Int ?: 500
        val area = (params["area"] as? Float ?: 1.0f) * 10000f // Scale area for visual space
        val gravity = params["gravity"] as? Float ?: 0.1f
        val speed = 2.0f // A constant speed factor for displacement

        // Optimal distance
        val k = sqrt(area / nodes.size)

        // --- 2. Initialize Node Positions ---
        // For detangling, we generally want to respect current positions unless they are all at 0,0
        var currentNodes = nodes.mapValues { (_, node) ->
            if (node.pos == Offset.Zero) {
                node.copy(
                    pos = Offset(
                        Random.nextFloat() * 10f - 5f,
                        Random.nextFloat() * 10f - 5f
                    )
                )
            } else {
                node.copy()
            }
        }

        // --- 3. Run Iterations ---
        for (i in 0 until iterations) {
            val forces = mutableMapOf<Long, Offset>()
            val nextNodeMap = currentNodes.mapValues { it.value.copy() }

            // Initialize forces
            for (node in nextNodeMap.values) {
                forces[node.id] = Offset.Zero
            }

            // --- Calculate Repulsion (all pairs) ---
            val nodeList = nextNodeMap.values.toList()
            for (idxA in nodeList.indices) {
                for (idxB in (idxA + 1) until nodeList.size) {
                    val nodeA = nodeList[idxA]
                    val nodeB = nodeList[idxB]

                    val delta = nodeA.pos - nodeB.pos
                    var dist = delta.getDistance()
                    if (dist == 0f) dist = 0.01f

                    // f_r = (k*k) / d
                    val repulsionForce = (k * k) / dist
                    val forceVec = delta.normalized() * repulsionForce

                    forces[nodeA.id] = forces[nodeA.id]!! + forceVec
                    forces[nodeB.id] = forces[nodeB.id]!! - forceVec
                }
            }

            // --- Calculate Attraction (edges only) ---
            for (edge in edges) {
                val nodeA = nextNodeMap[edge.sourceId] ?: continue
                val nodeB = nextNodeMap[edge.targetId] ?: continue

                val delta = nodeA.pos - nodeB.pos
                var dist = delta.getDistance()
                if (dist == 0f) dist = 0.01f

                // f_a = (d*d) / k
                val attractionForce = (dist * dist) / k
                val forceVec = delta.normalized() * attractionForce

                forces[nodeA.id] = forces[nodeA.id]!! - forceVec
                forces[nodeB.id] = forces[nodeB.id]!! + forceVec
            }

            // --- Calculate Gravity ---
            for (node in nextNodeMap.values) {
                val gravityForce = -node.pos.normalized() * gravity * k
                forces[node.id] = forces[node.id]!! + gravityForce
            }

            // --- Apply Forces (Displacement) ---
            val coolingFactor = 1.0f - (i.toFloat() / iterations.toFloat())
            for (node in nextNodeMap.values) {
                if (node.isFixed) continue

                val force = forces[node.id]!!
                val forceMag = force.getDistance()

                // Apply displacement, limited by speed and temperature
                val displacement = force.normalized() * (forceMag * 0.01f * speed).coerceAtMost(coolingFactor * 50f)
                node.pos += displacement
            }

            currentNodes = nextNodeMap // Update state for next iteration
            emit(currentNodes) // Emit the new state for visualization
        }
    }

    /**
     * Placeholder for Kamada-Kawai.
     * In a real implementation, this would solve the partial differential equations for energy minimization.
     */
    fun runKKLayout(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        params: Map<String, Any>
    ): Flow<Map<Long, GraphNode>> = flow {
        // Stub implementation - just emits the current nodes to prevent crash
        emit(nodes)
    }

    private fun Offset.normalized(): Offset {
        val mag = this.getDistance()
        return if (mag == 0f) Offset.Zero else this / mag
    }
}