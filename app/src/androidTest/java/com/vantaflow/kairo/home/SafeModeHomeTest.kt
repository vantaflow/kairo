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
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Accueil du mode sans échec : favoris, bandeau au-dessus, aucune recommandation. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SafeModeHomeTest {
    @get:Rule val rule = createComposeRule()
    private val focus = com.vantaflow.kairo.remote.HomeFocus()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val state = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre",
        favorites = (1..3).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
        loaded = true,
    )

    private fun setSafeHome() {
        rule.setContent {
            TvHomeTheme {
                HomeScreen(
                    state, focus, {}, {}, {}, {},
                    onOpenSearch = null,
                    otherApps = listOf(AppInfo("com.example.other", "Other", hasBanner = false)),
                    onSafeModeRestart = {},
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
    fun noRecommendationRows() {
        setSafeHome()
        rule.onNodeWithText(context.getString(R.string.home_watch_next)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_live_tv)).assertDoesNotExist()
    }

    @Test
    fun withoutFavoritesTheRestartButtonHasFocus() {
        rule.setContent {
            TvHomeTheme {
                HomeScreen(state.copy(favorites = emptyList()), focus, {}, {}, {}, {}, onOpenSearch = null, onSafeModeRestart = {})
            }
        }
        rule.onNodeWithTag("safe-mode-restart").assertIsFocused()
    }
}
