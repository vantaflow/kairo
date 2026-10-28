package com.tvhome.launcher.remote

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLearnerTest {
    @Test
    fun capturesNextRemappableKeyOnce() {
        val learner = KeyLearner()
        learner.start()
        assertFalse(learner.offer(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(learner.offer(KeyEvent.KEYCODE_PROG_RED))
        assertEquals(KeyEvent.KEYCODE_PROG_RED, learner.captured.value)
        assertFalse(learner.learning.value)
        assertFalse(learner.offer(KeyEvent.KEYCODE_PROG_BLUE))
    }

    @Test
    fun ignoresKeysWhenNotLearning() {
        val learner = KeyLearner()
        assertFalse(learner.offer(KeyEvent.KEYCODE_PROG_RED))
        assertNull(learner.captured.value)
    }
}
