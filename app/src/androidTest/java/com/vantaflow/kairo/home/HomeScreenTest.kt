package com.vantaflow.kairo.home

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val focus = com.vantaflow.kairo.remote.HomeFocus()

    private val state = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre",
        favorites = (1..5).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
    )

    @Test
    fun firstFavoriteHasInitialFocus() {
        rule.setContent {
            TvHomeTheme { HomeScreen(state, focus, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("favorite-0").assertIsFocused()
    }

    @Test
    fun focusRestoredAfterResume() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            TvHomeTheme { HomeScreen(state, focus, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("favorite-2").requestFocus()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
    }
}
