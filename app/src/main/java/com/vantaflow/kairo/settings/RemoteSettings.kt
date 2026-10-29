package com.vantaflow.kairo.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.remote.KeyLearner
import com.vantaflow.kairo.remote.KeyRemapService
import com.vantaflow.kairo.remote.keyLabel
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Réaffectation des touches d'applis de la télécommande (YouTube, Netflix, Prime Video, Disney+…). */
@Composable
fun RemoteSettings(vm: SettingsViewModel, installedApps: List<AppInfo>) {
    val context = LocalContext.current
    val bindings by vm.keyBindings.collectAsStateWithLifecycle()
    val learner = KeyLearner.shared
    val learning by learner.learning.collectAsStateWithLifecycle()
    val captured by learner.captured.collectAsStateWithLifecycle()
    var pendingKey by remember { mutableStateOf<Int?>(null) }
    var serviceOn by remember { mutableStateOf(KeyRemapService.isEnabled(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { serviceOn = KeyRemapService.isEnabled(context) }
    }
    LaunchedEffect(captured) { captured?.let { pendingKey = it } }
    DisposableEffect(Unit) { onDispose { learner.cancel() } }
    val labels = installedApps.associate { it.packageName to it.label }

    // Retour annule le choix d'appli ou l'apprentissage au lieu de quitter les réglages.
    BackHandler(enabled = pendingKey != null) { pendingKey = null }
    BackHandler(enabled = learning) { learner.cancel() }

    val pending = pendingKey
    if (pending != null) {
        AppPicker(
            title = "Touche ${keyLabel(pending)} : quelle appli ouvrir ?",
            apps = installedApps,
            onPick = { vm.setKeyBinding(pending, it.packageName); pendingKey = null },
            onCancel = { pendingKey = null },
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (serviceOn) "Le service de touches est actif." else "Le service de touches n'est pas activé.",
            color = OnBackground,
            fontSize = 20.sp,
        )
        if (!serviceOn) {
            Text("Activez-le une fois depuis un PC (la box masque souvent ce réglage) :", color = OnBackgroundMuted)
            Text(
                "Attention : cette commande remplace les autres services d'accessibilité actifs. " +
                    "Vérifiez-les d'abord avec : adb shell settings get secure enabled_accessibility_services",
                color = OnBackgroundMuted,
            )
            Text(
                "adb shell settings put secure enabled_accessibility_services " +
                    "com.vantaflow.kairo/.remote.KeyRemapService\n" +
                    "adb shell settings put secure accessibility_enabled 1",
                fontFamily = FontFamily.Monospace,
                color = OnBackground,
            )
        }
        if (learning) {
            val focus = remember { FocusRequester() }
            Text(
                "Appuyez maintenant sur la touche à réaffecter (YouTube, Netflix…).",
                color = OnBackground,
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        // Secours quand le service est inactif : la touche arrive jusqu'au launcher.
                        e.type == KeyEventType.KeyDown && learner.offer(e.key.nativeKeyCode)
                    },
            )
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            OutlinedButton(onClick = learner::cancel) { Text("Annuler") }
        } else {
            Button(onClick = learner::start) { Text("Ajouter une touche") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bindings.entries.sortedBy { it.key }, key = { it.key }) { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${keyLabel(entry.key)} → ${labels[entry.value] ?: entry.value}",
                        color = OnBackground,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    SmallButton("Changer", { pendingKey = entry.key })
                    SmallButton("Retirer", { vm.setKeyBinding(entry.key, null) })
                }
            }
        }
    }
}

/** Liste d'applis à choisir à la télécommande. */
@Composable
fun AppPicker(title: String, apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onCancel: () -> Unit) {
    val first = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = OnBackground, fontSize = 20.sp)
        OutlinedButton(onClick = onCancel) { Text("Annuler") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    selected = false,
                    onClick = { onPick(app) },
                    headlineContent = { Text(app.label) },
                    modifier = if (app == apps.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }
}
