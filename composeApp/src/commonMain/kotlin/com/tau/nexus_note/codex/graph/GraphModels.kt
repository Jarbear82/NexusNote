package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Offset
import com.tau.nexus_note.datamodels.ColorInfo

// --- Sealed Base for all visual Graph Nodes ---
sealed interface GraphNode {
    val id: Long
    val label: String
    var pos: Offset
    var vel: Offset
    val mass: Float
    val radius: Float
    val width: Float
    val height: Float
    val colorInfo: ColorInfo
    var isFixed: Boolean
    var isLocked: Boolean
    var isExpanded: Boolean
    var oldForce: Offset
    var swinging: Float
    var traction: Float
    val isCollapsed: Boolean
    val backgroundImagePath: String?
    fun copyNode(): GraphNode
}

// 1. Title (Root)
data class TitleGraphNode(
    val title: String,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 2. Heading (Branch)
data class HeadingGraphNode(
    val text: String,
    val level: Int,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 3. Short Text
data class ShortTextGraphNode(
    val text: String,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 4. Long Text
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
) : GraphNode { override fun copyNode() = this.copy() }

// 5. Code Block
data class CodeBlockGraphNode(
    val code: String,
    val language: String,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 6. Map
data class MapGraphNode(
    val data: Map<String, String>,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 7. Set (Unique Values)
data class SetGraphNode(
    val items: List<String>,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 8. Unordered List
data class UnorderedListGraphNode(
    val items: List<String>,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 9. Ordered List
data class OrderedListGraphNode(
    val items: List<String>,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 10. Table Graph Node
data class TableGraphNode(
    val headers: List<String>,
    val rows: List<Map<String, String>>,
    val caption: String?,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 11. Image Graph Node (New)
data class ImageGraphNode(
    val uri: String, // Absolute path
    val altText: String,
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
) : GraphNode { override fun copyNode() = this.copy() }

// 12. Legacy List Graph Node (For robustness if referenced)
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
) : GraphNode { override fun copyNode() = this.copy() }

// 13. Tag (Leaf)
data class TagGraphNode(
    val name: String,
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
) : GraphNode { override fun copyNode() = this.copy() }

// Helper: Cluster
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
) : GraphNode { override fun copyNode() = this.copy() }