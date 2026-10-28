package com.tvhome.launcher.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.tvhome.launcher.home.AppTile
import com.tvhome.launcher.ui.theme.Background
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

/** Grille de toutes les applis installées (5 colonnes). */
@Composable
fun AppsScreen(
    apps: List<AppInfo>,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    /** Applis masquées : estompées et marquées quand elles sont affichées. */
    hidden: Set<String> = emptySet(),
    onLaunch: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    /** null = bouton Play Store masqué (profil enfant). */
    onOpenPlayStore: (() -> Unit)? = {},
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Background).padding(start = 72.dp, end = 72.dp, top = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Applications", fontSize = 32.sp, color = OnBackground)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Afficher les applis masquées (${hidden.size})", color = OnBackgroundMuted)
                if (hidden.isEmpty()) {
                    Text("Aucune : appui long sur une appli › Masquer", fontSize = 13.sp, color = OnBackgroundMuted.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = showHidden, onCheckedChange = onShowHiddenChange)
            Spacer(Modifier.width(24.dp))
            onOpenPlayStore?.let { Button(onClick = it) { Text("Obtenir des applis (Play Store)") } }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                val isHidden = app.packageName in hidden
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(
                        app = app,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongClick(app) },
                        width = 200.dp,
                        dimmed = isHidden,
                        modifier = if (app == apps.first()) Modifier.focusRequester(first) else Modifier,
                    )
                    Text(
                        if (isHidden) "Masquée · ${app.label}" else app.label,
                        color = if (isHidden) OnBackgroundMuted.copy(alpha = 0.6f) else OnBackgroundMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
