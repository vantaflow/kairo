package com.vantaflow.kairo.tools

import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vantaflow.kairo.home.TileFrame
import com.vantaflow.kairo.ui.TvIcon
import com.vantaflow.kairo.ui.TvIconImage
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.OnBackground

/** Outils de Kairo, chacun avec sa couleur. */
enum class Tool(val label: String, val icon: TvIcon, val colors: List<Color>) {
    Radio("Radio", TvIcon.Radio, listOf(Color(0xFF6A2C91), Color(0xFFD4477A))),
    Weather("Météo", TvIcon.Weather, listOf(Color(0xFF1565A8), Color(0xFF4FA3D8))),
    Sleep("Minuteur", TvIcon.Timer, listOf(Color(0xFF1F2A5C), Color(0xFF5B4FB0))),
    Cleaner("Nettoyeur", TvIcon.Clean, listOf(Color(0xFF0F6B5A), Color(0xFF34B38A))),
}

/** Rangée « Outils » : [subtitles] = état de chaque outil (radio en cours, temps restant…). */
@Composable
fun ToolsRow(
    tools: List<Tool>,
    subtitles: Map<Tool, String>,
    onOpen: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    val layout = LocalHomeLayout.current
    Column(modifier) {
        if (layout.showRowTitles) {
            Text("Outils", fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(tools, key = { it.name }) { tool ->
                TileFrame(
                    onClick = { onOpen(tool) },
                    onLongClick = { onOpen(tool) },
                    width = layout.tileWidthDp.dp,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
                ) {
                    Column(
                        Modifier.fillMaxSize().background(Brush.linearGradient(tool.colors)).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TvIconImage(tool.icon, Color.White, size = 30.dp)
                        Column {
                            Text(tool.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                            subtitles[tool]?.let {
                                Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Menu « Outils » ouvert depuis l'en-tête : panneau en haut à droite, Retour le ferme. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ToolsMenu(tools: List<Tool>, subtitles: Map<Tool, String>, onOpen: (Tool) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 72.dp).width(420.dp)
                .background(Color(0xFF202329), RoundedCornerShape(22.dp)).padding(14.dp)
                .focusGroup().focusProperties { exit = { FocusRequester.Cancel } }
                // Retour ferme le menu (le focus est dans le menu, la touche y passe d'abord).
                .onPreviewKeyEvent { e ->
                    if (e.key.nativeKeyCode != android.view.KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyUp) onDismiss()
                    true
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tools.forEachIndexed { i, tool ->
                Surface(
                    onClick = { onOpen(tool) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = OnBackground,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF101216),
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(Brush.linearGradient(tool.colors), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            TvIconImage(tool.icon, Color.White, size = 28.dp)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(tool.label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            subtitles[tool]?.let { Text(it, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.75f)) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
