package com.vantaflow.kairo.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vantaflow.kairo.R
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.LiveChannel
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accueil du mode sans échec, par le même chemin que l'activité ([SafeModeHomeScreen]) : l'état
 * fourni contient des recommandations, « À suivre » et des chaînes TV, qui ne doivent pas s'afficher.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SafeModeHomeTest {
    @get:Rule val rule = createComposeRule()
    private val focus = com.vantaflow.kairo.remote.HomeFocus()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val program = Program(1, "com.example.video", "Film à reprendre", null, null, null, null)
    private val live = LiveChannel("tv-1", "com.example.tv", "1", "Chaîne une", "content://tv/1")

    private val state = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre",
        favorites = (1..3).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
        watchNext = listOf(program),
        recoRows = listOf(Channel(7, "com.example.video", "Tendances", listOf(program))),
        liveChannels = listOf(live),
        tvInputs = listOf(live),
        loaded = true,
    )

    private fun setSafeHome(home: HomeUiState = state) {
        rule.setContent {
            TvHomeTheme {
                SafeModeHomeScreen(
                    home, focus, {}, {}, {}, {}, onRestart = {}, onAndroidSettings = {},
                    otherApps = listOf(AppInfo("com.example.other", "Other", hasBanner = false)),
                )
            }
        }
    }

    @Test
    fun firstFavoriteFocusedAndBannerShown() {
        setSafeHome()
        rule.onNodeWithTag("favorite-0").assertIsFocused()
        rule.onNodeWithTag("safe-mode-banner").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.safe_mode_title)).assertIsDisplayed()
    }

    @Test
    fun upReachesRestartButtonAndDownComesBack() {
        setSafeHome()
        rule.onNodeWithTag("favorite-0").assertIsFocused()
        rule.onNodeWithTag("favorite-0").performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("safe-mode-restart").assertIsFocused()
        rule.onNodeWithTag("safe-mode-restart").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("favorite-0").assertIsFocused()
    }

    @Test
    fun recommendationsAndLiveTvAreNotShown() {
        setSafeHome()
        rule.onNodeWithText(context.getString(R.string.home_watch_next)).assertDoesNotExist()
        rule.onNodeWithText("Tendances", substring = true).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_live_tv)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_inputs)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_all_apps)).assertExists()
    }

    @Test
    fun withoutFavoritesTheRestartButtonHasFocus() {
        setSafeHome(state.copy(favorites = emptyList()))
        rule.onNodeWithTag("safe-mode-restart").assertIsFocused()
    }
}
