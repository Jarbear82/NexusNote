package com.tau.nexusnote.codex.graph.fcose

// Defining these here to ensure the code compiles if they were missing or implicit

interface Constraint

data class Point(val x: Double, val y: Double)

data class FixedNodeConstraint(
    val nodeId: String,
    val targetPos: Point
) : Constraint

enum class AlignmentDirection { VERTICAL, HORIZONTAL }

data class AlignmentConstraint(
    val nodes: List<FcNode>, // Or IDs
    val direction: AlignmentDirection
) : Constraint

data class RelativeConstraint(
    val leftNode: FcNode,  // Or "Top" node if Vertical
    val rightNode: FcNode, // Or "Bottom" node if Vertical
    val direction: AlignmentDirection, // Horizontal = Left/Right, Vertical = Top/Bottom
    val minGap: Double = 50.0
) : Constraint

/**
 * Common interface for layout phases.
 * Allows swapping different implementations (e.g., swapping Spectral for a different draft algorithm).
 * Updated to be 'suspend' to allow thread context switching for safe UI updates.
 */
interface LayoutPhase {
    suspend fun run(graph: FcGraph, config: LayoutConfig)
}

enum class ConstraintUiType {
    ALIGN_VERTICAL,
    ALIGN_HORIZONTAL,
    RELATIVE_LR, // Relative Left-to-Right
    RELATIVE_TB  // Relative Top-to-Bottom
}