package com.vantaflow.kairo.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vantaflow.kairo.R
import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.catalog.toProgram
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CinemaBannerTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun catalog(n: Int): BannerItem {
        val item = CatalogItem(MediaType.Movie, n.toLong(), "Film $n", "Résumé $n", "2024", 7.5, null, null)
        return BannerItem(item.toProgram(), item, null)
    }

    private fun watchNext(n: Int) = BannerItem(
        Program(id = n.toLong(), packageName = "com.example.app", title = "Épisode $n", subtitle = "S1 · É$n", posterUri = null, intentUri = null, progressPercent = 40),
        null,
        null,
    )

    @Composable
    private fun Banner(
        items: List<BannerItem>,
        modifier: Modifier = Modifier,
        inMyList: (CatalogItem) -> Boolean = { false },
        onToggleMyList: (CatalogItem) -> Unit = {},
        listId: Any? = null,
        onCurrent: (BannerItem?) -> Unit = {},
    ) {
        TvHomeTheme {
            CinemaBanner(
                items = items,
                inMyList = inMyList,
                onOpen = {},
                onToggleMyList = onToggleMyList,
                onCurrent = onCurrent,
                primaryFocus = remember { FocusRequester() },
                modifier = modifier,
                listId = listId,
            )
        }
    }

    private fun assertTitle(title: String) =
        rule.onNodeWithTag("banner-title", useUnmergedTree = true).assertTextEquals(title)

    /** Bouton [tag] qui porte le texte [text] (le texte est un nœud enfant du bouton). */
    private fun assertButtonText(tag: String, text: String) =
        rule.onNode(hasTestTag(tag) and (hasText(text) or hasAnyDescendant(hasText(text))), useUnmergedTree = true).assertExists()

    /** Laisse passer une image : les changements d'état sont appliqués (horloge manuelle). */
    private fun frame() = rule.mainClock.advanceTimeByFrame()

    @Test
    fun catalogItemShowsDetailsAndMyListButtons() {
        rule.setContent { Banner(listOf(catalog(1))) }
        assertTitle("Film 1")
        assertButtonText("banner-primary", context.getString(R.string.banner_open_details))
        rule.onNodeWithTag("banner-mylist").assertExists()
        assertButtonText("banner-mylist", "+ " + context.getString(R.string.home_my_list))
    }

    @Test
    fun watchNextItemShowsResumeAndNoMyList() {
        rule.setContent { Banner(listOf(watchNext(1))) }
        assertTitle("Épisode 1")
        assertButtonText("banner-primary", context.getString(R.string.banner_resume))
        rule.onNodeWithTag("banner-mylist").assertDoesNotExist()
    }

    @Test
    fun autoAdvancesAfterEightSeconds() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Banner(listOf(catalog(1), catalog(2), catalog(3))) }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        rule.mainClock.advanceTimeBy(BANNER_ADVANCE_MS - 1_000)
        assertTitle("Film 1")
        rule.mainClock.advanceTimeBy(1_500)
        assertTitle("Film 2")
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun manualMoveStopsAutoAdvance() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Banner(listOf(catalog(1), catalog(2), catalog(3))) }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        // « Vers le début » depuis le bouton principal : contenu précédent (le dernier, en boucle).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 3")
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 3")
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun singleItemHasNoDotsAndDoesNotMove() {
        rule.mainClock.autoAdvance = false
        val shown = mutableListOf<BannerItem?>()
        // Une case focalisable de chaque côté : une flèche non consommée y enverrait le focus.
        rule.setContent {
            Row {
                Box(Modifier.size(40.dp).testTag("before").focusable())
                Banner(listOf(catalog(1)), Modifier.weight(1f), onCurrent = { shown += it })
                Box(Modifier.size(40.dp).testTag("after").focusable())
            }
        }
        rule.onNodeWithTag("banner-dots").assertDoesNotExist()
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 1")
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        rule.onNodeWithTag("before").assertIsNotFocused()
        // Depuis Ma liste, « vers la fin » n'a pas de contenu suivant : le focus y reste
        // (un changement de contenu l'aurait renvoyé au bouton principal).
        rule.onNodeWithTag("banner-mylist").requestFocus()
        frame()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        frame()
        assertTitle("Film 1")
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        rule.onNodeWithTag("after").assertIsNotFocused()
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 1")
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        rule.onNodeWithTag("banner-dots").assertDoesNotExist()
        // Le contenu montré n'a jamais changé : seulement « Film 1 » (ou null avant le focus).
        rule.runOnIdle { assertEquals(listOf("Film 1"), shown.filterNotNull().map { it.program.title }.distinct()) }
    }

    @Test
    fun myListFocusPausesAutoAdvance() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Banner(listOf(catalog(1), catalog(2), catalog(3))) }
        rule.onNodeWithTag("banner-mylist").requestFocus()
        frame()
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 1")
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
    }

    @Test
    fun noAutoAdvanceWhenNotResumed() {
        rule.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.STARTED
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Banner(listOf(catalog(1), catalog(2), catalog(3)))
            }
        }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 1")
        // De retour au premier plan : l'avance reprend.
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        frame()
        rule.mainClock.advanceTimeBy(BANNER_ADVANCE_MS + 500)
        assertTitle("Film 2")
    }

    @Test
    fun noAutoAdvanceWhileWindowNotFocused() {
        rule.mainClock.autoAdvance = false
        // Fiche du catalogue ouverte par-dessus (autre fenêtre) : celle de l'accueil n'a plus le focus.
        var windowFocused by mutableStateOf(false)
        val window = object : WindowInfo {
            override val isWindowFocused: Boolean get() = windowFocused
        }
        rule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                Banner(listOf(catalog(1), catalog(2), catalog(3)))
            }
        }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 1")
        rule.onNodeWithTag("banner-primary").assertIsFocused()
        // Fiche fermée : l'avance reprend.
        rule.runOnIdle { windowFocused = true }
        frame()
        rule.mainClock.advanceTimeBy(BANNER_ADVANCE_MS + 500)
        assertTitle("Film 2")
    }

    @Test
    fun shrinkingListKeepsFocusAndShowsNextItem() {
        rule.mainClock.autoAdvance = false
        var items by mutableStateOf(listOf(catalog(1), catalog(2), catalog(3)))
        rule.setContent { Banner(items) }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        // « Vers le début » depuis le premier : le 3e contenu, puis « vers la fin » : Ma liste.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 3")
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        frame()
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        // Le 3e disparaît (Ma liste retiré par exemple) ; le 2e est devenu un contenu À suivre,
        // sans bouton Ma liste : l'index revient dans la liste et le focus passe au bouton principal.
        rule.runOnIdle { items = listOf(catalog(1), watchNext(2)) }
        repeat(3) { frame() }
        assertTitle("Épisode 2")
        rule.onNodeWithTag("banner-mylist").assertDoesNotExist()
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun rtlEndKeyGoesToNextItem() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Banner(listOf(catalog(1), catalog(2)))
            }
        }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        // De droite à gauche, « vers la fin » est la flèche gauche : bouton principal → Ma liste.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        assertTitle("Film 1")
        // Dernier bouton → contenu suivant, focus revenu sur le bouton principal.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 2")
        rule.onNodeWithTag("banner-primary").assertIsFocused()
    }

    @Test
    fun noAutoAdvanceWithoutFocus() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column {
                Banner(listOf(catalog(1), catalog(2), catalog(3)))
                Box(Modifier.size(40.dp).testTag("other").focusable())
            }
        }
        rule.onNodeWithTag("other").requestFocus()
        frame()
        rule.onNodeWithTag("other").assertIsFocused()
        rule.mainClock.advanceTimeBy(20_000)
        assertTitle("Film 1")
    }

    @Test
    fun addingToMyListKeepsShownItem() {
        rule.mainClock.autoAdvance = false
        // Comme l'accueil : Ma liste en tête de la bannière, le dernier ajouté en premier.
        var myList by mutableStateOf(emptyList<Int>())
        rule.setContent {
            val items = myList.map(::catalog) + (1..3).filterNot { it in myList }.map(::catalog)
            Banner(
                items,
                inMyList = { it.id.toInt() in myList },
                onToggleMyList = { c ->
                    val n = c.id.toInt()
                    myList = if (n in myList) myList - n else listOf(n) + myList
                },
            )
        }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        // « Vers le début » : le 3e contenu, puis « vers la fin » : son bouton Ma liste.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 3")
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        frame()
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        // + Ma liste : le film passe en tête de la liste, la bannière le montre toujours.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        repeat(3) { frame() }
        rule.runOnIdle { assertEquals(listOf(3), myList) }
        assertTitle("Film 3")
        rule.onNodeWithTag("banner-mylist").assertIsFocused()
        assertButtonText("banner-mylist", context.getString(R.string.banner_remove_my_list))
        // Un second appui vise le même film : il est retiré.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        repeat(3) { frame() }
        rule.runOnIdle { assertEquals(emptyList<Int>(), myList) }
        assertTitle("Film 3")
    }

    @Test
    fun otherListStartsOnFirstItem() {
        rule.mainClock.autoAdvance = false
        var tab by mutableStateOf(HomeTab.ForYou)
        var items by mutableStateOf(listOf(catalog(1), catalog(2), catalog(3)))
        rule.setContent { Banner(items, listId = tab) }
        rule.onNodeWithTag("banner-primary").requestFocus()
        frame()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        frame()
        assertTitle("Film 3")
        // Autre onglet : premier contenu, même si le contenu affiché fait aussi partie de la liste.
        rule.runOnIdle {
            tab = HomeTab.Movies
            items = listOf(catalog(4), catalog(3), catalog(5))
        }
        repeat(2) { frame() }
        assertTitle("Film 4")
    }
}
