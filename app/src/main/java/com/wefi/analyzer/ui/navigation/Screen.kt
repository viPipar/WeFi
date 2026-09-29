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
    object ChannelGraph : Screen("graph", "Grafik", Icons.Rounded.AutoGraph, 0)
    object ApList : Screen("ap_list", "Radar AP", Icons.Rounded.FormatListBulleted, 1)
    object ChannelRating : Screen("rating", "Rating", Icons.Rounded.StarRate, 2)
    object SpeedTest : Screen("speedtest", "Speedtest", Icons.Rounded.Speed, 3)

    companion object {
        val items = listOf(ChannelGraph, ApList, ChannelRating, SpeedTest)
    }
}
