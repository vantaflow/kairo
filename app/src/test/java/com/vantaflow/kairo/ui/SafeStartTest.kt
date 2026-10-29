package com.vantaflow.kairo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafeStartTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()

    private fun throwing(e: RuntimeException) = object : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) = throw e
    }

    @Test
    fun missingActivityDoesNotCrash() {
        assertFalse(throwing(ActivityNotFoundException()).startSafely(Intent("x")))
    }

    @Test
    fun securityExceptionDoesNotCrash() {
        assertFalse(throwing(SecurityException()).startSafely(Intent("x")))
    }
}
