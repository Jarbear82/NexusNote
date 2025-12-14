package com.tau.nexusnote.codex.graph.fcose

/**
 * Configuration object for the CoSE layout algorithm.
 * Replaces hardcoded constants to allow runtime tuning.
 */
data class LayoutConfig(
    // Physics / Forces
    var idealEdgeLength: Double = 50.0,
    var gravityConstant: Double = 0.25,
    var repulsionConstant: Double = 4500.0,

    // Cooling & Termination
    var coolingFactor: Double = 0.95,
    var initialTemp: Double = 1000.0,
    var minTemp: Double = 1.0,
    var maxIterations: Int = 500, // Increased max iterations to allow energy threshold to dominate
    var energyThreshold: Double = 0.5, // Stop if max displacement is below this (pixels)

    // Optimization
    var gridCellSize: Double? = null,
    var repulsionJitter: Double = 0.1,
    var repulsionJitterDistance: Double = 0.14,

    // Spectral
    var spectralScalingFactor: Double = 50.0,
    var eigenVectorIterations: Int = 20,

    // UI / Animation
    var animationStepDelay: Long = 800L // Delay between pipeline steps in Run All (ms)
)