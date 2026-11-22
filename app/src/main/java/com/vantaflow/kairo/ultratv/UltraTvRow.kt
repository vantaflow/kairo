package com.vantaflow.kairo.ultratv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.vantaflow.kairo.R
import com.vantaflow.kairo.home.TileFrame
import com.vantaflow.kairo.reco.rememberRowState
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.LocalLauncherTheme
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/**
 * Rangée « Ultra TV » affichée : réglage actif, Ultra TV installée, ni le profil Kairo ni celui
 * d'Ultra TV en mode enfants. Sans chaîne, les raccourcis restent.
 */
fun utvRowVisible(state: UtvState, kairoKids: Boolean, shown: Boolean): Boolean =
    shown && state.pkg != null && !kairoKids && !state.kids

/** Hauteur réservée sous chaque tuile (titre en cours, barre, suivant) : la rangée garde sa hauteur quand les chaînes arrivent. */
private val InfoHeight = 54.dp

/**
 * Rangée Ultra TV : quatre raccourcis vers ses rubriques, puis une tuile par chaîne avec le programme
 * en cours et le suivant. [nowMs] est le même pour toute la rangée (rafraîchi chaque minute par l'appelant).
 */
@Composable
fun UltraTvRow(
    state: UtvState,
    nowMs: Long,
    onOpenSection: (UtvSection) -> Unit,
    onOpenChannel: (UtvChannel) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    val layout = LocalHomeLayout.current
    val width = layout.tileWidthDp.dp
    // Une tuile par lien (la première occurrence) : le lien sert de clé stable, la tuile sélectionnée
    // survit à un ajout, un retrait ou un nouvel ordre des chaînes.
    val channels = remember(state.channels) { state.channels.distinctBy { it.deepLink } }
    val currentChannels by rememberUpdatedState(channels)
    val rowState = rememberRowState()
    val firstSection = remember { FocusRequester() }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val selected = remember { SelectedChannel() }

    // Chaîne sélectionnée disparue de la liste : le focus passe à la tuile voisine (la dernière
    // restante au besoin), sinon au premier raccourci ; il ne reste jamais perdu.
    val previous = remember { arrayOf(channels) }
    LaunchedEffect(channels) {
        // Position de la chaîne disparue cherchée dans la liste d'avant (elle a pu être réordonnée).
        val before = previous[0]
        previous[0] = channels
        requesters.keys.retainAll(channels.mapTo(HashSet()) { it.deepLink })
        val link = selected.link ?: return@LaunchedEffect
        if (channels.any { it.deepLink == link }) return@LaunchedEffect
        selected.link = null
        val position = before.indexOfFirst { it.deepLink == link }.coerceAtLeast(0)
        val target = if (channels.isEmpty()) null else channels[position.coerceIn(0, channels.lastIndex)]
        runCatching { rowState.scrollToItem(if (target == null) 0 else UtvSection.entries.size + channels.indexOf(target)) }
        val requester = target?.let { requesters.getOrPut(it.deepLink) { FocusRequester() } } ?: firstSection
        for (attempt in 1..10) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) break
        }
    }

    Column(modifier.testTag("utv-row")) {
        if (layout.showRowTitles) {
            Text(stringResource(R.string.utv_row_title), fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(UtvSection.entries, key = { "section-${it.name}" }) { section ->
                Column(Modifier.width(width)) {
                    SectionTile(
                        section,
                        onClick = { onOpenSection(section) },
                        width = width,
                        modifier = Modifier
                            .testTag("utv-section-${section.name.lowercase()}")
                            .then(if (section == UtvSection.entries.first()) Modifier.focusRequester(firstSection) else Modifier)
                            .onFocusChanged { if (it.isFocused) onFocused() },
                    )
                    Spacer(Modifier.height(10.dp + InfoHeight))
                }
            }
            itemsIndexed(channels, key = { _, c -> "channel-${c.deepLink}" }) { index, channel ->
                Column(Modifier.width(width)) {
                    UtvChannelTile(
                        channel,
                        onClick = { onOpenChannel(channel) },
                        width = width,
                        modifier = Modifier
                            .testTag("utv-channel-$index")
                            .focusRequester(requesters.getOrPut(channel.deepLink) { FocusRequester() })
                            .onFocusChanged {
                                if (it.isFocused) {
                                    selected.link = channel.deepLink
                                    onFocused()
                                } else if (selected.link == channel.deepLink && currentChannels.any { c -> c.deepLink == channel.deepLink }) {
                                    // Focus parti ailleurs (la chaîne existe toujours) : plus rien à reprendre.
                                    selected.link = null
                                }
                            },
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgramInfo(channel, nowMs, index)
                }
            }
        }
    }
}

/** Lien de la chaîne qui a le focus ; lu seulement dans les rappels de focus et l'effet de reprise. */
private class SelectedChannel {
    var link: String? = null
}

/** Raccourci vers une rubrique d'Ultra TV : icône au trait (couleur du thème) et libellé. */
@Composable
private fun SectionTile(section: UtvSection, onClick: () -> Unit, width: Dp, modifier: Modifier) {
    val label = stringResource(section.label)
    val accent = Color(LocalLauncherTheme.current.accent)
    TileFrame(onClick = onClick, onLongClick = {}, modifier = modifier, width = width) {
        Column(
            Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val painter = rememberVectorPainter(section.icon)
            val filter = remember(accent) { ColorFilter.tint(accent) }
            Image(painter, contentDescription = null, colorFilter = filter, modifier = Modifier.size(32.dp))
            Text(
                label,
                fontSize = 17.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
    }
}

/** Tuile de chaîne, au style des chaînes TV de l'accueil : logo (sinon le nom), numéro en haut au début. */
@Composable
private fun UtvChannelTile(channel: UtvChannel, onClick: () -> Unit, width: Dp, modifier: Modifier) {
    TileFrame(onClick = onClick, onLongClick = {}, modifier = modifier, width = width) {
        var hasLogo by remember(channel.logo) { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxSize()
                // Chaîne verrouillée : rien du programme, pas même pour l'accessibilité.
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(channel.number, channel.name, channel.nowTitle.takeUnless { channel.locked }).joinToString(" ")
                },
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
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
                    style = ContentDirection,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                )
            }
            channel.number?.let {
                Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
    }
}

/** Sous la tuile : titre en cours, barre d'avancement fixe (pas d'animation), « Ensuite : … ». Rien pour une chaîne verrouillée. */
@Composable
private fun ProgramInfo(channel: UtvChannel, nowMs: Long, index: Int) {
    Column(Modifier.fillMaxWidth().height(InfoHeight)) {
        if (channel.locked) return@Column
        channel.nowTitle?.let {
            Text(it, fontSize = 15.sp, color = OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ContentDirection)
        }
        nowProgressPercent(channel, nowMs)?.let { percent ->
            val accent = Color(LocalLauncherTheme.current.accent)
            Box(
                Modifier
                    .padding(vertical = 5.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .testTag("utv-progress-$index"),
            ) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(3.dp).background(accent))
            }
        }
        channel.nextTitle?.let {
            Text(
                stringResource(R.string.utv_next, it),
                fontSize = 13.sp,
                color = OnBackgroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ContentDirection,
            )
        }
    }
}

/** Titres venus d'Ultra TV : chacun garde son propre sens d'écriture (arabe ou latin), quelle que soit la langue de Kairo. */
private val ContentDirection = TextStyle(textDirection = TextDirection.Content)

private val UtvSection.label: Int
    get() = when (this) {
        UtvSection.Guide -> R.string.utv_section_guide
        UtvSection.Live -> R.string.utv_section_live
        UtvSection.Movies -> R.string.utv_section_movies
        UtvSection.Series -> R.string.utv_section_series
    }

// Icônes au trait des raccourcis (24 × 24, trait de 2, extrémités arrondies), comme les icônes de Kairo.
private val GuideIcon by lazy { strokeIcon("guide", "M3 5h18v14H3z M3 10h18 M9 10v9 M15 10v9") }
private val LiveIcon by lazy { strokeIcon("live", "M3 8h18v12H3z M8 3l4 5 4-5") }
private val MoviesIcon by lazy { strokeIcon("movies", "M4 4h16v16H4z M8 4v16 M16 4v16 M4 9h4 M4 15h4 M16 9h4 M16 15h4") }
private val SeriesIcon by lazy { strokeIcon("series", "M3 8h14v12H3z M7 4h14v12 M8.5 11l4 3-4 3z") }

private val UtvSection.icon: ImageVector
    get() = when (this) {
        UtvSection.Guide -> GuideIcon
        UtvSection.Live -> LiveIcon
        UtvSection.Movies -> MoviesIcon
        UtvSection.Series -> SeriesIcon
    }

private fun strokeIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
