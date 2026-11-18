package com.vantaflow.kairo.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteMoveTest {
    private val all = listOf("a", "masquee", "b", "c")
    private val shown = listOf("a", "b", "c")

    private fun moved(pkg: String, delta: Int): List<String>? = favoriteMoveTarget(all, shown, pkg, delta)?.let { to ->
        (all - pkg).toMutableList().apply { add(to.coerceIn(0, size), pkg) }
    }

    @Test
    fun movingRightJumpsOverAHiddenFavorite() {
        assertEquals(listOf("masquee", "b", "a", "c"), moved("a", +1))
    }

    @Test
    fun movingLeftJumpsOverAHiddenFavorite() {
        assertEquals(listOf("b", "a", "masquee", "c"), moved("b", -1))
    }

    @Test
    fun nothingMovesAtTheEnds() {
        assertNull(favoriteMoveTarget(all, shown, "a", -1))
        assertNull(favoriteMoveTarget(all, shown, "c", +1))
    }
}
