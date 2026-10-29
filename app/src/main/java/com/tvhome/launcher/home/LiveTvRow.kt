package com.tvhome.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tvhome.launcher.reco.LiveChannel
import com.tvhome.launcher.reco.rememberRowState
import com.tvhome.launcher.ui.theme.LocalHomeLayout
import com.tvhome.launcher.ui.theme.OnBackground

/** Rangée de chaînes TV ou d'entrées HDMI ; n'est affichée que si la box en a (liste non vide). */
@Composable
fun LiveTvRow(
    title: String,
    channels: List<LiveChannel>,
    onOpen: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    val layout = LocalHomeLayout.current
    Column(modifier) {
        if (layout.showRowTitles) {
            Text(title, fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        LazyRow(
            state = rememberRowState(),
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(channels, key = { it.key }) { channel ->
                ChannelTile(
                    channel,
                    onClick = { onOpen(channel) },
                    width = layout.tileWidthDp.dp,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
                )
            }
        }
    }
}

/** Tuile de chaîne : logo publié par le tuner s'il existe, sinon le nom ; numéro en haut à gauche. */
@Composable
private fun ChannelTile(channel: LiveChannel, onClick: () -> Unit, width: Dp, modifier: Modifier = Modifier) {
    TileFrame(onClick = onClick, onLongClick = {}, modifier = modifier, width = width) {
        var hasLogo by remember(channel.logoUri) { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { contentDescription = listOfNotNull(channel.number, channel.name).joinToString(" ") },
        ) {
            if (channel.logoUri != null) {
                AsyncImage(
                    model = channel.logoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { hasLogo = true },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
                )
            }
            if (!hasLogo) {
                Text(
                    channel.name,
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                )
            }
            channel.number?.let {
                Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
    }
}
