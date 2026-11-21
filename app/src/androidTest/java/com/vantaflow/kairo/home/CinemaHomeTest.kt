package com.vantaflow.kairo.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.catalog.toProgram
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.ui.theme.HomeStyle
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CinemaHomeTest {
    @get:Rule val rule = createComposeRule()
    private val focus = HomeFocus()

    private val state = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre",
        favorites = (1..5).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
    )

    /** Contenu du catalogue sans image de fond (pas de chargement réseau pendant le test). */
    private fun catalog(n: Int): BannerItem {
        val item = CatalogItem(MediaType.Movie, n.toLong(), "Film $n", "Résumé $n", "2024", 7.5, null, null)
        return BannerItem(item.toProgram(), item, null)
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun focusStartsOnBannerWhenItHasItems() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = listOf(catalog(1), catalog(2))), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner").assertExists()
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun focusStartsOnFirstFavoriteWithoutItems() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = emptyList()), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner").assertDoesNotExist()
        rule.onNodeWithTag("favorite-0").assertIsFocused()
    }

    @Test
    fun downFromBannerReachesFavoritesAndUpReturns() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = listOf(catalog(1))), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        // Bas depuis la bannière : le favori retenu (le 1er au départ).
        press(Key.DirectionDown)
        rule.onNodeWithTag("favorite-0").assertIsFocused()
        // Haut depuis un favori : retour sur le bouton principal de la bannière.
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        // Bas de nouveau : le favori quitté, pas le plus proche à l'écran.
        press(Key.DirectionDown)
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        // Depuis le bouton Ma liste aussi.
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithTag("favorite-2").assertIsFocused()
    }

    @Test
    fun resetReturnsToBanner() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = listOf(catalog(1))), HomeActions(), focus) }
        }
        rule.onNodeWithTag("favorite-2").requestFocus()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        rule.runOnIdle { focus.reset() }
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun refocusReturnsWhereFocusWas() {
        lateinit var focusManager: FocusManager
        rule.setContent {
            focusManager = LocalFocusManager.current
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = listOf(catalog(1))), HomeActions(), focus) }
        }
        // Sur un favori, focus parti ailleurs (panneau des réglages) : refocus rend le même favori, pas la bannière.
        rule.onNodeWithTag("favorite-2").requestFocus()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        rule.runOnIdle { focusManager.clearFocus() }
        rule.onNode(isFocused()).assertDoesNotExist()
        rule.runOnIdle { focus.refocus() }
        rule.onNodeWithTag("favorite-2").assertIsFocused()
        rule.onNodeWithTag("favorite-2").assertIsDisplayed()
        // Sur la bannière : la bannière, même si le favori retenu n'est pas le 1er.
        press(Key.DirectionUp)
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        rule.runOnIdle { focusManager.clearFocus() }
        rule.onNode(isFocused()).assertDoesNotExist()
        rule.runOnIdle { focus.refocus() }
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun downFromHeaderReachesBanner() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = listOf(catalog(1))), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        // Haut depuis la bannière : l'en-tête.
        press(Key.DirectionUp)
        rule.onNode(isFocused()).assertExists()
        rule.onNodeWithTag("banner-primary").assertIsNotFocused()
        rule.onNodeWithTag("favorite-0").assertIsNotFocused()
        // Bas depuis l'en-tête : la bannière, pas le favori retenu.
        press(Key.DirectionDown)
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun lateBannerDoesNotStealFocus() {
        var banner by mutableStateOf(emptyList<BannerItem>())
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = banner), HomeActions(), focus) }
        }
        rule.onNodeWithTag("favorite-0").assertIsFocused()
        // L'utilisateur a déjà bougé : droite vers le 2e favori.
        press(Key.DirectionRight)
        rule.onNodeWithTag("favorite-1").assertIsFocused()
        // Catalogue chargé après coup : la bannière apparaît sans prendre le focus, et le favori reste visible.
        rule.runOnIdle { banner = listOf(catalog(1), catalog(2)) }
        rule.waitForIdle()
        rule.onNodeWithTag("banner").assertExists()
        rule.onNodeWithTag("favorite-1").assertIsFocused()
        rule.onNodeWithTag("favorite-1").assertIsDisplayed()
    }

    @Test
    fun lateBannerTakesFocusWhenUserHasNotMoved() {
        var banner by mutableStateOf(emptyList<BannerItem>())
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = banner), HomeActions(), focus) }
        }
        rule.onNodeWithTag("favorite-0").assertIsFocused()
        // Démarrage à froid : aucune touche appuyée, le catalogue arrive → la bannière prend le focus.
        rule.runOnIdle { banner = listOf(catalog(1)) }
        rule.waitForIdle()
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        rule.onNodeWithTag("banner-primary").assertIsDisplayed()
    }

    @Test
    fun emptiedBannerWithoutFavoritesOrTabsKeepsFocusSomewhere() {
        var banner by mutableStateOf(listOf(catalog(1)))
        val noFavorites = state.copy(favorites = emptyList(), loaded = true)
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = noFavorites, banner = banner, tab = null), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        rule.runOnIdle { banner = emptyList() }
        rule.waitForIdle()
        rule.onNodeWithTag("banner").assertDoesNotExist()
        // Ni favori ni onglet : le focus va sur l'en-tête, il ne reste jamais nulle part.
        rule.onNode(isFocused()).assertExists()
    }

    @Test
    fun homeBehindSettingsNeverRequestsFocus() {
        var style by mutableStateOf(HomeStyle.Classic)
        rule.setContent {
            TvHomeTheme {
                Column {
                    // Tient lieu du panneau des réglages, qui a le focus.
                    Box(Modifier.size(40.dp).testTag("panel").focusable())
                    HomeHost(style, HomeData(state = state, banner = listOf(catalog(1)), behindPanel = true), HomeActions(), focus)
                }
            }
        }
        rule.onNodeWithTag("panel").requestFocus()
        rule.onNodeWithTag("panel").assertIsFocused()
        // Style changé dans le panneau : l'accueil est recréé derrière, sans prendre le focus.
        rule.runOnIdle { style = HomeStyle.Cinema }
        rule.waitForIdle()
        rule.onNodeWithTag("banner").assertExists()
        rule.onNodeWithTag("panel").assertIsFocused()
        rule.onNodeWithTag("banner-primary").assertIsNotFocused()
    }

    @Test
    fun emptiedBannerGivesFocusToFavorite() {
        var banner by mutableStateOf(listOf(catalog(1)))
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Cinema, HomeData(state = state, banner = banner), HomeActions(), focus) }
        }
        rule.onNodeWithTag("favorite-2").requestFocus()
        press(Key.DirectionUp)
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        // Dernier contenu retiré (Ma liste) pendant que la bannière a le focus : il revient au favori retenu.
        rule.runOnIdle { banner = emptyList() }
        rule.waitForIdle()
        rule.onNodeWithTag("banner").assertDoesNotExist()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
    }

    @Test
    fun classicStyleNeverShowsBanner() {
        rule.setContent {
            TvHomeTheme { HomeHost(HomeStyle.Classic, HomeData(state = state, banner = listOf(catalog(1))), HomeActions(), focus) }
        }
        rule.onNodeWithTag("banner").assertDoesNotExist()
        rule.onNodeWithTag("favorite-0").assertIsFocused()
    }
}
