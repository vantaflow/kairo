package com.tvhome.launcher.reco

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgramIntentTest {
    private fun program(uri: String?) = Program(1, "com.netflix", "t", null, null, uri, null)
    private val fallback: (String) -> Intent? = { Intent("fallback").setPackage(it) }

    @Test
    fun parseUriPreferred() {
        val intent = programLaunchIntent(program("intent://watch/80100172#Intent;scheme=nflx;package=com.netflix;end"), fallback)
        assertEquals("nflx", intent?.scheme)
    }

    @Test
    fun invalidUriFallsBackToApp() {
        val intent = programLaunchIntent(program("intent:#Intent;%%%invalide"), fallback)
        assertEquals("fallback", intent?.action)
        assertEquals("com.netflix", intent?.`package`)
    }

    @Test
    fun missingUriFallsBackToApp() {
        assertEquals("fallback", programLaunchIntent(program(null), fallback)?.action)
    }

    @Test
    fun blankUriFallsBackToApp() {
        assertEquals("fallback", programLaunchIntent(program("  "), fallback)?.action)
    }

    @Test
    fun unstartableIntentFallsBackToApp() {
        val started = mutableListOf<String?>()
        val ok = launchProgram(program("intent://x#Intent;scheme=nflx;end"), fallback) { intent ->
            started += intent.action ?: intent.scheme
            intent.action == "fallback"
        }
        assertEquals(true, ok)
        assertEquals(listOf(Intent.ACTION_VIEW, "fallback"), started)
    }

    @Test
    fun parsedIntentIsHardened() {
        val uri = "intent:#Intent;component=com.tvhome.launcher/.HomeActivity;launchFlags=0x3;end"
        val intent = programLaunchIntent(program(uri), fallback)
        assertEquals("fallback", intent?.action)
        val other = programLaunchIntent(program("intent://w#Intent;scheme=nflx;launchFlags=0x3;end"), fallback)!!
        assertEquals(0, other.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertNull(other.selector)
    }

    @Test
    fun noFallbackGivesNull() {
        assertNull(programLaunchIntent(program(null)) { null })
    }
}
