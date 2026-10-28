package com.tvhome.launcher.home

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
import com.tvhome.launcher.ui.TvIcon
import com.tvhome.launcher.ui.TvIconImage
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.padding
import com.tvhome.launcher.network.NetState
import com.tvhome.launcher.network.NetworkIndicator
import com.tvhome.launcher.ui.theme.LocalHomeLayout
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted

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
) {
    val layout = LocalHomeLayout.current
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
                HeaderButton(TvIcon.Search, "Rechercher", onOpenSearch)
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
    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
    contentColor = OnBackground,
)
