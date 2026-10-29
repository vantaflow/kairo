package com.vantaflow.kairo.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.settings.SmallButton
import com.vantaflow.kairo.settings.TvInput
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.ui.theme.SurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val FAVORITES = "fav"
private const val SEARCH = "search"

/** Onglet d'arrivée : le pays où se trouve la box (par l'IP), sinon le dernier choisi, sinon la langue de la box. */
fun initialRadioTab(ipCountry: String?, savedCountry: String?, boxCountry: String): String =
    listOf(ipCountry, savedCountry, boxCountry).firstOrNull { !it.isNullOrBlank() } ?: ""

/** Pays proposés, celui de l'utilisateur en premier (ajouté s'il n'est pas dans la liste). */
fun radioCountriesFor(user: String): List<Pair<String, String>> {
    if (user.isBlank()) return RadioCountries
    val label = RadioCountries.firstOrNull { it.first == user }?.second
        ?: Locale("", user).getDisplayCountry(Locale.FRENCH).ifBlank { user }
    return listOf(user to label) + RadioCountries.filterNot { it.first == user }
}

/** Pays de la box d'après son adresse IP (gardé pour la session). */
@Volatile private var ipCountryCache: String? = null

private fun ipCountry(http: com.vantaflow.kairo.net.HttpGet): String =
    ipCountryCache ?: runCatching {
        org.json.JSONObject(http.get("https://get.geojs.io/v1/ip/country.json")!!).optString("country")
    }.getOrDefault("").also { if (it.isNotBlank()) ipCountryCache = it }

/** Écran Radio : radios en direct par pays, favorites et recherche ; la lecture continue hors de l'écran. */
@Composable
fun RadioScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    val scope = rememberCoroutineScope()
    val browser = remember { RadioBrowser(container.http) }
    val favorites by container.prefs.radioFavorites.collectAsStateWithLifecycle(null)
    val savedCountry by container.prefs.radioCountry.collectAsStateWithLifecycle("?")
    val now by Radio.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    val firstChip = remember { FocusRequester() }

    // Onglet de départ : le pays de l'utilisateur, une fois les préférences lues.
    var userCountry by remember { mutableStateOf("") }
    LaunchedEffect(savedCountry) {
        if (tab != null || savedCountry == "?") return@LaunchedEffect
        val ip = withContext(Dispatchers.IO) { ipCountry(container.http) }
        tab = initialRadioTab(ip, savedCountry, Locale.getDefault().country)
        userCountry = tab.orEmpty()
    }
    val current = tab
    val stations by produceState<List<Station>?>(null, current, searched) {
        value = null
        value = when (current) {
            null -> null
            FAVORITES -> favorites.orEmpty().distinctBy { it.url }
            SEARCH -> if (searched.isBlank()) emptyList() else withContext(Dispatchers.IO) { browser.search(searched) }
            else -> withContext(Dispatchers.IO) { browser.top(current) }
        }
    }
    val favUrls = favorites.orEmpty().map { it.url }.toSet()
    fun play(s: Station) {
        Radio.toggle(context, s)
        scope.launch(Dispatchers.IO) { runCatching { browser.click(s.id) } }
    }
    fun toggleFavorite(s: Station) = scope.launch { container.prefs.toggleRadioFavorite(s) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1240), Background)))
            .padding(start = 72.dp, end = 72.dp, top = 44.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        NowPlaying(now, isFavorite = now.station?.url in favUrls, onToggle = { now.station?.let(::play) }, onFavorite = { now.station?.let(::toggleFavorite) })
        ChoiceRow {
            if (favorites.orEmpty().isNotEmpty()) {
                ChoiceChip(current == FAVORITES, { tab = FAVORITES }) { Text("★ Favoris") }
            }
            radioCountriesFor(userCountry).forEach { (code, label) ->
                val chip = @Composable {
                    ChoiceChip(current == code, {
                        tab = code
                        scope.launch { container.prefs.setRadioCountry(code) }
                    }) { Text(label) }
                }
                // Focus d'arrivée sur le pays de l'utilisateur.
                if (code == userCountry) Box(Modifier.focusRequester(firstChip)) { chip() } else chip()
            }
            ChoiceChip(current == SEARCH, { tab = SEARCH }) { Text("Rechercher") }
        }
        if (current == SEARCH) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvInput(query, { query = it }, Modifier.width(520.dp), placeholder = "Nom de la radio", onDone = { searched = query })
                SmallButton("Chercher", { searched = query })
            }
        }
        val list = stations
        when {
            list == null -> Text("Chargement des radios…", color = OnBackgroundMuted, fontSize = 20.sp)
            list.isEmpty() -> Text(
                when (current) {
                    SEARCH -> if (searched.isBlank()) "Tapez le nom d'une radio (ou envoyez-le depuis le téléphone)." else "Aucune radio trouvée pour « $searched »."
                    FAVORITES -> "Aucune favorite : appui long sur une radio pour l'ajouter."
                    else -> "Radios indisponibles : vérifiez la connexion."
                },
                color = OnBackgroundMuted, fontSize = 20.sp,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(list, key = { it.url }) { s ->
                    StationTile(
                        s,
                        playing = now.active && now.station?.url == s.url,
                        favorite = s.url in favUrls,
                        onClick = { play(s) },
                        onLongClick = { toggleFavorite(s) },
                    )
                }
            }
        }
    }
    LaunchedEffect(current != null) {
        if (current != null) runCatching { firstChip.requestFocus() }
    }
}

