package com.vantaflow.kairo.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF0F1115)
private val Panel = Color(0xFF181B22)
private val Card = Color(0xFF20242D)
private val Accent = Color(0xFF4DA6FF)
private val TextMain = Color(0xFFEDEFF3)
private val TextMuted = Color(0xFF9AA3B2)
private val Danger = Color(0xFFFF6B6B)

private const val AUTHORITY = "com.vantaflow.kairo.files.provider"
private const val SEND_PACKAGE = "com.vantaflow.kairo.send"
private const val PLAYER_PACKAGE = "com.vantaflow.kairo.player"
private const val PKG = "com.vantaflow.kairo.files"

/** Un emplacement de la colonne de gauche : la box, une clé USB, un dossier courant. */
data class Place(val label: String, val detail: String, val dir: File, val icon: String)

/** Fichiers copiés ou coupés, en attente de « Coller ici ». */
data class Clip(val files: List<File>, val cut: Boolean)

/** Copie, déplacement ou suppression en cours. */
data class Progress(val label: String, val done: Long, val total: Long)

/** Question posée au clavier : renommer, nouveau dossier. */
data class Ask(val title: String, val initial: String, val action: String, val onOk: (String) -> Unit)

class MainActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FilesApp(granted, resumes) }
    }

    override fun onResume() {
        super.onResume()
        granted = Environment.isExternalStorageManager()
        resumes++
    }
}

/** Emplacements : stockage de la box et clés USB branchées, puis les dossiers courants. */
fun places(context: Context): List<Place> {
    val sm = context.getSystemService(StorageManager::class.java)
    val volumes = sm?.storageVolumes.orEmpty().mapNotNull { v ->
        val dir = v.directory ?: return@mapNotNull null
        val free = formatSize(dir.freeSpace)
        val total = formatSize(dir.totalSpace)
        if (v.isPrimary) Place("Stockage de la box", "$free libres sur $total", dir, "📺")
        else Place(v.getDescription(context) ?: "Clé USB", "$free libres sur $total", dir, "💾")
    }
    val root = Environment.getExternalStorageDirectory()
    val shortcuts = listOf(
        Triple("Téléchargements", Environment.DIRECTORY_DOWNLOADS, "📥"),
        Triple("Films", Environment.DIRECTORY_MOVIES, "🎬"),
        Triple("Musique", Environment.DIRECTORY_MUSIC, "🎵"),
        Triple("Images", Environment.DIRECTORY_PICTURES, "🖼"),
    ).mapNotNull { (label, name, icon) ->
        File(root, name).takeIf { it.isDirectory }?.let { Place(label, "Dossier de la box", it, icon) }
    }
    return volumes + shortcuts
}

/** Ouvre un fichier dans l'appli qui sait le lire (lecteur, visionneuse, installateur d'APK). */
fun openFile(context: Context, file: File, chooser: Boolean): String? {
    val uri = runCatching { FileProvider.getUriForFile(context, AUTHORITY, file) }.getOrElse { return "Fichier inaccessible" }
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    // Vidéos, musiques, PDF et textes : directement dans Kairo Player s'il est installé.
    if (!chooser && opensInPlayer(file.name)) {
        val inPlayer = Intent(view).setPackage(PLAYER_PACKAGE)
        if (runCatching { context.startActivity(inPlayer) }.isSuccess) return null
    }
    return try {
        context.startActivity(if (chooser) Intent.createChooser(view, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else view)
        null
    } catch (e: ActivityNotFoundException) {
        "Aucune appli ne sait ouvrir ce fichier"
    }
}

/** Envoie des fichiers à Kairo Send Files, qui les propose au téléphone. */
fun sendToPhone(context: Context, files: List<File>): String? {
    val uris = ArrayList(files.filter { it.isFile }.map { FileProvider.getUriForFile(context, AUTHORITY, it) })
    if (uris.isEmpty()) return "Choisissez un fichier (pas un dossier)"
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
        .setPackage(SEND_PACKAGE).setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (uris.size == 1) intent.putExtra(Intent.EXTRA_STREAM, uris[0]) else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "Installez Kairo Send Files pour envoyer au téléphone"
    }
}

