package com.vantaflow.kairo.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vantaflow.kairo.ui.theme.HomeStyle
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeStyleSettingsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pickingCinemaCallsOnPick() {
        val picked = mutableListOf<HomeStyle>()
        rule.setContent {
            TvHomeTheme { HomeStylePicker(current = HomeStyle.Classic, onPick = { picked += it }) }
        }
        // À la télécommande : focus sur la carte Cinéma, puis OK.
        rule.onNodeWithTag("style-cinema").requestFocus()
        rule.onNodeWithTag("style-cinema").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf(HomeStyle.Cinema), picked) }
    }

    @Test
    fun currentStyleIsMarkedSelected() {
        rule.setContent {
            TvHomeTheme { HomeStylePicker(current = HomeStyle.Classic, onPick = {}) }
        }
        rule.onNodeWithTag("style-classic").assertIsSelected()
        rule.onNodeWithTag("style-cinema").assertIsNotSelected()
    }

    @Test
    fun focusStaysInPickerWhenStyleChanges() {
        var current by mutableStateOf(HomeStyle.Classic)
        rule.setContent {
            TvHomeTheme { HomeStylePicker(current = current, onPick = { current = it }) }
        }
        rule.onNodeWithTag("style-cinema").requestFocus()
        rule.onNodeWithTag("style-cinema").assertIsFocused()
        // Le style change (l'accueil derrière le panneau change avec lui) : le focus ne bouge pas.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag("style-cinema").assertIsSelected()
        rule.onNodeWithTag("style-cinema").assertIsFocused()
        // Changement venu d'ailleurs (autre profil, sauvegarde restaurée) : idem.
        rule.runOnIdle { current = HomeStyle.Classic }
        rule.waitForIdle()
        rule.onNodeWithTag("style-classic").assertIsSelected()
        rule.onNodeWithTag("style-cinema").assertIsFocused()
    }
}
