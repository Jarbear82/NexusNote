package com.tau.nexusnote.datamodels

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/**
 * Step 2.1: GraphNode now implements GraphEntity.
 * Replaced single 'label' with 'schemas' list.
 */
class GraphNode(
    override val id: Long,
    val schemas: List<SchemaDefinition>, // Replaces single label
    val displayProperty: String,
    // Physical state
    initialPos: Offset,
    var vel: Offset,
    var mass: Float,
    val radius: Float,
    val width: Float,
    val height: Float,
    val isCompound: Boolean = false,
    val isHyperNode: Boolean = false,
    val colorInfo: ColorInfo,
    var isFixed: Boolean = false,
    // Physics internals
    var oldForce: Offset = Offset.Zero,
    var swinging: Float = 0f,
    var traction: Float = 0f,
    // Data state
    override val properties: List<CodexProperty>
) : GraphEntity {
    var pos by mutableStateOf(initialPos)

    // Computed helper for backward compatibility / simple display
    val label: String get() = schemas.firstOrNull()?.name ?: "Unknown"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GraphNode

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun copy(
        id: Long = this.id,
        schemas: List<SchemaDefinition> = this.schemas,
        displayProperty: String = this.displayProperty,
        pos: Offset = this.pos,
        vel: Offset = this.vel,
        mass: Float = this.mass,
        radius: Float = this.radius,
        width: Float = this.width,
        height: Float = this.height,
        isCompound: Boolean = this.isCompound,
        isHyperNode: Boolean = this.isHyperNode,
        colorInfo: ColorInfo = this.colorInfo,
        isFixed: Boolean = this.isFixed,
        oldForce: Offset = this.oldForce,
        swinging: Float = this.swinging,
        traction: Float = this.traction,
        properties: List<CodexProperty> = this.properties
    ): GraphNode {
        return GraphNode(
            id = id,
            schemas = schemas,
            displayProperty = displayProperty,
            initialPos = pos,
            vel = vel,
            mass = mass,
            radius = radius,
            width = width,
            height = height,
            isCompound = isCompound,
            isHyperNode = isHyperNode,
            colorInfo = colorInfo,
            isFixed = isFixed,
            oldForce = oldForce,
            swinging = swinging,
            traction = traction,
            properties = properties
        )
    }
}

/**
 * Step 2.1: GraphEdge now implements GraphEntity.
 * It has its own ID and properties, making it a first-class citizen.
 */
data class GraphEdge(
    override val id: Long,
    val schemas: List<SchemaDefinition>, // Edges can also have types
    override val properties: List<CodexProperty>,
    val sourceId: Long,
    val targetId: Long,
    // UI Helpers
    val roleLabel: String? = null,
    val strength: Float,
    val colorInfo: ColorInfo
) : GraphEntity {
    val label: String get() = schemas.firstOrNull()?.name ?: "Unknown"
}

/**
 * Aggregates all graph elements for a snapshot of the current state.
 */
data class Graph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * Represents the pan and zoom state of the graph canvas.
 */
data class TransformState(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 1.0f
)