@Composable
private fun NowPlaying(now: RadioState, isFavorite: Boolean, onToggle: () -> Unit, onFavorite: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StationLogo(now.station, 110.dp)
        Column(Modifier.weight(1f)) {
            Text("Radio", fontSize = 18.sp, color = OnBackgroundMuted)
            Text(now.station?.name ?: "Choisissez une radio", fontSize = 38.sp, fontWeight = FontWeight.Medium, color = OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (now.status) {
                    RadioStatus.Loading -> "Connexion…"
                    RadioStatus.Playing -> "♪ En direct · la radio continue sur l'accueil et dans les menus"
                    RadioStatus.Error -> "Cette radio ne répond pas, essayez-en une autre."
                    RadioStatus.Idle -> if (now.station != null) "En pause" else "OK pour écouter · appui long pour l'ajouter aux favoris"
                },
                fontSize = 18.sp,
                color = if (now.status == RadioStatus.Error) Color(0xFFFF8A80) else OnBackgroundMuted,
            )
        }
        if (now.station != null) {
            SmallButton(if (now.active) "■ Arrêter" else "▶ Écouter", onToggle)
            SmallButton(if (isFavorite) "★ Retirer des favoris" else "☆ Favori", onFavorite)
        }
    }
}

@Composable
private fun StationTile(s: Station, playing: Boolean, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.height(150.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor.copy(alpha = 0.85f),
            contentColor = OnBackground,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF101216),
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (playing) Border(BorderStroke(3.dp, AccentColor), shape = shape) else Border.None,
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StationLogo(s, 64.dp)
            Text(
                (if (playing) "♪ " else "") + s.name,
                fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (favorite) Text("★", fontSize = 18.sp, color = Color(0xFFFFC94D), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

/** Logo de la radio, sinon ses initiales sur un fond de couleur stable. */
@Composable
private fun StationLogo(s: Station?, size: Dp) {
    val fallback = @Composable {
        val hue = ((s?.name?.hashCode() ?: 0) and 0xFFFFFF) % 360
        Box(Modifier.fillMaxSize().background(Color.hsv(hue.toFloat(), 0.45f, 0.55f)), contentAlignment = Alignment.Center) {
            Text(
                s?.name?.split(' ')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.take(1).uppercase() } ?: "♪",
                fontSize = (size.value / 2.6f).sp, color = Color.White, fontWeight = FontWeight.Bold,
            )
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 5)).background(Color.White.copy(alpha = 0.08f))) {
        if (s == null || s.favicon.isBlank()) fallback()
        else SubcomposeAsyncImage(
            model = s.favicon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}
