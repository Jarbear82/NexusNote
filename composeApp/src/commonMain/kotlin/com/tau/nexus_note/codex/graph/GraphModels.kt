package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.ColorInfo

// --- Sealed Base for all visual Graph Nodes ---
sealed interface GraphNode {
    val id: Long
    val label: String
    val displayProperty: String

    // Physics properties
    var pos: Offset
    var vel: Offset
    val mass: Float
    val radius: Float

    // Dimensions for Rectangular Repulsion
    val width: Float
    val height: Float

    val colorInfo: ColorInfo

    // Physics State
    var isFixed: Boolean // Used for dragging or temp locks
    var isLocked: Boolean // User explicitly locked this node (Persistent in session)

    // NEW: Transient UI State for Expandable Nodes
    var isExpanded: Boolean

    // Physics internals (FA2)
    var oldForce: Offset
    var swinging: Float
    var traction: Float

    // Common Visualization
    val isCollapsed: Boolean // Collapsed in terms of Hierarchy (folding)
    val backgroundImagePath: String?

    // Abstract copy method for Physics Engine
    fun copyNode(): GraphNode
}

// --- NEW: Cluster Node ---
data class ClusterNode(
    override val id: Long,
    val containedNodes: Set<Long>, // IDs of nodes inside
    val childCount: Int,
    override val label: String = "Cluster",
    override val displayProperty: String = "Group",
    override var pos: Offset = Offset.Zero,
    override var vel: Offset = Offset.Zero,
    override val mass: Float = 30f,
    override val radius: Float = 50f,
    override val width: Float = 100f,
    override val height: Float = 100f,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false, // Not used heavily but required by interface
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

// --- Specialized Node Types for Rendering ---

data class GenericGraphNode(
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    val displayProperties: Map<String, String>,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

// 1. Structural Spine Nodes
data class DocumentGraphNode(
    val title: String,
    val metaJson: String,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

data class SectionGraphNode(
    val title: String,
    val level: Int,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

// 2. Content Nodes
data class BlockGraphNode(
    val content: String,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

data class CodeBlockGraphNode(
    val code: String,
    val language: String,
    val filename: String,
    val caption: String,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

data class TableGraphNode(
    val headers: List<String>,
    val data: List<Map<String, String>>,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

// --- NEW: List Graph Node ---
data class ListGraphNode(
    val items: List<String>,
    val listType: String, // "ordered", "unordered", "task"
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

// 3. Concept Rib Nodes
data class TagGraphNode(
    val name: String,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}

data class AttachmentGraphNode(
    val filename: String,
    val mimeType: String,
    val resolvedPath: String,
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val width: Float,
    override val height: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var isLocked: Boolean = false,
    override var isExpanded: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}