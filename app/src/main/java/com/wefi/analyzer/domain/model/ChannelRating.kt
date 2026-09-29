package com.wefi.analyzer.domain.model

/**
 * Model hasil evaluasi interferensi dan rating rekomendasi kanal (1-10 bintang).
 */
data class ChannelRating(
    val channel: Int,
    val frequencyMhz: Int,
    val stars: Int,
    val apCount: Int,
    val isRecommended: Boolean = false,
    val reason: String = ""
)
