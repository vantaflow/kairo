package com.tvhome.launcher.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultLauncherCheckTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun isDefaultHomeTrueWhenOurActivityPreferred() {
        assertTrue(isDefaultHome(context))
    }

    @Test
    fun isDefaultHomeFalseOtherwise() {
        assertFalse(isDefaultHome(resolvedHomePackage = "com.google.android.apps.tv.launcherx", ownPackage = context.packageName))
        assertFalse(isDefaultHome(resolvedHomePackage = "android", ownPackage = context.packageName))
        assertFalse(isDefaultHome(resolvedHomePackage = null, ownPackage = context.packageName))
    }
}
