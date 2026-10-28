package com.tvhome.launcher.remote

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteKeysTest {
    @Test
    fun menuKeyOpensMenu() {
        assertEquals(RemoteAction.OpenMenu, remoteActionFor(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun infoKeyOpensMenu() {
        assertEquals(RemoteAction.OpenMenu, remoteActionFor(KeyEvent.KEYCODE_INFO))
    }

    @Test
    fun otherKeysIgnored() {
        assertNull(remoteActionFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(remoteActionFor(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun homeFocusResetGoesToFirstTile() {
        val focus = HomeFocus()
        focus.index = 4
        val before = focus.resetToken
        focus.reset()
        assertEquals(0, focus.index)
        assertEquals(before + 1, focus.resetToken)
    }

    @Test
    fun refocusKeepsTileAndRequestsFocusAgain() {
        val focus = HomeFocus()
        focus.index = 3
        val before = focus.resetToken
        focus.refocus()
        assertEquals(3, focus.index)
        assertEquals(before + 1, focus.resetToken)
    }
}
