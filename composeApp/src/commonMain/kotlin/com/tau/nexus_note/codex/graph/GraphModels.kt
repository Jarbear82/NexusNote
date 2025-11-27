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
    val colorInfo: ColorInfo
    var isFixed: Boolean

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

// --- Specialized Node Types for Rendering ---

data class GenericGraphNode(
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    val metaJson: String, // frontmatter
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    val caption: String,
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
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
    val resolvedPath: String, // Absolute path for rendering
    // Standard overrides
    override val id: Long,
    override val label: String,
    override val displayProperty: String,
    override var pos: Offset,
    override var vel: Offset,
    override val mass: Float,
    override val radius: Float,
    override val colorInfo: ColorInfo,
    override var isFixed: Boolean = false,
    override var oldForce: Offset = Offset.Zero,
    override var swinging: Float = 0f,
    override var traction: Float = 0f,
    override val isCollapsed: Boolean = false,
    override val backgroundImagePath: String? = null
) : GraphNode {
    override fun copyNode() = this.copy()
}