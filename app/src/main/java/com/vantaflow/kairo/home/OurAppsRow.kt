package com.vantaflow.kairo.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vantaflow.kairo.apps.InstallState
import com.vantaflow.kairo.apps.OurApp
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Ce qu'affiche une appli à installer : action ou progression. */
fun installLabel(state: InstallState?, update: Boolean = false): String = when (state) {
    is InstallState.Downloading -> "Téléchargement… ${state.percent} %"
    InstallState.Confirm -> "Confirmez l'installation sur la télé"
    is InstallState.Failed -> "${state.message} · OK pour réessayer"
    else -> if (update) "OK pour mettre à jour" else "OK pour installer"
}

/** « Nos applications » : nos applis pas encore installées ou à mettre à jour (rangée masquée sinon). */
@Composable
fun OurAppsRow(
    apps: List<OurApp>,
    states: Map<String, InstallState>,
    updates: Set<String>,
    onInstall: (OurApp) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    val layout = LocalHomeLayout.current
    Column(modifier) {
        if (layout.showRowTitles) {
            Text("Nos applications", fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(apps, key = { it.id }) { app ->
                val state = states[app.id]
                Column(Modifier.width(layout.tileWidthDp.dp * 1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileFrame(
                        onClick = { onInstall(app) },
                        onLongClick = {},
                        width = layout.tileWidthDp.dp * 1.5f,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
                    ) {
                        Image(
                            painterResource(app.banner),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Installer ${app.name}" },
                        )
                        if (state is InstallState.Downloading) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.4f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(state.percent / 100f).background(AccentColor))
                            }
                        }
                        Text(
                            if (app.id in updates) "Mise à jour" else "Nouveau",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(AccentColor.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // Écart : la tuile zoomée au focus (et son contour) ne recouvre pas le texte.
                    Text(
                        app.tagline, fontSize = 13.sp, color = OnBackgroundMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(installLabel(state, update = app.id in updates), fontSize = 13.sp, color = if (state is InstallState.Failed) Color(0xFFFF8A80) else AccentColor)
                }
            }
        }
    }
}
