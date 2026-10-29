package com.vantaflow.kairo.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun install(pkg: String, label: String, category: String, banner: Boolean = false) {
        val appInfo = ApplicationInfo().apply {
            packageName = pkg
            nonLocalizedLabel = label
            name = label
            if (banner) this.banner = android.R.drawable.ic_menu_gallery
        }
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = appInfo })
        val activity = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            applicationInfo = appInfo
            nonLocalizedLabel = label
            exported = true
        }
        val resolve = ResolveInfo().apply { activityInfo = activity; nonLocalizedLabel = label }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        pm.addResolveInfoForIntent(intent, resolve)
        pm.addOrUpdateActivity(activity)
        pm.addIntentFilterForActivity(
            ComponentName(pkg, "$pkg.Main"),
            android.content.IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
        )
    }

    @Test
    fun leanbackAndLauncherAreMergedWithoutDuplicates() {
        install("com.a", "Alpha", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.a", "Alpha", Intent.CATEGORY_LAUNCHER)
        val apps = AppRepository(context).installedApps()
        assertEquals(1, apps.count { it.packageName == "com.a" })
    }

    @Test
    fun ownPackageIsExcluded() {
        install(context.packageName, "TV Home", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.a", "Alpha", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = AppRepository(context).installedApps()
        assertFalse(apps.any { it.packageName == context.packageName })
        assertTrue(apps.any { it.packageName == "com.a" })
    }

    @Test
    fun sortedByLabelIgnoringCase() {
        install("com.n", "netflix", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.k", "Kodi", Intent.CATEGORY_LEANBACK_LAUNCHER)
        install("com.b", "beIN", Intent.CATEGORY_LAUNCHER)
        val labels = AppRepository(context).installedApps().map { it.label }
        assertEquals(listOf("beIN", "Kodi", "netflix"), labels)
    }

    @Test
    fun appWithoutBannerHasBannerFalse() {
        install("com.nobanner", "Sans", Intent.CATEGORY_LAUNCHER, banner = false)
        install("com.banner", "Avec", Intent.CATEGORY_LEANBACK_LAUNCHER, banner = true)
        val apps = AppRepository(context).installedApps().associateBy { it.packageName }
        assertFalse(apps.getValue("com.nobanner").hasBanner)
        assertTrue(apps.getValue("com.banner").hasBanner)
    }

    @Test
    fun tileFallsBackToIconWhenBannerMissing() {
        assertEquals("icon" to false, chooseTileImage(hasBanner = true, banner = { null }, icon = { "icon" }))
    }

    @Test
    fun tileUsesBannerWhenAvailable() {
        assertEquals("banner" to true, chooseTileImage(hasBanner = true, banner = { "banner" }, icon = { "icon" }))
    }

    @Test
    fun tileWithoutBannerFlagUsesIcon() {
        assertEquals("icon" to false, chooseTileImage(hasBanner = false, banner = { "banner" }, icon = { "icon" }))
    }
}
