package com.wefi.analyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val tabId: Int
) {
    data object ChannelGraph : Screen("graph", "Grafik", Icons.Rounded.AutoGraph, 0)
    data object ApList : Screen("ap_list", "Radar AP", Icons.Rounded.FormatListBulleted, 1)
    data object ChannelRating : Screen("rating", "Rating", Icons.Rounded.StarRate, 2)
    data object SpeedTest : Screen("speedtest", "Speedtest", Icons.Rounded.Speed, 3)

    companion object {
        val items: List<Screen>
            get() = listOf(ChannelGraph, ApList, ChannelRating, SpeedTest)

        fun findByRoute(route: String?): Screen = when (route) {
            ChannelGraph.route -> ChannelGraph
            ApList.route -> ApList
            ChannelRating.route -> ChannelRating
            SpeedTest.route -> SpeedTest
            else -> ChannelGraph
        }
    }
}
