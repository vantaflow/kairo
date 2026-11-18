package com.vantaflow.kairo.settings

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
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Favoris : une ligne compacte par appli, avec déplacement et retrait. */
@Composable
fun FavoritesSettings(vm: SettingsViewModel, installedApps: List<AppInfo>) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val labels = installedApps.associate { it.packageName to it.label }
    if (favorites.isEmpty()) {
        SettingHint(stringResource(R.string.settings_favorites_empty))
        return
    }
    // Droite à gauche : la rangée Favoris de l'accueil est en miroir (1er favori à droite), donc
    // « précédent » se déplace vers la droite. Les flèches suivent le sens visuel ; l'ordre des
    // boutons (précédent puis suivant) est lui-même inversé par la Row.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val prevArrow = if (rtl) "→" else "←"
    val nextArrow = if (rtl) "←" else "→"
    // Marge haut et bas : le 1er et le dernier bouton grossissent au focus.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
        item { SettingHint(stringResource(R.string.settings_favorites_order_hint)) }
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
                SmallButton(prevArrow, { vm.moveFavoriteBy(pkg, -1) }, enabled = index > 0)
                SmallButton(nextArrow, { vm.moveFavoriteBy(pkg, +1) }, enabled = index < favorites.lastIndex)
                SmallButton(stringResource(R.string.settings_remove), { vm.removeFavorite(pkg) })
            }
        }
    }
}
