package com.tvhome.launcher.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import com.tvhome.launcher.reco.EPG_PERMISSIONS
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.reco.recoApps
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

/** Recommandations : accès, « À suivre », ordre et visibilité par appli et par chaîne. */
@Composable
fun RecoSettings(vm: SettingsViewModel, installedApps: List<AppInfo>) {
    LaunchedEffect(Unit) { vm.loadRecoChannels() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.loadRecoChannels()
    }
    val channels by vm.recoChannels.collectAsStateWithLifecycle()
    val access by vm.recoAccess.collectAsStateWithLifecycle()
    val order by vm.recoAppOrder.collectAsStateWithLifecycle()
    val hiddenChannels by vm.hiddenChannels.collectAsStateWithLifecycle()
    val hiddenApps by vm.hiddenRecoApps.collectAsStateWithLifecycle()
    val showWatchNext by vm.showWatchNext.collectAsStateWithLifecycle()
    val labels = installedApps.associate { it.packageName to it.label }
    val apps = recoApps(channels, order)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SwitchRow("Afficher « À suivre »", showWatchNext, vm::setShowWatchNext, "Les contenus commencés dans vos applis.")
        }
        if (!access) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Le launcher n'a pas encore accès aux recommandations des applis.", color = OnBackground)
                    Button(onClick = { permission.launch(EPG_PERMISSIONS) }) { Text("Autoriser l'accès") }
                    Text("Si la box refuse, accordez-le une fois depuis un PC :", color = OnBackgroundMuted)
                    SelectionContainer {
                        Text(
                            "adb shell pm grant com.tvhome.launcher com.android.providers.tv.permission.READ_EPG_DATA\n" +
                                "adb shell pm grant com.tvhome.launcher android.permission.READ_TV_LISTINGS",
                            fontFamily = FontFamily.Monospace,
                            color = OnBackground,
                        )
                    }
                }
            }
        } else if (apps.isEmpty()) {
            item { Text("Aucune appli ne publie de recommandations pour l'instant.", color = OnBackgroundMuted) }
        }
        apps.forEachIndexed { index, pkg ->
            item(key = "app-$pkg") {
                val hidden = pkg in hiddenApps
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${index + 1}. ${labels[pkg] ?: pkg}",
                        color = OnBackground,
                        fontSize = 20.sp,
                        modifier = Modifier.width(260.dp),
                    )
                    OutlinedButton(onClick = { vm.moveRecoApp(pkg, -1, apps) }, enabled = index > 0) { Text("Monter") }
                    OutlinedButton(onClick = { vm.moveRecoApp(pkg, +1, apps) }, enabled = index < apps.lastIndex) {
                        Text("Descendre")
                    }
                    OutlinedButton(onClick = { vm.setRecoAppHidden(pkg, !hidden) }) {
                        Text(if (hidden) "Afficher" else "Masquer")
                    }
                }
            }
            items(channels.filter { it.packageName == pkg && it.programs.isNotEmpty() }, key = { "ch-${it.id}" }) { channel ->
                val chHidden = channel.id in hiddenChannels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 32.dp),
                ) {
                    Text(channel.name, color = OnBackgroundMuted, modifier = Modifier.width(228.dp))
                    OutlinedButton(onClick = { vm.setChannelHidden(channel.id, !chHidden) }) {
                        Text(if (chHidden) "Afficher la rangée" else "Masquer la rangée")
                    }
                }
            }
        }
    }
}
