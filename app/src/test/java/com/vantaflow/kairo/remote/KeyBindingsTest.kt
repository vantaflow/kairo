package com.vantaflow.kairo.remote

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBindingsTest {
    @Test
    fun navigationKeysNotRemappable() {
        listOf(
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_MENU,
        ).forEach { assertFalse("$it", isRemappable(it)) }
    }

    @Test
    fun appKeysRemappable() {
        assertTrue(isRemappable(KeyEvent.KEYCODE_PROG_RED))
        assertTrue(isRemappable(KeyEvent.KEYCODE_BUTTON_1))
        assertTrue(isRemappable(KeyEvent.KEYCODE_TV_INPUT))
        assertTrue(isRemappable(KeyEvent.KEYCODE_EXPLORER))
    }

    @Test
    fun bindingsRoundTrip() {
        val bindings = mapOf(183 to "com.google.android.youtube.tv", 184 to "com.netflix.ninja")
        assertEquals(bindings, decodeBindings(encodeBindings(bindings)))
    }

    @Test
    fun garbageDecodesEmpty() {
        assertEquals(emptyMap<Int, String>(), decodeBindings(null))
        assertEquals(emptyMap<Int, String>(), decodeBindings("n'importe quoi\n=\nabc=def"))
    }

    @Test
    fun bindingLookup() {
        val bindings = mapOf(183 to "com.netflix.ninja")
        assertEquals("com.netflix.ninja", bindingFor(183, bindings))
        assertNull(bindingFor(184, bindings))
        assertNull(bindingFor(KeyEvent.KEYCODE_HOME, mapOf(KeyEvent.KEYCODE_HOME to "x")))
    }
}
