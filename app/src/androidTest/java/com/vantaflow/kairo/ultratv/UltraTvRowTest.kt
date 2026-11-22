package com.vantaflow.kairo.ultratv

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.home.HomeScreen
import com.vantaflow.kairo.home.HomeUiState
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UltraTvRowTest {
    @get:Rule val rule = createComposeRule()

    private val now = 1_000_000L

    private fun channel(i: Int, locked: Boolean = false) = UtvChannel(
        providerId = i.toLong(), remoteId = "r$i", name = "Chaîne $i", number = "$i", logo = null,
        nowTitle = "En cours $i", nowStartMs = now - 600_000L, nowEndMs = now + 600_000L, nowImage = null,
        nextTitle = "Suivant $i", nextStartMs = now + 600_000L, nextEndMs = now + 1_200_000L,
        deepLink = "ultratv://channel/$i", locked = locked,
    )

    private val state = UtvState(pkg = ULTRA_TV_RELEASE, channels = listOf(channel(1), channel(2)))

    @Test
    fun shortcutsAndChannelsReachableWithRemote() {
        rule.setContent {
            TvHomeTheme { UltraTvRow(state, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        val tags = listOf("utv-section-guide", "utv-section-live", "utv-section-movies", "utv-section-series", "utv-channel-0", "utv-channel-1")
        rule.onNodeWithTag(tags.first()).requestFocus()
        rule.onNodeWithTag(tags.first()).assertIsFocused()
        tags.zipWithNext().forEach { (from, to) ->
            rule.onNodeWithTag(from).performKeyInput { pressKey(Key.DirectionRight) }
            rule.onNodeWithTag(to).assertIsFocused()
        }
    }

    @Test
    fun okOnChannelOpensThatChannel() {
        var opened: UtvChannel? = null
        var section: UtvSection? = null
        rule.setContent {
            TvHomeTheme { UltraTvRow(state, now, onOpenSection = { section = it }, onOpenChannel = { opened = it }, onFocused = {}) }
        }
        rule.onNodeWithTag("utv-channel-1").requestFocus()
        rule.onNodeWithTag("utv-channel-1").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(state.channels[1], opened)
        rule.onNodeWithTag("utv-section-movies").requestFocus()
        rule.onNodeWithTag("utv-section-movies").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(UtvSection.Movies, section)
    }

    @Test
    fun lockedChannelShowsNoProgramNorProgress() {
        val locked = UtvState(pkg = ULTRA_TV_RELEASE, channels = listOf(channel(1), channel(2, locked = true)))
        rule.setContent {
            TvHomeTheme { UltraTvRow(locked, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        // Chaîne libre : titre, barre et suivant affichés.
        rule.onNodeWithText("En cours 1").assertExists()
        rule.onNodeWithTag("utv-progress-0").assertExists()
        // Chaîne verrouillée : seulement logo, numéro et nom.
        rule.onNodeWithText("En cours 2").assertDoesNotExist()
        rule.onNodeWithText("Suivant 2", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("utv-progress-1").assertDoesNotExist()
    }

    @Test
    fun rowAppearingLaterDoesNotTakeFocus() {
        val ultraTv = mutableStateOf(UtvState())
        val home = HomeUiState(
            timeText = "20:45",
            dateText = "Mercredi 28 octobre",
            favorites = (1..3).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
        )
        rule.setContent {
            TvHomeTheme { HomeScreen(home, HomeFocus(), {}, {}, {}, {}, ultraTv = ultraTv.value, ultraTvNowMs = now) }
        }
        rule.onNodeWithTag("favorite-1").requestFocus()
        rule.onNodeWithTag("favorite-1").assertIsFocused()
        rule.onNodeWithTag("utv-row").assertDoesNotExist()
        // Ultra TV lue après coup : la rangée apparaît, la sélection reste sur le favori.
        ultraTv.value = state
        rule.waitForIdle()
        rule.onNodeWithTag("utv-row").assertExists()
        rule.onNodeWithTag("favorite-1").assertIsFocused()
    }
}
