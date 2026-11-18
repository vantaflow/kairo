package com.vantaflow.kairo.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownTabsTest {
    @Test
    fun forYouAlwaysStays() {
        assertEquals(listOf(HomeTab.ForYou), shownTabs(movies = false, shows = false, apps = false))
    }

    @Test
    fun uncheckedTabDisappearsAndOrderIsKept() {
        assertEquals(listOf(HomeTab.ForYou, HomeTab.Movies, HomeTab.Apps), shownTabs(movies = true, shows = false, apps = true))
    }
}
