package com.vantaflow.kairo.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.vantaflow.kairo.reco.EPG_PERMISSIONS
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

private const val PKG = "com.vantaflow.kairo"
private const val DREAM = "$PKG/.dream.TvHomeDream"

/** Une étape de l'assistant : [done] null = état illisible sur cette box. */
private class SetupStep(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val done: Boolean?,
    @StringRes val action: Int,
    val onAction: () -> Unit,
    val adb: String? = null,
    val optional: Boolean = false,
    /** Section des réglages citée dans la description (« Réglages › … »), le cas échéant. */
    @StringRes val descriptionSection: Int? = null,
)

fun recoAccessGranted(context: Context): Boolean =
    EPG_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/** Vrai s'il manque une étape essentielle : l'assistant s'ouvre alors au premier lancement. */
fun setupNeeded(context: Context): Boolean = !recoAccessGranted(context) || !isDefaultHome(context)

/**
 * Assistant de configuration : ce qui est prêt, ce qui demande un clic, et sinon la commande
 * ADB exacte. Les états sont relus à chaque retour dans Kairo (après un passage dans les
 * paramètres Android).
 */
@Composable
fun SetupWizard(onDone: () -> Unit, onReplay: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    val steps = remember(refresh) {
        listOf(
            SetupStep(
                R.string.settings_setup_reco,
                R.string.settings_setup_reco_desc,
                recoAccessGranted(context),
                R.string.settings_allow,
                { permission.launch(EPG_PERMISSIONS) },
                adb = "adb shell pm grant $PKG android.permission.READ_TV_LISTINGS",
            ),
            SetupStep(
                R.string.settings_setup_home,
                R.string.settings_setup_home_desc,
                isDefaultHome(context),
                R.string.settings_setup_choose,
                { openHomeSettings(context) },
                adb = "adb shell pm disable-user --user 0 $GOOGLE_TV_LAUNCHER\n(pour revenir : adb shell pm enable $GOOGLE_TV_LAUNCHER)",
            ),
            SetupStep(
                R.string.settings_setup_updates,
                R.string.settings_setup_updates_desc,
                context.packageManager.canRequestPackageInstalls(),
                R.string.settings_allow,
                {
                    openSafely(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$PKG")))
                },
                adb = "adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow",
                optional = true,
                descriptionSection = R.string.section_update,
            ),
            SetupStep(
                R.string.settings_setup_keys,
                R.string.settings_setup_keys_desc,
                keyServiceRunning(context),
                R.string.settings_setup_accessibility,
                { openSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                optional = true,
            ),
            SetupStep(
                R.string.settings_setup_media,
                R.string.settings_setup_media_desc,
                com.vantaflow.kairo.media.NowPlaying.hasAccess(context),
                R.string.settings_allow,
                { openSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                adb = com.vantaflow.kairo.media.MEDIA_ACCESS_ADB,
                optional = true,
            ),
            SetupStep(
                R.string.settings_setup_dream,
                R.string.settings_setup_dream_desc,
                dreamEnabled(context),
                R.string.settings_setup_dream_action,
                { openSafely(context, Intent(Settings.ACTION_DREAM_SETTINGS)) },
                adb = "adb shell settings put secure screensaver_components $DREAM",
                optional = true,
            ),
        )
    }
    val ready = steps.count { it.done == true }

    // Marge haut et bas : le 1er et le dernier bouton grossissent au focus.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
        item {
            Text(
                if (ready == steps.size) stringResource(R.string.settings_setup_all_ready)
                else pluralStringResource(R.plurals.settings_setup_ready_count, ready, ready, steps.size),
                color = OnBackground,
                fontSize = 20.sp,
            )
        }
        steps.forEach { step -> item(key = step.title) { StepRow(step) } }
        // Toujours un élément focalisable, même quand tout est prêt (sinon le focus quitte le panneau).
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton(stringResource(R.string.settings_setup_done), onDone)
                SmallButton(stringResource(R.string.settings_setup_replay), onReplay)
            }
        }
        item {
            SettingHint(stringResource(R.string.settings_setup_adb_hint))
        }
    }
}

@Composable
private fun StepRow(step: SetupStep) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (mark, color) = when (step.done) {
                true -> "✓" to Color(0xFF6DD58C)
                false -> "•" to (if (step.optional) OnBackgroundMuted else Color(0xFFFFB74D))
                null -> "?" to OnBackgroundMuted
            }
            Text(mark, color = color, fontSize = 20.sp)
            Text(stringResource(step.title).let { if (step.optional) stringResource(R.string.settings_optional_suffix, it) else it }, color = OnBackground, fontSize = 18.sp)
        }
        Text(step.descriptionSection?.let { stringResource(step.description, settingsPath(it)) } ?: stringResource(step.description), color = OnBackgroundMuted, modifier = Modifier.padding(start = 24.dp))
        if (step.done != true) {
            Row(Modifier.padding(start = 24.dp)) { SmallButton(stringResource(step.action), step.onAction) }
            step.adb?.let { cmd ->
                Text(stringResource(R.string.settings_box_refuses), color = OnBackgroundMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp))
                // Pas de SelectionContainer : il prendrait le focus sans le montrer.
                Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnBackground, modifier = Modifier.padding(start = 24.dp))
            }
        }
    }
}

/** Service de touches réellement actif (le réglage seul peut le citer sans qu'il tourne). */
fun keyServiceRunning(context: Context): Boolean =
    context.getSystemService(AccessibilityManager::class.java)
        ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        ?.any { it.resolveInfo?.serviceInfo?.packageName == PKG } == true

/** Écran de veille Kairo choisi ; null si la box interdit de le lire. */
fun dreamEnabled(context: Context): Boolean? = secureSetting(context, "screensaver_components")?.contains(DREAM)

/** Réglage système lu sans planter : certaines box en interdisent la lecture (null). */
private fun secureSetting(context: Context, key: String): String? =
    try {
        Settings.Secure.getString(context.contentResolver, key).orEmpty()
    } catch (_: SecurityException) {
        null
    }

fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Écran absent sur cette box : la commande ADB affichée reste la solution.
    } catch (_: SecurityException) {
    }
}
