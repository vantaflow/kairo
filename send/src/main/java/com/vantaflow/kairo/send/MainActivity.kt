package com.vantaflow.kairo.send

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF14111F)
private val Panel = Color(0xFF1C1829)
private val Card = Color(0xFF26203A)
private val Accent = Color(0xFFA98BFF)
private val TextMain = Color(0xFFEFEDF6)
private val TextMuted = Color(0xFFA39DBA)
private val Danger = Color(0xFFFF7A7A)

class MainActivity : ComponentActivity() {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { SendScreen(resumes) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        sendApp.visible = true
        SendService.start(this)
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    override fun onStop() {
        sendApp.visible = false
        SendService.idleCheck()
        super.onStop()
    }

    /** Fichiers partagés depuis la box (Kairo Files › Envoyer au téléphone) : copiés dans le dossier proposé au téléphone. */
    private fun handleShare(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelable(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableList(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { uris.isEmpty() && it.isNotBlank() }?.let { Hub.note(it) }
        if (uris.isEmpty()) return
        val store = sendApp.store
        uris.forEach { uri ->
            val (name, size) = nameAndSize(uri)
            val id = Hub.start(name, size.coerceAtLeast(0))
            io.launch {
                val ok = runCatching {
                    contentResolver.openInputStream(uri)!!.use { input ->
                        store.save(name, contentResolver.getType(uri), input, -1) { Hub.progress(id, it) }
                    }
                }.isSuccess
                Hub.finish(id, ok)
            }
        }
    }

    private fun nameAndSize(uri: Uri): Pair<String, Long> = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "fichier") to (if (c.isNull(1)) -1L else c.getLong(1)) else null
        }
    }.getOrNull() ?: ((uri.lastPathSegment?.substringAfterLast('/') ?: "fichier") to -1L)
}

@Suppress("DEPRECATION")
private fun Intent.parcelable(key: String): Uri? = getParcelableExtra(key) as? Uri

@Suppress("DEPRECATION")
private fun Intent.parcelableList(key: String): List<Uri> = getParcelableArrayListExtra<Uri>(key).orEmpty()

/** Adresse IPv4 de la box sur le réseau local (Wi-Fi ou Ethernet) ; null hors réseau. */
fun localIpv4(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
        ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}

fun qrCode(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val units = listOf("Ko", "Mo", "Go", "To")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return (if (value >= 100) "%.0f" else "%.1f").format(Locale.FRANCE, value) + " " + units[i]
}

private fun open(context: Context, file: SharedFile): String? {
    val mime = file.mime ?: android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    // Vidéos, musiques, PDF et textes : directement dans Kairo Player s'il est installé.
    val ext = file.name.substringAfterLast('.', "").lowercase()
    if (mime.startsWith("video/") || mime.startsWith("audio/") || ext in setOf("pdf", "txt", "srt", "log", "md", "csv", "json", "nfo")) {
        if (runCatching { context.startActivity(Intent(intent).setPackage("com.vantaflow.kairo.player")) }.isSuccess) return null
    }
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "Aucune appli ne sait ouvrir ce fichier"
    }
}

private fun openLink(context: Context, link: String): String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (e: ActivityNotFoundException) {
    "Aucun navigateur sur la box"
}

