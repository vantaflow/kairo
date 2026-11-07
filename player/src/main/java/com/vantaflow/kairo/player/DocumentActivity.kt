package com.vantaflow.kairo.player

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Taille maximale d'un texte affiché (au-delà, le début seulement). */
private const val TEXT_MAX = 2 * 1024 * 1024

/**
 * Texte en UTF-8 s'il est valide, sinon en Windows-1252 (fichiers .txt et sous-titres faits sous Windows).
 * Le BOM éventuel est retiré.
 */
fun decodeText(bytes: ByteArray): String {
    val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
    val buffer = ByteBuffer.wrap(bytes, start, bytes.size - start)
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(buffer).toString()
    } catch (e: CharacterCodingException) {
        String(bytes, start, bytes.size - start, Charset.forName("windows-1252"))
    }.replace("\r\n", "\n")
}

/** Lecteur de documents : PDF page par page, textes défilants. Ouvert depuis Kairo Files ou toute appli. */
class DocumentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data ?: return finish()
        val type = intent.type ?: contentResolver.getType(uri)
        val name = displayName(uri)
        val pdf = type == "application/pdf" || name.lowercase().endsWith(".pdf")
        setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF101114))) {
                if (pdf) PdfView(uri, name) else TextView(uri, name)
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"

    @Composable
    private fun PdfView(uri: Uri, name: String) {
        var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var page by remember { mutableIntStateOf(0) }
        var zoom by remember { mutableStateOf(false) }
        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            val r = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openFileDescriptor(uri, "r")?.let(::PdfRenderer) }.getOrNull()
            }
            if (r == null) error = "PDF illisible (protégé par mot de passe ?)" else renderer = r
            delay(50)
            runCatching { focus.requestFocus() }
        }
        // Page rendue à la largeur de l'écran (un peu plus en zoom pour rester nette).
        val screenWidth = resources.displayMetrics.widthPixels
        LaunchedEffect(renderer, page, zoom) {
            val r = renderer ?: return@LaunchedEffect
            bitmap = withContext(Dispatchers.IO) {
                synchronized(r) {
                    r.openPage(page).use { p ->
                        val width = if (zoom) screenWidth else (screenWidth * 0.6f).toInt()
                        val height = (width.toFloat() * p.height / p.width).toInt()
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AColor.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.asImageBitmap()
                    }
                }
            }
            scroll.scrollTo(0)
        }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { renderer?.close() } }

        val count = renderer?.pageCount ?: 0
        Box(
            Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { if (page < count - 1) page++; true }
                    Key.DirectionLeft -> { if (page > 0) page--; true }
                    Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(400f) }; true }
                    Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-400f) }; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { zoom = !zoom; true }
                    else -> false
                }
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            error?.let { Text(it, color = Color.White, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center)) }
            bitmap?.let { bmp ->
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bmp, contentDescription = name, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(if (zoom) 1f else 0.6f))
                }
            }
            if (count > 0) {
                Text(
                    "$name · page ${page + 1} / $count   ← →  pages · ↑ ↓  défiler · OK  ${if (zoom) "page entière" else "zoom"}",
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun TextView(uri: Uri, name: String) {
        var lines by remember { mutableStateOf<List<String>?>(null) }
        var truncated by remember { mutableStateOf(false) }
        val state = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        var size by remember { mutableIntStateOf(22) }

        LaunchedEffect(Unit) {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(TEXT_MAX + 1)
                        var n = 0
                        while (n < buf.size) {
                            val r = input.read(buf, n, buf.size - n)
                            if (r < 0) break
                            n += r
                        }
                        buf.copyOf(n)
                    }
                }.getOrNull()
            }
            if (bytes == null) lines = listOf("Texte illisible") else {
                truncated = bytes.size > TEXT_MAX
                lines = decodeText(if (truncated) bytes.copyOf(TEXT_MAX) else bytes).split('\n')
            }
            delay(50)
            runCatching { focus.requestFocus() }
        }
        Column(
            Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { scope.launch { state.animateScrollBy(260f) }; true }
                    Key.DirectionUp -> { scope.launch { state.animateScrollBy(-260f) }; true }
                    Key.DirectionRight -> { scope.launch { state.animateScrollBy(900f) }; true }
                    Key.DirectionLeft -> { scope.launch { state.animateScrollBy(-900f) }; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { size = if (size >= 30) 18 else size + 4; true }
                    else -> false
                }
            }.padding(horizontal = 80.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$name   ↑ ↓  défiler · ← →  page · OK  taille du texte" + if (truncated) " · début du fichier (2 Mo)" else "",
                color = Color(0xFF9AA3B2), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp),
            )
            val list = lines
            if (list == null) Text("Lecture…", color = Color.White, fontSize = 20.sp)
            else LazyColumn(state = state, modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
                items(list) { line ->
                    Text(line.ifEmpty { " " }, color = Color(0xFFEDEFF3), fontSize = size.sp, lineHeight = (size * 1.45f).sp)
                }
            }
        }
    }
}
