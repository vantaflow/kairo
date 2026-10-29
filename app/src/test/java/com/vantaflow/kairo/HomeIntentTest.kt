package com.vantaflow.kairo

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeIntentTest {
    @Test
    fun homeIntentResolvesToHomeActivity() {
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val names = pm.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        assertTrue(names.contains(HomeActivity::class.java.name))
    }
}