@Composable
fun SendScreen(resumes: Int) {
    val context = LocalContext.current
    val app = context.sendApp
    val transfers by Hub.transfers.collectAsState()
    val notes by Hub.notes.collectAsState()
    val version by Hub.version.collectAsState()
    var files by remember { mutableStateOf<List<SharedFile>?>(null) }
    var ip by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<SharedFile?>(null) }
    var noteOpen by remember { mutableStateOf<Note?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version, resumes) { files = withContext(Dispatchers.IO) { app.store.list() } }
    // Le réseau peut changer (Wi-Fi qui revient au réveil) : l'adresse est relue régulièrement.
    LaunchedEffect(resumes) {
        while (true) {
            ip = localIpv4(context)
            delay(5000)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            message = null
        }
    }
    val url = ip?.let { "http://$it:$SEND_PORT/?k=${app.token}" }
    val qr = remember(url) { url?.let(::qrCode) }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.width(440.dp).fillMaxHeight().background(Panel).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Kairo Send Files", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Spacer(Modifier.height(20.dp))
            if (qr != null && url != null) {
                Box(Modifier.size(230.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    Image(qr, contentDescription = "QR code", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(18.dp))
                Text("Scannez avec le téléphone, ou ouvrez sur l'ordinateur :", fontSize = 15.sp, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                Text(url, fontSize = 15.sp, color = TextMain, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(14.dp))
                Text("Même Wi-Fi que la box. Les fichiers arrivent dans Téléchargements › Kairo Send.", fontSize = 13.sp, color = TextMuted)
            } else {
                Text("La box n'est connectée à aucun réseau.", fontSize = 17.sp, color = TextMuted)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp, end = 48.dp, top = 36.dp)) {
            Text("Fichiers échangés", fontSize = 22.sp, color = TextMain, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            val list = files
            // Le focus va sur le premier élément (envoi en cours, texte reçu ou fichier).
            val firstKey = transfers.firstOrNull()?.let { "t${it.id}" } ?: notes.firstOrNull()?.let { "n${it.id}" }
                ?: list?.firstOrNull()?.let { "f${it.id}" }
            val first = remember { FocusRequester() }
            LaunchedEffect(firstKey != null) {
                if (firstKey == null) return@LaunchedEffect
                repeat(10) {
                    delay(50)
                    if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                }
            }
            fun mod(key: String) = if (key == firstKey) Modifier.focusRequester(first) else Modifier
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                items(transfers, key = { "t${it.id}" }) { t ->
                    val pct = if (t.total > 0) (t.done * 100 / t.total).toInt().coerceIn(0, 100) else 0
                    Item(
                        modifier = mod("t${t.id}"),
                        icon = if (t.failed) "⚠" else "⬇",
                        title = t.name,
                        detail = if (t.failed) "Échec de l'envoi · OK pour effacer"
                        else if (t.total > 0) "${formatSize(t.done)} sur ${formatSize(t.total)} · $pct %" else formatSize(t.done),
                        progress = if (t.failed || t.total <= 0) null else pct / 100f,
                        onClick = { if (t.failed) Hub.dismissFailed() },
                    )
                }
                items(notes, key = { "n${it.id}" }) { n ->
                    Item(
                        modifier = mod("n${n.id}"),
                        icon = if (isWebLink(n.text)) "🔗" else "💬",
                        title = n.text.lineSequence().first(),
                        detail = if (isWebLink(n.text)) "Lien reçu · OK pour l'ouvrir" else "Texte reçu · OK pour le lire",
                        onClick = { if (isWebLink(n.text)) openLink(context, n.text)?.let { message = it } else noteOpen = n },
                        onMenu = { Hub.removeNote(n.id) },
                    )
                }
                when {
                    list == null -> item { Text("Lecture…", color = TextMuted, fontSize = 17.sp) }
                    list.isEmpty() && transfers.isEmpty() && notes.isEmpty() -> item {
                        Text(
                            "Rien pour l'instant. Envoyez un fichier depuis le téléphone, ou, depuis Kairo Files, choisissez « Envoyer au téléphone ».",
                            color = TextMuted, fontSize = 17.sp, lineHeight = 26.sp,
                        )
                    }
                    else -> items(list, key = { "f${it.id}" }) { f ->
                        Item(
                            modifier = mod("f${f.id}"),
                            icon = iconFor(f.name),
                            title = f.name,
                            detail = formatSize(f.size) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.added)),
                            onClick = { open(context, f)?.let { message = it } },
                            onMenu = { menuFor = f },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { f ->
        Dialog(f.name, onDismiss = { menuFor = null }) {
            MenuButton("Ouvrir", first = true) { menuFor = null; open(context, f)?.let { message = it } }
            MenuButton("Supprimer", danger = true) {
                menuFor = null
                if (app.store.delete(f)) Hub.changed() else message = "Suppression impossible"
            }
        }
    }

    noteOpen?.let { n ->
        Dialog("Texte reçu", onDismiss = { noteOpen = null }) {
            Text(n.text, color = TextMain, fontSize = 18.sp, lineHeight = 27.sp, modifier = Modifier.padding(bottom = 14.dp))
            MenuButton("Fermer", first = true) { noteOpen = null }
            MenuButton("Effacer", danger = true) { Hub.removeNote(n.id); noteOpen = null }
        }
    }

    message?.let { m ->
        Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                m, color = TextMain, fontSize = 17.sp,
                modifier = Modifier.background(Color(0xEE2E2842), RoundedCornerShape(12.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

private fun iconFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "mkv", "avi", "mov", "webm", "ts", "m4v" -> "🎬"
    "mp3", "flac", "aac", "m4a", "ogg", "wav", "opus" -> "🎵"
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> "🖼"
    "apk" -> "📦"
    "zip", "rar", "7z" -> "🗜"
    else -> "📄"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Item(
    icon: String,
    modifier: Modifier = Modifier,
    title: String,
    detail: String,
    progress: Float? = null,
    onMenu: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onMenu,
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { k ->
            if (onMenu != null && k.key == Key.Menu && k.type == KeyEventType.KeyUp) { onMenu(); true } else false
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Card, focusedContainerColor = Color(0xFF342C4E)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent))),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = TextMuted, fontSize = 13.sp, maxLines = 1)
                progress?.let { p ->
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Bg, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Accent, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuButton(label: String, first: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) {
        repeat(10) {
            delay(30)
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(if (first) Modifier.focusRequester(requester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = if (danger) Danger else TextMain,
            focusedContentColor = if (danger) Color(0xFFC62828) else Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(label, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
    }
}

@Composable
private fun Dialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp).background(Panel, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Text(title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}
