package com.vantaflow.kairo.home

import com.vantaflow.kairo.ui.theme.BannerSource
import com.vantaflow.kairo.ui.theme.HomeStyle
import com.vantaflow.kairo.ui.theme.bannerSourcesOf
import com.vantaflow.kairo.ui.theme.homeStyleById
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStyleTest {
    @Test fun unknownStyleFallsBackToClassic() {
        assertEquals(HomeStyle.Classic, homeStyleById(null))
        assertEquals(HomeStyle.Classic, homeStyleById("grid"))
        assertEquals(HomeStyle.Cinema, homeStyleById("cinema"))
    }

    @Test fun bannerSourcesDefaultToAll() {
        assertEquals(BannerSource.entries.toSet(), bannerSourcesOf(null))
        assertEquals(setOf(BannerSource.MyList), bannerSourcesOf(setOf("my_list", "inconnu")))
        assertEquals(emptySet<BannerSource>(), bannerSourcesOf(emptySet()))
    }
}
