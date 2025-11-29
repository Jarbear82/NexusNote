package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.utils.labelToColor

data class ClusteringResult(
    val nodeMap: Map<Long, Long>, // Maps Original Node ID -> Cluster Node ID
    val clusters: Map<Long, ClusterNode> // Maps Cluster ID -> Cluster Node Object
)

object ClusteringEngine {

    /**
     * Finds nodes with degree 1 (leaves) and groups them into their connected neighbor.
     * The neighbor becomes the "center" of a new ClusterNode.
     */
    fun clusterOutliers(
        nodes: List<NodeDisplayItem>,
        edges: List<EdgeDisplayItem>,
        currentPositions: Map<Long, Offset>
    ): ClusteringResult {
        // 1. Calculate degrees and adjacency
        val adj = mutableMapOf<Long, MutableList<Long>>()
        val degree = mutableMapOf<Long, Int>().withDefault { 0 }

        edges.forEach { edge ->
            adj.getOrPut(edge.src.id) { mutableListOf() }.add(edge.dst.id)
            adj.getOrPut(edge.dst.id) { mutableListOf() }.add(edge.src.id)
            degree[edge.src.id] = degree.getValue(edge.src.id) + 1
            degree[edge.dst.id] = degree.getValue(edge.dst.id) + 1
        }

        val nodeMap = mutableMapOf<Long, Long>()
        val clusters = mutableMapOf<Long, ClusterNode>()
        val processedNodes = mutableSetOf<Long>()

        // Unique ID generator for clusters (using negative range)
        var clusterIdCounter = -1000L

        // 2. Identify Outliers
        val outliers = nodes.filter { degree.getValue(it.id) == 1 }

        outliers.forEach { outlier ->
            if (processedNodes.contains(outlier.id)) return@forEach

            val neighborId = adj[outlier.id]?.firstOrNull() ?: return@forEach

            // Logic:
            // If Neighbor is already part of a cluster, add outlier to it.
            // If Neighbor is NOT in a cluster, create a new cluster centered at Neighbor.

            // Check if neighbor is already mapped to a cluster
            val existingClusterId = nodeMap[neighborId]

            if (existingClusterId != null) {
                // Add to existing
                val cluster = clusters[existingClusterId]!!
                val newContained = cluster.containedNodes + outlier.id
                clusters[existingClusterId] = cluster.copy(
                    containedNodes = newContained,
                    childCount = newContained.size
                )
                nodeMap[outlier.id] = existingClusterId
                processedNodes.add(outlier.id)
            } else {
                // Create new cluster at neighbor
                val neighborNode = nodes.find { it.id == neighborId } ?: return@forEach
                val newClusterId = clusterIdCounter--

                val contained = setOf(neighborId, outlier.id)
                val pos = currentPositions[neighborId] ?: Offset.Zero

                val cluster = ClusterNode(
                    id = newClusterId,
                    containedNodes = contained,
                    childCount = contained.size,
                    label = neighborNode.label,
                    pos = pos,
                    radius = 40f,
                    mass = 20f,
                    colorInfo = labelToColor(neighborNode.label),
                    // Missing required properties initialized to defaults or placeholders
                    width = 100f,
                    height = 100f
                )

                clusters[newClusterId] = cluster
                nodeMap[neighborId] = newClusterId
                nodeMap[outlier.id] = newClusterId

                processedNodes.add(neighborId)
                processedNodes.add(outlier.id)
            }
        }

        return ClusteringResult(nodeMap, clusters)
    }

    /**
     * Finds nodes with high degree (Hubs) and collapses their neighbors into them.
     */
    fun clusterByHubSize(
        nodes: List<NodeDisplayItem>,
        edges: List<EdgeDisplayItem>,
        currentPositions: Map<Long, Offset>,
        threshold: Int
    ): ClusteringResult {
        // 1. Calculate degrees and adjacency
        val adj = mutableMapOf<Long, MutableList<Long>>()
        val degree = mutableMapOf<Long, Int>().withDefault { 0 }

        edges.forEach { edge ->
            adj.getOrPut(edge.src.id) { mutableListOf() }.add(edge.dst.id)
            adj.getOrPut(edge.dst.id) { mutableListOf() }.add(edge.src.id)
            degree[edge.src.id] = degree.getValue(edge.src.id) + 1
            degree[edge.dst.id] = degree.getValue(edge.dst.id) + 1
        }

        val nodeMap = mutableMapOf<Long, Long>()
        val clusters = mutableMapOf<Long, ClusterNode>()
        val processedNodes = mutableSetOf<Long>()
        var clusterIdCounter = -5000L

        // 2. Identify Hubs (sorted by degree desc to greedily take neighbors)
        val hubs = nodes.filter { degree.getValue(it.id) > threshold }
            .sortedByDescending { degree.getValue(it.id) }

        hubs.forEach { hub ->
            if (processedNodes.contains(hub.id)) return@forEach

            // Get available neighbors (not yet clustered)
            val neighbors = adj[hub.id]?.filter { !processedNodes.contains(it) } ?: emptyList()

            if (neighbors.isNotEmpty()) {
                val newClusterId = clusterIdCounter--
                val contained = (neighbors + hub.id).toSet()

                val pos = currentPositions[hub.id] ?: Offset.Zero

                val cluster = ClusterNode(
                    id = newClusterId,
                    containedNodes = contained,
                    childCount = contained.size,
                    label = "HUB",
                    pos = pos,
                    radius = 60f + (neighbors.size * 2f).coerceAtMost(40f), // Grow with size
                    mass = 40f + neighbors.size,
                    colorInfo = labelToColor("HUB"),
                    // Missing required properties initialized to defaults or placeholders
                    width = 100f,
                    height = 100f
                )

                clusters[newClusterId] = cluster

                // Map all to this cluster
                contained.forEach { id ->
                    nodeMap[id] = newClusterId
                    processedNodes.add(id)
                }
            }
        }

        return ClusteringResult(nodeMap, clusters)
    }
}