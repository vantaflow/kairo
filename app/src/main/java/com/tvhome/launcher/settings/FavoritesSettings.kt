package com.tvhome.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

/** Favoris : une ligne compacte par appli, avec déplacement et retrait. */
@Composable
fun FavoritesSettings(vm: SettingsViewModel, installedApps: List<AppInfo>) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val labels = installedApps.associate { it.packageName to it.label }
    if (favorites.isEmpty()) {
        SettingHint("Aucun favori. Ajoutez-en depuis Applications (appui long ou touche Menu sur une appli).")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SettingHint("L'ordre ici est celui de la rangée Favoris de l'accueil.") }
        itemsIndexed(favorites, key = { _, pkg -> pkg }) { index, pkg ->
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
                    color = OnBackground,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SmallButton("←", { vm.moveFavoriteBy(pkg, -1) }, enabled = index > 0)
                SmallButton("→", { vm.moveFavoriteBy(pkg, +1) }, enabled = index < favorites.lastIndex)
                SmallButton("Retirer", { vm.removeFavorite(pkg) })
            }
        }
    }
}
