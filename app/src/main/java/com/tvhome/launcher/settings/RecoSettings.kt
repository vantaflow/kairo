package com.tvhome.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.tvhome.launcher.reco.EPG_PERMISSIONS
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${index + 1}", color = OnBackgroundMuted, fontSize = 16.sp)
                    Text(
                        labels[pkg] ?: pkg,
                        color = if (hidden) OnBackgroundMuted else OnBackground,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SmallButton("↑", { vm.moveRecoApp(pkg, -1, apps) }, enabled = index > 0)
                    SmallButton("↓", { vm.moveRecoApp(pkg, +1, apps) }, enabled = index < apps.lastIndex)
                    SmallButton(if (hidden) "Afficher" else "Masquer", { vm.setRecoAppHidden(pkg, !hidden) })
                }
            }
            // Rangées de l'appli : un interrupteur par rangée.
            items(channels.filter { it.packageName == pkg && it.programs.isNotEmpty() }, key = { "ch-${it.id}" }) { channel ->
                Box(Modifier.padding(start = 24.dp)) {
                    SwitchRow(channel.name, checked = channel.id !in hiddenChannels, onChange = { vm.setChannelHidden(channel.id, !it) })
                }
            }
        }
    }
}
