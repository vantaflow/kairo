package com.vantaflow.kairo.ultratv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
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
import com.vantaflow.kairo.reco.LiveChannel
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val state = UtvState(pkg = ULTRA_TV, channels = listOf(channel(1), channel(2)))

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
        // La 2e chaîne (6e élément) n'est pas composée tant que la rangée n'a pas défilé : on y va à la télécommande.
        rule.onNodeWithTag("utv-channel-0").requestFocus()
        rule.onNodeWithTag("utv-channel-0").performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("utv-channel-1").assertIsFocused()
        rule.onNodeWithTag("utv-channel-1").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(state.channels[1], opened)
        rule.onNodeWithTag("utv-section-movies").requestFocus()
        rule.onNodeWithTag("utv-section-movies").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(UtvSection.Movies, section)
    }

    @Test
    fun lockedChannelShowsNoProgramNorProgress() {
        // Chaîne verrouillée en premier : sa tuile est composée (5e élément, visible).
        val locked = UtvState(pkg = ULTRA_TV, channels = listOf(channel(1, locked = true)))
        rule.setContent {
            TvHomeTheme { UltraTvRow(locked, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        // La tuile existe bien, avec son numéro et son nom…
        rule.onNodeWithTag("utv-channel-0").assertExists()
        rule.onNode(hasContentDescription("1 Chaîne 1"), useUnmergedTree = true).assertExists()
        // … mais rien du programme : ni titre, ni suivant, ni barre, ni titre dans l'accessibilité.
        rule.onNodeWithText("En cours 1", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Suivant 1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("utv-progress-0", useUnmergedTree = true).assertDoesNotExist()
        rule.onAllNodes(hasContentDescription("En cours 1", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun freeChannelShowsProgramAndProgress() {
        // Contre-épreuve du test précédent : la même chaîne libre montre tout.
        rule.setContent {
            TvHomeTheme { UltraTvRow(UtvState(pkg = ULTRA_TV, channels = listOf(channel(1))), now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        rule.onNodeWithText("En cours 1", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Suivant 1", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("utv-progress-0", useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("En cours 1", substring = true), useUnmergedTree = true).assertExists()
    }

    @Test
    fun removedChannelPassesFocusToNeighbourThenToFirstShortcut() {
        val utv = mutableStateOf(state)
        rule.setContent {
            TvHomeTheme { UltraTvRow(utv.value, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        rule.onNodeWithTag("utv-channel-0").requestFocus()
        rule.onNodeWithTag("utv-channel-0").performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("utv-channel-1").assertIsFocused()
        // La 2e chaîne disparaît : le focus passe à la dernière restante.
        utv.value = state.copy(channels = listOf(channel(1)))
        rule.waitForIdle()
        rule.onNodeWithTag("utv-channel-0").assertIsFocused()
        // Plus aucune chaîne : le focus va au premier raccourci.
        utv.value = state.copy(channels = emptyList())
        rule.waitForIdle()
        rule.onNodeWithTag("utv-section-guide").assertIsFocused()
    }

    @Test
    fun removedFocusedChannelNeverSendsFocusAbove() {
        val utv = mutableStateOf(UtvState(pkg = ULTRA_TV, channels = listOf(channel(1), channel(2), channel(3))))
        var aboveFocused = 0
        rule.setContent {
            TvHomeTheme {
                Column {
                    // Premier élément de l'écran, comme l'en-tête de l'accueil.
                    Box(Modifier.size(40.dp).testTag("above").onFocusChanged { if (it.isFocused) aboveFocused++ }.focusable())
                    UltraTvRow(utv.value, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {})
                }
            }
        }
        rule.onNodeWithTag("utv-channel-0").requestFocus()
        rule.onNodeWithTag("utv-channel-0").assertIsFocused()
        rule.waitForIdle()
        aboveFocused = 0
        // La chaîne focalisée disparaît : le focus va directement à la voisine, jamais au-dessus.
        utv.value = utv.value.copy(channels = listOf(channel(2), channel(3)))
        rule.waitForIdle()
        assertEquals(0, aboveFocused)
        rule.onNodeWithTag("utv-channel-0").assertIsFocused()
        rule.onNodeWithTag("utv-channel-0", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasContentDescription("2 Chaîne 2", substring = true)))
        // Plus aucune chaîne : premier raccourci, toujours sans passer au-dessus.
        utv.value = utv.value.copy(channels = emptyList())
        rule.waitForIdle()
        assertEquals(0, aboveFocused)
        rule.onNodeWithTag("utv-section-guide").assertIsFocused()
    }

    @Test
    fun reorderedChannelsKeepFocusOnSameChannel() {
        val utv = mutableStateOf(state)
        rule.setContent {
            TvHomeTheme { UltraTvRow(utv.value, now, onOpenSection = {}, onOpenChannel = {}, onFocused = {}) }
        }
        rule.onNodeWithTag("utv-channel-0").requestFocus()
        // Une chaîne arrive devant : la chaîne 1 passe en 2e position et garde le focus.
        utv.value = state.copy(channels = listOf(channel(3), channel(1), channel(2)))
        rule.waitForIdle()
        rule.onNodeWithTag("utv-channel-1").assertIsFocused()
        rule.onNodeWithTag("utv-channel-1", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasContentDescription("1 Chaîne 1", substring = true)))
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

    private val homeWithRowsBelow = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre",
        favorites = (1..3).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) },
        liveChannels = (1..3).map { LiveChannel("tv$it", "com.example.tv", "$it", "Télé $it", "content://tv/$it") },
        tvInputs = listOf(LiveChannel("hdmi1", "com.example.tv", null, "HDMI 1", "content://tv/hdmi1")),
    )

    /** Tuile focalisable de l'appli [label] (rangées d'applis : la description est sur l'image). */
    private fun appTile(label: String) = isFocusable() and hasAnyDescendant(hasContentDescription(label))

    @Test
    fun rowAppearingAboveKeepsFocusedRowInPlace() {
        val ultraTv = mutableStateOf(UtvState())
        val others = (1..3).map { AppInfo("com.example.other$it", "Autre $it", hasBanner = false) }
        rule.setContent {
            TvHomeTheme {
                HomeScreen(homeWithRowsBelow, HomeFocus(), {}, {}, {}, {}, otherApps = others, ultraTv = ultraTv.value, ultraTvNowMs = now)
            }
        }
        // Focus sur la dernière rangée (« Toutes les applis », sous la place d'Ultra TV) : l'accueil est
        // défilé au maximum, le cas où la borne du défilement bouge pendant la mesure.
        rule.onNode(appTile("Autre 1"), useUnmergedTree = true).requestFocus()
        rule.waitForIdle()
        rule.onNode(appTile("Autre 1"), useUnmergedTree = true).assertIsFocused()
        // Position non rognée (une tuile sortie de l'écran ne vaut pas 0).
        fun y() = rule.onNode(appTile("Autre 1"), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        val before = y()

        // Apparition : même place dès la première image, puis à la fin.
        rule.mainClock.autoAdvance = false
        ultraTv.value = state
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("utv-row").assertExists()
        assertEquals(before, y(), 1f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(before, y(), 1f)
        rule.onNode(appTile("Autre 1"), useUnmergedTree = true).assertIsFocused()

        // Disparition : même place dès la première image, puis à la fin.
        rule.mainClock.autoAdvance = false
        ultraTv.value = UtvState()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("utv-row").assertDoesNotExist()
        assertEquals(before, y(), 1f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(before, y(), 1f)
        rule.onNode(appTile("Autre 1"), useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun focusOutsideHomeStaysWhenRowAppearsAndDisappears() {
        val ultraTv = mutableStateOf(UtvState())
        rule.setContent {
            TvHomeTheme {
                Box {
                    HomeScreen(homeWithRowsBelow, HomeFocus(), {}, {}, {}, {}, ultraTv = ultraTv.value, ultraTvNowMs = now)
                    // Élément hors de l'accueil, comme le panneau des réglages ouvert par-dessus.
                    Box(Modifier.size(40.dp).testTag("outside").focusable())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("outside").requestFocus()
        rule.onNodeWithTag("outside").assertIsFocused()
        ultraTv.value = state
        rule.waitForIdle()
        rule.onNodeWithTag("utv-row").assertExists()
        rule.onNodeWithTag("outside").assertIsFocused()
        ultraTv.value = UtvState()
        rule.waitForIdle()
        rule.onNodeWithTag("utv-row").assertDoesNotExist()
        rule.onNodeWithTag("outside").assertIsFocused()
    }

    @Test
    fun rowDisappearingWithFocusReturnsToFavorite() {
        val ultraTv = mutableStateOf(state)
        val focus = HomeFocus()
        rule.setContent {
            TvHomeTheme { HomeScreen(homeWithRowsBelow, focus, {}, {}, {}, {}, ultraTv = ultraTv.value, ultraTvNowMs = now) }
        }
        rule.onNodeWithTag("favorite-2").requestFocus()
        rule.onNodeWithTag("utv-section-live").requestFocus()
        rule.onNodeWithTag("utv-section-live").assertIsFocused()
        // Profil d'Ultra TV devenu enfant : la rangée disparaît, le focus revient au favori courant.
        ultraTv.value = UtvState()
        rule.waitForIdle()
        rule.onNodeWithTag("utv-row").assertDoesNotExist()
        rule.onNodeWithTag("favorite-2").assertIsFocused()
    }

    @Test
    fun retainedRowIsInertAndKeepsItsProgress() {
        val ultraTv = mutableStateOf(state)
        val nowMs = mutableStateOf(now)
        var opened: UtvChannel? = null
        val focus = HomeFocus()
        rule.setContent {
            TvHomeTheme {
                HomeScreen(
                    homeWithRowsBelow, focus, {}, {}, {}, {},
                    ultraTv = ultraTv.value, ultraTvNowMs = nowMs.value, onOpenUtvChannel = { opened = it },
                )
            }
        }
        rule.onNodeWithTag("favorite-1").requestFocus()
        rule.onNodeWithTag("utv-channel-0").requestFocus()
        rule.onNodeWithTag("utv-channel-0").assertIsFocused()
        rule.waitForIdle()
        // Rangée retirée (profil enfant) : l'accueil reçoit l'état vide et l'heure 0, comme depuis l'activité.
        rule.mainClock.autoAdvance = false
        ultraTv.value = UtvState()
        nowMs.value = 0L
        rule.mainClock.advanceTimeByFrame()
        // Rangée encore affichée le temps de rendre le focus : barre gardée, OK sans effet.
        rule.onNodeWithTag("utv-row").assertExists()
        rule.onNodeWithTag("utv-progress-0", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("utv-channel-0").assertIsFocused()
        rule.onNodeWithTag("utv-channel-0").performKeyInput { pressKey(Key.DirectionCenter) }
        assertNull(opened)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("utv-row").assertDoesNotExist()
        rule.onNodeWithTag("favorite-1").assertIsFocused()
        assertNull(opened)
    }
}
