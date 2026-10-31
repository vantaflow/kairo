package com.vantaflow.kairo.catalog

import androidx.compose.ui.zIndex
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vantaflow.kairo.radio.PlayerButton
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fiche d'un film ou d'une série : image, résumé, « Regarder sur » chaque appli de la box qui l'a
 * dans le pays de la box, et « Ma liste ». Retour la ferme.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatalogDetail(
    item: CatalogItem,
    repository: CatalogRepository,
    inMyList: Boolean,
    onToggleMyList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var where by remember(item) { mutableStateOf<List<Pair<Provider, String>>?>(null) }
    var overview by remember(item) { mutableStateOf(item.overview) }
    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            if (overview.isBlank()) repository.overview(item)?.let { overview = it }
            where = repository.whereToWatch(item)
        }
    }
    val first = remember { FocusRequester() }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0E1014))
            .focusGroup().focusProperties { exit = { FocusRequester.Cancel } }
            .onPreviewKeyEvent { e ->
                if (e.key.nativeKeyCode != android.view.KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) onDismiss()
                true
            },
    ) {
        AsyncImage(
            model = remember(item) { ImageRequest.Builder(context).data(tmdbImage(item.backdrop ?: item.poster, "w1280")).size(1280, 720).allowRgb565(true).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(),
        )
        // Attribution exigée par les conditions de TMDB.
        Text(
            "Données TMDB · non approuvé par TMDB",
            fontSize = 13.sp, color = OnBackgroundMuted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 28.dp).zIndex(1f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0.25f to Color(0xFF0E1014), 0.62f to Color(0xFF0E1014).copy(alpha = 0.55f), 1f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xFF0E1014))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp).width(760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (item.type == MediaType.Movie) "Film" else "Série", fontSize = 18.sp, color = OnBackgroundMuted)
            Text(item.title, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.year, item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                fontSize = 20.sp, color = OnBackgroundMuted,
            )
            if (overview.isNotBlank()) Text(overview, fontSize = 19.sp, color = OnBackground, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 27.sp)
            Text(
                when {
                    where == null -> "Recherche des applis où le regarder…"
                    where!!.isEmpty() -> "Pas disponible dans vos applis (${countryName(repository.region())})."
                    else -> "Disponible sur : " + where!!.joinToString(", ") { it.first.name }
                },
                fontSize = 16.sp, color = OnBackgroundMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                where.orEmpty().forEachIndexed { i, (p, pkg) ->
                    PlayerButton("▶  ${p.name}", if (i == 0) Modifier.focusRequester(first) else Modifier, big = i == 0) {
                        com.vantaflow.kairo.reco.LaunchedTitles.record(context, pkg, item)
                        openInApp(context, pkg, item.title)
                    }
                }
                PlayerButton(
                    if (inMyList) "✓  Ma liste" else "＋  Ma liste",
                    if (where.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier,
                    big = where.isNullOrEmpty(),
                    onClick = onToggleMyList,
                )
            }
        }
    }
    LaunchedEffect(where) { runCatching { first.requestFocus() } }
}

/** Ouvre l'appli sur sa recherche du titre si elle l'accepte, sinon sur son accueil. */
fun openInApp(context: Context, pkg: String, title: String) {
    val pm = context.packageManager
    val search = Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, title).putExtra("query", title)
    val intent = search.takeIf { pm.queryIntentActivities(it, 0).isNotEmpty() }
        ?: pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
