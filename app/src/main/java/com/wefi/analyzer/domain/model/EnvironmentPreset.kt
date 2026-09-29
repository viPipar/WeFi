package com.wefi.analyzer.domain.model

/**
 * Preset lingkungan untuk Path Loss Exponent (n) pada formula Log-Distance Path Loss.
 */
enum class EnvironmentPreset(val label: String, val exponent: Double) {
    OUTDOOR("🌳 Outdoor", 2.0),
    INDOOR("🏢 Indoor", 2.8),
    CONCRETE("🧱 Beton", 3.5)
}
