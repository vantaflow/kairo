package com.vantaflow.kairo.home

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import com.vantaflow.kairo.ui.TvIcon
import com.vantaflow.kairo.ui.TvIconImage
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.padding
import com.vantaflow.kairo.network.NetState
import com.vantaflow.kairo.network.NetworkIndicator
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.SurfaceColor
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Heure + date à gauche, boutons Applications et Réglages à droite. */
@Composable
fun HeaderBar(
    timeText: String,
    dateText: String,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    netState: NetState? = null,
    onOpenSearch: () -> Unit = {},
    onOpenTools: (() -> Unit)? = null,
    /** Radio en cours : pastille « ♪ » qui rouvre le lecteur (null = rien ne joue). */
    nowPlaying: String? = null,
    onOpenNowPlaying: () -> Unit = {},
    /** Onglets (Pour vous, Films…) : ils prennent la place de la grande heure, comme Google TV. */
    tabs: (@Composable () -> Unit)? = null,
) {
    val layout = LocalHomeLayout.current
    if (tabs != null) {
        // Une seule ligne : onglets à gauche ; à droite, boutons en icônes (sans indicateur réseau) puis heure et météo.
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            tabs()
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                nowPlaying?.let { name ->
                    Button(onClick = onOpenNowPlaying, colors = PillColors()) {
                        Text("♪ " + name.take(22), maxLines = 1)
                    }
                }
                IconButton(TvIcon.Search, "Rechercher", onOpenSearch)
                onOpenTools?.let { IconButton(TvIcon.Tools, "Outils", it) }
                IconButton(TvIcon.Apps, "Applications", onOpenApps)
                IconButton(TvIcon.Settings, "Réglages", onOpenSettings)
                if (layout.showClock) {
                    // Météo courte (« 18° ») lue dans la ligne de date : « Vendredi 30 octobre · 18° · Couvert ».
                    val temp = dateText.split(" · ").getOrNull(1)?.takeIf { layout.showDate && it.endsWith("°") }
                    Text(
                        listOfNotNull(temp, timeText).joinToString("   "),
                        fontSize = 26.sp, color = OnBackground, maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp).testTag("time"),
                    )
                }
            }
        }
        return
    }
    // Ligne 1 : heure et boutons. Ligne 2 : date et météo sur toute la largeur (jamais tronquées).
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (layout.showClock) {
                Text(
                    timeText,
                    fontSize = layout.clockSizeSp.sp,
                    fontWeight = FontWeight.Light,
                    color = OnBackground,
                    maxLines = 1,
                    modifier = Modifier.testTag("time"),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                netState?.let { NetworkIndicator(it, Modifier.padding(end = 8.dp)) }
                nowPlaying?.let { name ->
                    Button(onClick = onOpenNowPlaying, colors = PillColors()) {
                        Text("♪ " + name.take(22), maxLines = 1)
                    }
                }
                HeaderButton(TvIcon.Search, "Rechercher", onOpenSearch)
                onOpenTools?.let { HeaderButton(TvIcon.Tools, "Outils", it) }
                HeaderButton(TvIcon.Apps, "Applications", onOpenApps)
                HeaderButton(TvIcon.Settings, "Réglages", onOpenSettings)
            }
        }
        if (layout.showDate) {
            Text(
                dateText,
                fontSize = 22.sp,
                color = OnBackgroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("date"),
            )
        }
    }
}

/** Bouton rond de l'en-tête (icône seule), comme Google TV ; le libellé reste pour l'accessibilité. */
@Composable
private fun IconButton(icon: TvIcon, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = PillColors(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        TvIconImage(icon, LocalContentColor.current, size = 22.dp)
    }
}

/** Bouton pilule de l'en-tête : icône du design + libellé. */
@Composable
private fun HeaderButton(icon: TvIcon, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = PillColors()) {
        TvIconImage(icon, LocalContentColor.current, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun PillColors() = ButtonDefaults.colors(
    // Couleurs du thème : surface au repos, accent au focus.
    containerColor = SurfaceColor.copy(alpha = 0.82f),
    contentColor = OnBackground,
    focusedContainerColor = AccentColor,
    focusedContentColor = Background,
)
