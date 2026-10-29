package com.vantaflow.kairo.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0E1018)
private val Card = Color(0xFF1A1E2B)
private val Accent = Color(0xFF8AB4F8)
private val TextMain = Color(0xFFE8EAED)
private val TextMuted = Color(0xFFA9AEBF)

private sealed interface Screen {
    data object Home : Screen
    data class InFolder(val folder: Folder) : Screen
    data class Photos(val photos: List<Media>, val start: Int) : Screen
    data class Results(val query: String) : Screen
}

/** Kairo Player : vidéos, photos et musique de la box et des clés USB. */
class MainActivity : ComponentActivity() {
    private val library by lazy { MediaLibrary(this) }
    private val resume by lazy { ResumeStore(this) }
    private var media by mutableStateOf<List<Media>?>(null)
    private var progress by mutableStateOf<Map<Uri, Progress>>(emptyMap())
    private var granted by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private val reload = Runnable { refresh() }

    /** Clé branchée ou fichiers ajoutés : la liste se met à jour seule (avec un petit délai). */
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reload)
            handler.postDelayed(reload, 1500)
        }
    }

    /** Recherche reçue de Kairo (ACTION_SEARCH). */
    private var searchQuery by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        searchQuery = intent.takeIf { it.action == Intent.ACTION_SEARCH }?.getStringExtra(android.app.SearchManager.QUERY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchQuery = intent.takeIf { it.action == Intent.ACTION_SEARCH }?.getStringExtra(android.app.SearchManager.QUERY)
        contentResolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        // Thème sombre : boutons et focus bien visibles sur le fond de Kairo Player.
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { App() } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    private fun refresh() {
        granted = PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return
        Thread {
            val all = library.all()
            // Fichiers supprimés : plus de reprise ni de ligne dans « À suivre ».
            val existing = all.map { it.uri.toString() }.toSet()
            resume.prune(existing)
            WatchNext(this).prune(existing)
            RecentChannel(this).publish(all.filter { it.kind == Kind.Video })
            val p = resume.inProgress().toMap()
            runOnUiThread {
                media = all
                progress = p
            }
        }.start()
    }

    private fun play(item: Media, playlist: List<Media>) {
        val uris = ArrayList(playlist.filter { it.kind == item.kind }.map { it.uri })
        startActivity(playIntent(this, item.uri).putParcelableArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, uris))
    }

    @Composable
    private fun App() {
        var screen by remember { mutableStateOf<Screen>(searchQuery?.let { Screen.Results(it) } ?: Screen.Home) }
        LaunchedEffect(searchQuery) { searchQuery?.let { screen = Screen.Results(it) } }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
        BackHandler(enabled = screen != Screen.Home) {
            screen = when (val s = screen) {
                is Screen.Photos -> media?.let { all -> groupFolders(all).firstOrNull { f -> s.photos.firstOrNull()?.let { it in f.items } == true } }
                    ?.let { Screen.InFolder(it) } ?: Screen.Home
                else -> Screen.Home
            }
        }
        Box(Modifier.fillMaxSize().background(Bg)) {
            val all = media
            when {
                !granted -> Welcome(onAllow = { permission.launch(PERMISSIONS) })
                all == null -> Text("Chargement…", color = TextMuted, modifier = Modifier.align(Alignment.Center))
                else -> Crossfade(screen, animationSpec = tween(300), label = "écran") { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(all, onFolder = { screen = Screen.InFolder(it) }, onOpen = { item, list -> open(item, list) { screen = it } })
                        is Screen.InFolder -> FolderScreen(s.folder, onOpen = { item, list -> open(item, list) { screen = it } })
                        is Screen.Photos -> PhotoViewer(s.photos, s.start)
                        is Screen.Results -> {
                            val q = plainText(s.query)
                            val found = all.filter { plainText(it.name).contains(q) || plainText(it.folder).contains(q) }
                            FolderScreen(Folder("", "Recherche « ${s.query} »", found), onOpen = { item, list -> open(item, list) { screen = it } }, search = true)
                        }
                    }
                }
            }
        }
    }

    private fun open(item: Media, list: List<Media>, show: (Screen) -> Unit) {
        if (item.kind == Kind.Photo) {
            val photos = list.filter { it.kind == Kind.Photo }
            show(Screen.Photos(photos, photos.indexOf(item).coerceAtLeast(0)))
        } else {
            play(item, list)
        }
    }

    @Composable
    private fun Welcome(onAllow: () -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; runCatching { focus.requestFocus() } } }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Kairo Player", fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Spacer(Modifier.height(10.dp))
            Text("Vos vidéos, photos et musiques de la box et des clés USB.", fontSize = 20.sp, color = TextMuted)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAllow, modifier = Modifier.focusRequester(focus)) { Text("Autoriser l'accès aux fichiers") }
        }
    }

    @Composable
    private fun HomeScreen(all: List<Media>, onFolder: (Folder) -> Unit, onOpen: (Media, List<Media>) -> Unit) {
        val folders = remember(all) { groupFolders(all) }
        val byUri = remember(all) { all.associateBy { it.uri } }
        val resumeItems = remember(all, progress) { progress.keys.mapNotNull { byUri[it] } }
        val videos = remember(all) { all.filter { it.kind == Kind.Video }.sortedByDescending { it.dateAdded }.take(30) }
        val photos = remember(all) { all.filter { it.kind == Kind.Photo }.sortedByDescending { it.dateAdded }.take(30) }
        val music = remember(all) { all.filter { it.kind == Kind.Music }.sortedByDescending { it.dateAdded }.take(30) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { repeat(15) { withFrameNanos { }; runCatching { first.requestFocus() } } }

        LazyColumn(contentPadding = PaddingValues(top = 36.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            item {
                Row(Modifier.padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Kairo Player", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                    Spacer(Modifier.width(16.dp))
                    Text("${all.count { it.kind == Kind.Video }} vidéos · ${all.count { it.kind == Kind.Photo }} photos · ${all.count { it.kind == Kind.Music }} morceaux", color = TextMuted)
                }
            }
            if (all.isEmpty()) {
                item {
                    Text(
                        "Aucun fichier trouvé. Branchez une clé USB ou envoyez des fichiers depuis le téléphone (Kairo › Télécommande sur téléphone).",
                        color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
            }
            var firstUsed = false
            fun claim(): Modifier = if (!firstUsed) { firstUsed = true; Modifier.focusRequester(first) } else Modifier
            if (resumeItems.isNotEmpty()) {
                val m = claim()
                item(key = "resume") { MediaRow("Reprendre", resumeItems, m) { onOpen(it, resumeItems) } }
            }
            if (folders.isNotEmpty()) {
                val m = claim()
                item(key = "folders") {
                    Section("Dossiers") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(folders, key = { it.key }) { f ->
                                FolderCard(f, if (f == folders.first()) m else Modifier) { onFolder(f) }
                            }
                        }
                    }
                }
            }
            if (videos.isNotEmpty()) {
                val m = claim()
                item(key = "videos") { MediaRow("Vidéos récentes", videos, m) { onOpen(it, videos) } }
            }
            if (photos.isNotEmpty()) {
                val m = claim()
                item(key = "photos") { MediaRow("Photos récentes", photos, m) { onOpen(it, photos) } }
            }
            if (music.isNotEmpty()) {
                val m = claim()
                item(key = "music") { MediaRow("Musique", music, m) { onOpen(it, music) } }
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 20.sp, color = TextMain, modifier = Modifier.padding(start = 56.dp))
            content()
        }
    }

    @Composable
    private fun MediaRow(title: String, items: List<Media>, firstModifier: Modifier, onClick: (Media) -> Unit) {
        Section(title) {
            LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(items, key = { it.uri.toString() }) { m ->
                    MediaCard(m, progress[m.uri], if (m == items.first()) firstModifier else Modifier) { onClick(m) }
                }
            }
        }
    }

    @Composable
    private fun FolderScreen(folder: Folder, onOpen: (Media, List<Media>) -> Unit, search: Boolean = false) {
        val first = remember { FocusRequester() }
        LaunchedEffect(folder.key) { repeat(15) { withFrameNanos { }; runCatching { first.requestFocus() } } }
        Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
            Text(if (search) folder.path else folder.name, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = TextMain, modifier = Modifier.padding(start = 56.dp))
            Text(if (search) "${folder.items.size} résultats" else "${library.volumeLabel(folder.volume)} › ${folder.path.trimEnd('/')} · ${folder.items.size} fichiers", color = TextMuted, modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(folder.items, key = { _, m -> m.uri.toString() }) { i, m ->
                    MediaCard(m, progress[m.uri], if (i == 0) Modifier.focusRequester(first) else Modifier, fill = true) { onOpen(m, folder.items) }
                }
            }
        }
    }

    @Composable
    private fun FolderCard(folder: Folder, modifier: Modifier, onClick: () -> Unit) {
        Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardSurface(onClick, modifier.fillMaxWidth().height(135.dp)) {
                folder.cover?.let { Thumb(it) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                Text("${folder.items.size}", color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(folder.name, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(library.volumeLabel(folder.volume), color = TextMuted, fontSize = 13.sp, maxLines = 1)
        }
    }

    @Composable
    private fun MediaCard(m: Media, p: Progress?, modifier: Modifier, fill: Boolean = false, onClick: () -> Unit) {
        Column(if (fill) Modifier.fillMaxWidth() else Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardSurface(onClick, modifier.fillMaxWidth().height(135.dp)) {
                Thumb(m)
                if (m.kind != Kind.Photo && m.durationMs > 0) {
                    Text(
                        formatDuration(m.durationMs, music = m.kind == Kind.Music), color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (p != null && shouldResume(p)) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.percent / 100f).background(Accent))
                    }
                }
            }
            if (m.kind != Kind.Photo) Text(displayTitle(m.name), color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun CardSurface(onClick: () -> Unit, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
            colors = ClickableSurfaceDefaults.colors(containerColor = Card, focusedContainerColor = Card),
        ) { Box(Modifier.fillMaxSize(), content = content) }
    }

    /** Vignette fournie par l'index des médias (rapide, déjà en cache chez Android). */
    @Composable
    private fun Thumb(m: Media) {
        val context = LocalContext.current
        val bitmap by produceState(thumbCache.get(m.uri.toString()), m.uri) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.loadThumbnail(m.uri, Size(384, 216), null).asImageBitmap() }.getOrNull()
                }?.also { thumbCache.put(m.uri.toString(), it) }
            }
        }
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF243056), Color(0xFF141826)))), contentAlignment = Alignment.Center) {
                Text(if (m.kind == Kind.Music) "♪" else "▶", color = Accent, fontSize = 34.sp)
            }
        }
    }

    /** Photos plein écran : ← → pour changer, OK lance ou arrête le diaporama (5 s). */
    @Composable
    private fun PhotoViewer(photos: List<Media>, start: Int) {
        var index by remember { mutableIntStateOf(start) }
        var slideshow by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; runCatching { focus.requestFocus() } } }
        LaunchedEffect(slideshow, index) {
            if (slideshow) {
                delay(5000)
                index = (index + 1) % photos.size
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> { index = (index + 1) % photos.size; true }
                        Key.DirectionLeft -> { index = (index - 1 + photos.size) % photos.size; true }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { slideshow = !slideshow; true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            Crossfade(photos[index], animationSpec = tween(600), label = "photo") { m ->
                AsyncImage(model = m.uri, contentDescription = m.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Text(
                "${index + 1} / ${photos.size}" + if (slideshow) " · diaporama" else " · OK : diaporama",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    companion object {
        val PERMISSIONS = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        /** ~40 vignettes 384×216 en mémoire (une quinzaine de Mo). */
        private val thumbCache = LruCache<String, ImageBitmap>(40)
    }
}

/** Sans accents ni majuscules, pour la recherche. */
private fun plainText(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
