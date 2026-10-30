package com.vantaflow.kairo.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.vantaflow.kairo.radio.PlayerButton
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/**
 * Petit lecteur de la pastille « ♪ » quand une autre appli joue (Spotify, Anghami…) :
 * précédent, lecture / pause, suivant et retour dans l'appli. Il se ferme quand la lecture s'arrête.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaMiniPlayer(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now by NowPlaying.state.collectAsStateWithLifecycle()
    val play = remember { FocusRequester() }
    LaunchedEffect(now == null) { if (now == null) onDismiss() }
    val media = now ?: return

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 72.dp).width(560.dp)
                .background(Color(0xFF202329), RoundedCornerShape(22.dp)).padding(20.dp)
                .focusGroup().focusProperties { exit = { FocusRequester.Cancel } }
                .onPreviewKeyEvent { e ->
                    if (e.key.nativeKeyCode != android.view.KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyUp) onDismiss()
                    true
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaArt(media, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(media.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(media.subtitle, media.appLabel).distinct().joinToString(" · "),
                        fontSize = 16.sp, color = OnBackgroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton("⏮") { NowPlaying.previous() }
                PlayerButton(if (media.playing) "❚❚  Pause" else "▶  Lecture", Modifier.focusRequester(play), big = true) { NowPlaying.togglePlay() }
                PlayerButton("⏭") { NowPlaying.next() }
                PlayerButton("Ouvrir ${media.appLabel}".take(24)) { onDismiss(); NowPlaying.open(context) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

/** Pochette, sinon une note sur fond sombre. */
@Composable
fun MediaArt(media: MediaNow, size: Dp) {
    val shape = RoundedCornerShape(12.dp)
    val art = media.art
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).background(Color(0xFF2C3038), shape), contentAlignment = Alignment.Center) {
            Text("♪", fontSize = (size.value / 2.4f).sp, color = Color.White)
        }
    }
}
