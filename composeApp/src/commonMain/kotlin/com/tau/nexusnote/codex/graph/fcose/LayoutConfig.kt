package com.tau.nexusnote.codex.graph.fcose

/**
 * Configuration object for the One-Shot CoSE layout algorithm.
 * Distinct from PhysicsOptions (which is for Continuous Physics).
 */
data class LayoutConfig(
    // Physics / Forces
    var idealEdgeLength: Double = 50.0,
    var gravityConstant: Double = 0.25,
    var compoundGravityConstant: Double = 1.0, // New: Gravity specifically for compound nodes
    var repulsionConstant: Double = 4500.0,

    // Cooling & Termination
    var coolingFactor: Double = 0.8,
    var initialTemp: Double = 1000.0,
    var minTemp: Double = 1.0,
    var maxIterations: Int = 500,
    var energyThreshold: Double = 0.5,

    // Optimization
    var gridCellSize: Double? = null,
    var repulsionJitter: Double = 0.1,
    var repulsionJitterDistance: Double = 0.14,

    // Spectral
    var spectralScalingFactor: Double = 50.0,
    var eigenVectorIterations: Int = 20,

    // UI / Animation
    var animationStepDelay: Long = 800L
)