package com.vantaflow.kairo.tools

import androidx.compose.foundation.background
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