@Composable
fun FilesApp(granted: Boolean, resumes: Int) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (granted) Browser(resumes) else PermissionScreen()
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(80.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Kairo Files", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        Spacer(Modifier.height(12.dp))
        Text(
            "Pour parcourir la box et les clés USB, autorisez l'accès à tous les fichiers.",
            fontSize = 20.sp, color = TextMuted,
        )
        Spacer(Modifier.height(28.dp))
        Pill("Autoriser", Modifier.focusRequester(focus)) {
            val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$PKG"))
            val all = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(all) }
        }
        Spacer(Modifier.height(28.dp))
        Text("Si la box refuse, depuis un ordinateur :", fontSize = 15.sp, color = TextMuted)
        Text(
            "adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow",
            fontSize = 15.sp, color = TextMain, fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Browser(resumes: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var placeList by remember { mutableStateOf(emptyList<Place>()) }
    var current by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<Entry>?>(null) }
    var sort by remember { mutableStateOf(SortBy.Name) }
    var showHidden by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var confirmDelete by remember { mutableStateOf<Entry?>(null) }
    var info by remember { mutableStateOf<Pair<Entry, String>?>(null) }
    var archiveFor by remember { mutableStateOf<Entry?>(null) }
    var archiveContent by remember { mutableStateOf<Pair<Entry, List<ArchiveItem>>?>(null) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var cancel by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Élément à refocaliser après un chargement (dossier dont on revient, fichier créé…).
    var focusName by remember { mutableStateOf<String?>(null) }
    // Une fenêtre se ferme : le focus revient sur l'élément de la liste (pas sur la colonne de gauche).
    var refocus by remember { mutableIntStateOf(0) }
    val dialogOpen = menuFor != null || archiveFor != null || archiveContent != null || ask != null ||
        confirmDelete != null || info != null || progress != null
    LaunchedEffect(dialogOpen) { if (!dialogOpen) refocus++ }

    // Clés USB branchées ou retirées : la liste se relit à chaque retour dans l'appli.
    LaunchedEffect(resumes, reload) {
        placeList = withContext(Dispatchers.IO) { places(context) }
        if (current == null) current = placeList.firstOrNull()?.dir
        if (current?.exists() == false) current = placeList.firstOrNull()?.dir
    }
    LaunchedEffect(current, sort, showHidden, reload, resumes) {
        val dir = current ?: return@LaunchedEffect
        val raw = withContext(Dispatchers.IO) { dir.listFiles()?.map(::entryOf) }
        entries = raw?.let { sortEntries(it, sort, showHidden) }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            message = null
        }
    }

    fun go(dir: File) {
        focusName = null
        entries = null
        current = dir
    }

    val root = placeList.firstOrNull { p -> current?.absolutePath?.let { it == p.dir.absolutePath || it.startsWith(p.dir.absolutePath + "/") } == true && !p.detail.startsWith("Dossier") }
    val atRoot = current == null || root == null || current?.absolutePath == root.dir.absolutePath

    fun runJob(label: String, total: Long, job: suspend ((Long) -> Unit, () -> Boolean) -> Unit) {
        cancel = false
        progress = Progress(label, 0, total)
        scope.launch {
            var done = 0L
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    job({ n ->
                        done += n
                        progress = Progress(label, done, total)
                    }, { cancel })
                }
            }
            progress = null
            message = when {
                result.isSuccess -> null
                result.exceptionOrNull() is InterruptedException -> "Annulé"
                result.exceptionOrNull() is ArchiveException -> result.exceptionOrNull()?.message
                else -> "Échec : ${result.exceptionOrNull()?.message ?: "erreur"}"
            }
            reload++
        }
    }

    fun paste() {
        val c = clip ?: return
        val dest = current ?: return
        if (c.files.any { isInside(dest, it) }) {
            message = "Impossible de coller un dossier dans lui-même"
            return
        }
        scope.launch {
            val total = withContext(Dispatchers.IO) { c.files.sumOf { totalSize(it) } }
            if (!c.cut && dest.usableSpace < total) {
                message = "Pas assez de place : ${formatSize(total)} nécessaires, ${formatSize(dest.usableSpace)} libres"
                return@launch
            }
            focusName = c.files.firstOrNull()?.name
            runJob(if (c.cut) "Déplacement" else "Copie", total) { onBytes, cancelled ->
                c.files.forEach { f -> if (c.cut) moveInto(f, dest, onBytes, cancelled) else copyInto(f, dest, onBytes, cancelled) }
            }
            if (c.cut) clip = null
        }
    }

    BackHandler(enabled = menuFor == null && archiveFor == null && archiveContent == null && ask == null && confirmDelete == null && info == null && progress == null && !atRoot) {
        val dir = current ?: return@BackHandler
        focusName = dir.name
        entries = null
        current = dir.parentFile
    }

    Row(Modifier.fillMaxSize()) {
        // Emplacements
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Panel).padding(vertical = 32.dp, horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Kairo Files", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextMain, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
            placeList.forEach { p ->
                val selected = current?.absolutePath?.startsWith(p.dir.absolutePath) == true && (p == root || p.detail.startsWith("Dossier") && current?.absolutePath == p.dir.absolutePath)
                Row2(p.icon, p.label, p.detail, selected = selected, onClick = { go(p.dir) })
            }
        }
        // Dossier courant
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 48.dp, top = 32.dp)) {
            val path = current?.let { dir -> root?.let { r -> r.label + dir.absolutePath.removePrefix(r.dir.absolutePath).replace("/", " › ") } ?: dir.absolutePath } ?: ""
            Text(path, fontSize = 18.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                clip?.let { c ->
                    Pill(if (c.cut) "Déplacer ici (${c.files.size})" else "Coller ici (${c.files.size})", accent = true) { paste() }
                    Pill("Vider") { clip = null }
                }
                Pill("Nouveau dossier") {
                    val dir = current ?: return@Pill
                    ask = Ask("Nouveau dossier", "", "Créer") { name ->
                        val target = File(dir, name.trim())
                        when {
                            !validName(name) -> message = "Nom invalide"
                            target.exists() -> message = "« ${name.trim()} » existe déjà"
                            !target.mkdir() -> message = "Création impossible ici"
                            else -> { focusName = target.name; reload++ }
                        }
                    }
                }
                Pill("Tri : ${sort.label}") { sort = SortBy.entries[(sort.ordinal + 1) % SortBy.entries.size] }
                Pill(if (showHidden) "Masquer les cachés" else "Voir les cachés") { showHidden = !showHidden }
            }
            Spacer(Modifier.height(18.dp))
            val list = entries
            when {
                list == null -> Text("Lecture…", color = TextMuted, fontSize = 18.sp)
                list.isEmpty() -> Text("Dossier vide", color = TextMuted, fontSize = 18.sp)
                else -> EntryList(
                    list, focusName, refocus,
                    onFocused = { focusName = it.name },
                    onOpen = { e ->
                        val file = File(e.path)
                        when {
                            e.directory -> go(file)
                            isArchive(e.name) -> archiveFor = e
                            else -> openFile(context, file, chooser = false)?.let { message = it }
                        }
                    },
                    onMenu = { menuFor = it },
                )
            }
        }
    }

    // Actions sur un élément (appui long ou touche Menu)
    menuFor?.let { e ->
        val file = File(e.path)
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Ouvrir" to { if (e.directory) go(file) else openFile(context, file, false)?.let { message = it } })
            if (isArchive(e.name)) add("Extraire…" to { archiveFor = e })
            if (!e.directory) add("Ouvrir avec…" to { openFile(context, file, true)?.let { message = it } })
            if (!e.directory) add("Envoyer au téléphone (Kairo Send Files)" to { sendToPhone(context, listOf(file))?.let { message = it } })
            add("Copier" to { clip = Clip(listOf(file), cut = false); message = "Choisissez le dossier, puis « Coller ici »" })
            add("Couper" to { clip = Clip(listOf(file), cut = true); message = "Choisissez le dossier, puis « Déplacer ici »" })
            add("Renommer" to {
                ask = Ask("Renommer", e.name, "Renommer") { name ->
                    val target = File(file.parentFile, name.trim())
                    when {
                        !validName(name) -> message = "Nom invalide"
                        name.trim() == e.name -> Unit
                        target.exists() -> message = "« ${name.trim()} » existe déjà"
                        !file.renameTo(target) -> message = "Renommage impossible"
                        else -> { focusName = target.name; reload++ }
                    }
                }
            })
            add("Supprimer" to { confirmDelete = e })
            add("Propriétés" to {
                scope.launch {
                    val size = withContext(Dispatchers.IO) { totalSize(file) }
                    val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(e.modified))
                    info = e to buildString {
                        appendLine(e.path)
                        appendLine("Taille : ${formatSize(size)}")
                        e.children?.let { appendLine("Contenu : $it élément${if (it > 1) "s" else ""}") }
                        append("Modifié le $date")
                    }
                }
            })
        }
        Dialog(e.name, onDismiss = { menuFor = null }) {
            actions.forEachIndexed { i, (label, run) ->
                MenuButton(label, first = i == 0, danger = label == "Supprimer") {
                    menuFor = null
                    run()
                }
            }
        }
    }

    fun extract(e: Entry, into: File) {
        val file = File(e.path)
        focusName = into.name.takeIf { into != current } ?: focusName
        scope.launch {
            val total = withContext(Dispatchers.IO) { archiveSize(file) }
            if (total > 0 && into.parentFile?.usableSpace?.let { it < total } == true) {
                message = "Pas assez de place : ${formatSize(total)} nécessaires"
                return@launch
            }
            runJob("Extraction", total) { onBytes, cancelled -> extractArchive(file, into, onBytes, cancelled) }
        }
    }

    archiveFor?.let { e ->
        val file = File(e.path)
        val dir = file.parentFile ?: return@let
        val folder = archiveBaseName(e.name)
        Dialog(e.name, onDismiss = { archiveFor = null }) {
            MenuButton("Extraire dans « $folder »", first = true) {
                archiveFor = null
                extract(e, File(dir, freeName(dir, folder)))
            }
            MenuButton("Extraire ici") { archiveFor = null; extract(e, dir) }
            MenuButton("Voir le contenu") {
                archiveFor = null
                scope.launch {
                    val items = withContext(Dispatchers.IO) { runCatching { listArchive(file) } }
                    items.onSuccess { archiveContent = e to it }.onFailure { message = it.message ?: "Archive illisible" }
                }
            }
            MenuButton("Ouvrir avec…") { archiveFor = null; openFile(context, file, true)?.let { message = it } }
        }
    }

    archiveContent?.let { (e, items) ->
        Dialog("${e.name} · ${items.count { !it.directory }} fichier${if (items.count { !it.directory } > 1) "s" else ""}", onDismiss = { archiveContent = null }) {
            LazyColumn(Modifier.height(320.dp)) {
                itemsIndexed(items.filterNot { it.directory }.take(500)) { i, item ->
                    MenuButton(
                        item.name + if (item.size >= 0) "  ·  ${formatSize(item.size)}" else "",
                        first = i == 0,
                    ) {}
                }
            }
            Spacer(Modifier.height(10.dp))
            MenuButton("Extraire…") { archiveContent = null; archiveFor = e }
        }
    }

    confirmDelete?.let { e ->
        Dialog("Supprimer « ${e.name} » ?", onDismiss = { confirmDelete = null }) {
            Text(
                if (e.directory) "Le dossier et tout son contenu seront supprimés définitivement." else "Le fichier sera supprimé définitivement.",
                color = TextMuted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
            MenuButton("Annuler", first = true) { confirmDelete = null }
            MenuButton("Supprimer", danger = true) {
                confirmDelete = null
                val file = File(e.path)
                runJob("Suppression", 0) { _, _ ->
                    if (!file.deleteRecursively()) throw java.io.IOException("suppression impossible")
                }
                clip = clip?.let { c -> c.copy(files = c.files.filterNot { it == file }).takeIf { it.files.isNotEmpty() } }
            }
        }
    }

    info?.let { (e, text) ->
        Dialog(e.name, onDismiss = { info = null }) {
            Text(text, color = TextMain, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 12.dp))
            MenuButton("Fermer", first = true) { info = null }
        }
    }

    ask?.let { a ->
        AskDialog(a, onDismiss = { ask = null })
    }

    progress?.let { p ->
        Dialog(p.label, onDismiss = { cancel = true }) {
            val fraction = if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else 0f
            Text(
                if (p.total > 0) "${formatSize(p.done)} sur ${formatSize(p.total)} · ${(fraction * 100).toInt()} %" else "En cours…",
                color = TextMain, fontSize = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Card, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.height(16.dp))
            MenuButton("Annuler", first = true) { cancel = true }
        }
    }

    message?.let { m ->
        Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                m, color = TextMain, fontSize = 17.sp,
                modifier = Modifier.background(Color(0xEE2A2F3A), RoundedCornerShape(12.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EntryList(
    list: List<Entry>,
    focusName: String?,
    refocus: Int,
    onFocused: (Entry) -> Unit,
    onOpen: (Entry) -> Unit,
    onMenu: (Entry) -> Unit,
) {
    val state = rememberLazyListState()
    val requester = remember { FocusRequester() }
    val target = list.indexOfFirst { it.name == focusName }.takeIf { it >= 0 } ?: 0
    // Après un chargement ou une fenêtre fermée : le focus revient sur l'élément choisi, sinon le premier.
    LaunchedEffect(list, refocus) {
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!visible) state.scrollToItem((target - 3).coerceAtLeast(0))
        repeat(10) {
            delay(30)
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        itemsIndexed(list, key = { _, e -> e.path }) { i, e ->
            val detail = when {
                e.directory -> e.children?.let { "$it élément${if (it > 1) "s" else ""}" } ?: "Dossier"
                else -> formatSize(e.size)
            }
            Row2(
                e.kind.icon, e.name, detail,
                modifier = (if (i == target) Modifier.focusRequester(requester) else Modifier)
                    .onPreviewKeyEvent { k ->
                        if (k.key == Key.Menu && k.type == KeyEventType.KeyUp) { onMenu(e); true } else false
                    },
                trailing = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.modified)),
                tint = Color(e.kind.color),
                onFocus = { onFocused(e) },
                onLongClick = { onMenu(e) },
                onClick = { onOpen(e) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Row2(
    icon: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: String? = null,
    tint: Color = Accent,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Card else Color.Transparent,
            focusedContainerColor = Color(0xFF2C3340),
            pressedContainerColor = Color(0xFF2C3340),
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent))),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = TextMuted, fontSize = 13.sp, maxLines = 1)
            }
            trailing?.let { Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) Accent.copy(alpha = 0.25f) else Card,
            focusedContainerColor = Color.White,
            contentColor = TextMain,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
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

/** Fenêtre centrée par-dessus l'écran ; Retour la ferme. */
@Composable
private fun Dialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Panel, RoundedCornerShape(18.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun AskDialog(a: Ask, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(a.initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            delay(30)
            if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    fun submit() {
        onDismiss()
        a.onOk(text)
    }
    Dialog(a.title, onDismiss) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 19.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(field)
                .background(Card, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        MenuButton(a.action) { submit() }
        MenuButton("Annuler") { onDismiss() }
    }
}
