package com.tvhome.launcher.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

/** Package du launcher Google TV à désactiver (à confirmer sur la KM7 Plus, session du soir n°1). */
const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"

fun isDefaultHome(resolvedHomePackage: String?, ownPackage: String): Boolean =
    resolvedHomePackage != null && resolvedHomePackage == ownPackage

fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return isDefaultHome(resolved?.activityInfo?.packageName, context.packageName)
}

/** Assistant pour faire de Kairo l'écran d'accueil par défaut. */
@Composable
fun DefaultLauncherWizard() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { isDefault = isDefaultHome(context) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (isDefault) "Kairo est l'écran d'accueil par défaut." else "Kairo n'est pas encore l'écran d'accueil par défaut.",
            color = OnBackground,
            fontSize = 20.sp,
        )
        Button(onClick = { openHomeSettings(context) }) { Text("Choisir l'écran d'accueil") }
        Text(
            "Si la box ne propose pas le choix, désactivez le launcher Google depuis un PC :",
            color = OnBackgroundMuted,
        )
        SelectionContainer {
            Text(
                "adb shell pm disable-user --user 0 $GOOGLE_TV_LAUNCHER\n" +
                    "Pour revenir en arrière :\nadb shell pm enable $GOOGLE_TV_LAUNCHER",
                fontFamily = FontFamily.Monospace,
                color = OnBackground,
                modifier = Modifier,
            )
        }
    }
}

private fun openHomeSettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
