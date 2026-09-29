package com.wefi.analyzer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun screenItems_containsAllFourScreens() {
        val items = Screen.items
        assertEquals(4, items.size)
    }

    @Test
    fun screenItems_noneAreNull() {
        val items = Screen.items
        for (screen in items) {
            assertNotNull("Screen in Screen.items must never be null", screen)
            assertTrue("Screen route must not be blank", screen.route.isNotBlank())
            assertTrue("Screen title must not be blank", screen.title.isNotBlank())
        }
    }

    @Test
    fun screenItems_routesAndTabIdsAreUnique() {
        val items = Screen.items
        val routes = items.map { it.route }
        val tabIds = items.map { it.tabId }

        assertEquals("Routes must all be unique", routes.toSet().size, items.size)
        assertEquals("Tab IDs must all be unique", tabIds.toSet().size, items.size)
    }

    @Test
    fun findByRoute_returnsCorrectScreen() {
        assertEquals(Screen.ChannelGraph, Screen.findByRoute("graph"))
        assertEquals(Screen.ApList, Screen.findByRoute("ap_list"))
        assertEquals(Screen.ChannelRating, Screen.findByRoute("rating"))
        assertEquals(Screen.SpeedTest, Screen.findByRoute("speedtest"))
        assertEquals(Screen.ChannelGraph, Screen.findByRoute("unknown_route"))
        assertEquals(Screen.ChannelGraph, Screen.findByRoute(null))
    }
}
