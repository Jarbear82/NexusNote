// src/commonMain/kotlin/com/tau/nexus_note/codex/graph/GraphModels.kt

package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.ColorInfo

// --- Sealed Base for all visual Graph Nodes ---
sealed interface GraphNode {
    val id: Long
    val label: String // Schema Name (e.g., "Document", "Tag")

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

    // Transient UI State
    var isExpanded: Boolean

    // Physics internals (FA2)
    var oldForce: Offset
    var swinging: Float
    var traction: Float

    // Common Visualization
    val isCollapsed: Boolean
    val backgroundImagePath: String?

    // Abstract copy method for Physics Engine
    fun copyNode(): GraphNode
}

// --- 1. Heading Node (Structure) ---
data class HeadingGraphNode(
    val title: String,
    val level: Int = 1, // 1 for Doc, 2+ for Sections
    override val id: Long,
    override val label: String,
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

// --- 2. Short Text Node (Concepts/Ribs) ---
data class ShortTextGraphNode(
    val text: String,
    val iconName: String? = null,
    override val id: Long,
    override val label: String,
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

// --- 3. Long Text Node (Content) ---
data class LongTextGraphNode(
    val content: String,
    override val id: Long,
    override val label: String,
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

// --- 4. List Node (Structure) ---
data class ListGraphNode(
    val items: List<String>,
    val title: String? = null,
    override val id: Long,
    override val label: String,
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

// --- 5. Map Node (Metadata/Key-Value) ---
data class MapGraphNode(
    val data: Map<String, String>,
    val title: String? = null,
    override val id: Long,
    override val label: String,
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

// --- 6. Code Node (Syntax Highlighted) ---
data class CodeGraphNode(
    val code: String,
    val language: String,
    val filename: String = "",
    override val id: Long,
    override val label: String,
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

// --- 7. Table Node (Grids) ---
data class TableGraphNode(
    val headers: List<String>,
    val rows: List<Map<String, String>>,
    val caption: String? = null,
    override val id: Long,
    override val label: String,
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

// --- 8. Cluster Node (Grouping) ---
data class ClusterNode(
    override val id: Long,
    val containedNodes: Set<Long>,
    val childCount: Int,
    override val label: String = "Cluster",
    override var pos: Offset = Offset.Zero,
    override var vel: Offset = Offset.Zero,
    override val mass: Float = 30f,
    override val radius: Float = 50f,
    override val width: Float = 100f,
    override val height: Float = 100f,
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