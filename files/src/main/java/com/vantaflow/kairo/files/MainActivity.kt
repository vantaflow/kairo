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
import androidx.compose.ui.res.stringResource
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
import java.util.Locale

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

/** Un emplacement de la colonne de gauche : la box, une clé USB, un dossier courant ([shortcut]). */
data class Place(val label: String, val detail: String, val dir: File, val icon: String, val shortcut: Boolean = false)

/** Fichiers copiés ou coupés, en attente de « Coller ici ». */
data class Clip(val files: List<File>, val cut: Boolean)

/** Copie, déplacement ou suppression en cours. */
data class Progress(val label: String, val done: Long, val total: Long)

/** Question posée au clavier : renommer, nouveau dossier. */
data class Ask(val title: String, val initial: String, val action: String, val onOk: (String) -> Unit)

class MainActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var resumes by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        BoxLocale.applyTo(this)
    }

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
        val detail = context.getString(R.string.place_free, context.sizeText(dir.freeSpace), context.sizeText(dir.totalSpace))
        if (v.isPrimary) Place(context.getString(R.string.place_box), detail, dir, "📺")
        else Place(v.getDescription(context) ?: context.getString(R.string.place_usb), detail, dir, "💾")
    }
    val root = Environment.getExternalStorageDirectory()
    val shortcuts = listOf(
        Triple(R.string.place_downloads, Environment.DIRECTORY_DOWNLOADS, "📥"),
        Triple(R.string.place_movies, Environment.DIRECTORY_MOVIES, "🎬"),
        Triple(R.string.place_music, Environment.DIRECTORY_MUSIC, "🎵"),
        Triple(R.string.place_pictures, Environment.DIRECTORY_PICTURES, "🖼"),
    ).mapNotNull { (label, name, icon) ->
        File(root, name).takeIf { it.isDirectory }?.let {
            Place(context.getString(label), context.getString(R.string.place_box_folder), it, icon, shortcut = true)
        }
    }
    return volumes + shortcuts
}

/** Taille dans la langue de la box (o, Ko, Mo… en français ; B, KB, MB… en anglais). */
fun Context.sizeText(bytes: Long): String = formatSize(
    bytes,
    listOf(R.string.size_bytes, R.string.size_kb, R.string.size_mb, R.string.size_gb, R.string.size_tb).map(::getString),
    Locale.getDefault(),
)

/** Raison d'un échec d'archive, dans la langue de la box. */
fun Context.archiveError(e: ArchiveException): String = getString(
    when (e.reason) {
        ArchiveException.Reason.Rar5 -> R.string.archive_rar5
        ArchiveException.Reason.Password -> R.string.archive_password
        ArchiveException.Reason.UnreadableRar -> R.string.archive_rar_unreadable
        ArchiveException.Reason.UnknownFormat -> R.string.archive_unknown_format
    },
)

private fun sortLabel(sort: SortBy): Int = when (sort) {
    SortBy.Name -> R.string.sort_name
    SortBy.Date -> R.string.sort_date
    SortBy.Size -> R.string.sort_size
}

/** Ouvre un fichier dans l'appli qui sait le lire (lecteur, visionneuse, installateur d'APK). */
fun openFile(context: Context, file: File, chooser: Boolean): String? {
    val uri = runCatching { FileProvider.getUriForFile(context, AUTHORITY, file) }.getOrElse { return context.getString(R.string.file_inaccessible) }
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    // Vidéos, musiques, PDF et textes : directement dans Kairo Player s'il est installé.
    if (!chooser && opensInPlayer(file.name)) {
        val inPlayer = Intent(view).setPackage(PLAYER_PACKAGE)
        if (runCatching { context.startActivity(inPlayer) }.isSuccess) return null
    }
    return try {
        context.startActivity(if (chooser) Intent.createChooser(view, context.getString(R.string.open_with)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else view)
        null
    } catch (e: ActivityNotFoundException) {
        context.getString(R.string.no_app_to_open)
    }
}

/** Envoie des fichiers à Kairo Send Files, qui les propose au téléphone. */
fun sendToPhone(context: Context, files: List<File>): String? {
    val uris = ArrayList(files.filter { it.isFile }.map { FileProvider.getUriForFile(context, AUTHORITY, it) })
    if (uris.isEmpty()) return context.getString(R.string.choose_a_file)
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
        .setPackage(SEND_PACKAGE).setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (uris.size == 1) intent.putExtra(Intent.EXTRA_STREAM, uris[0]) else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        context.getString(R.string.install_send)
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
        Text(stringResource(R.string.app_name), fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.permission_text),
            fontSize = 20.sp, color = TextMuted,
        )
        Spacer(Modifier.height(28.dp))
        Pill(stringResource(R.string.permission_allow), Modifier.focusRequester(focus)) {
            val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$PKG"))
            val all = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(all) }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.permission_adb), fontSize = 15.sp, color = TextMuted)
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

    val root = placeList.firstOrNull { p -> current?.absolutePath?.let { it == p.dir.absolutePath || it.startsWith(p.dir.absolutePath + "/") } == true && !p.shortcut }
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
                result.exceptionOrNull() is InterruptedException -> context.getString(R.string.cancelled)
                result.exceptionOrNull() is ArchiveException -> context.archiveError(result.exceptionOrNull() as ArchiveException)
                result.exceptionOrNull() is CreateDirException ->
                    context.getString(R.string.cannot_create, (result.exceptionOrNull() as CreateDirException).dirName)
                else -> context.getString(R.string.failed, result.exceptionOrNull()?.message ?: context.getString(R.string.error_generic))
            }
            reload++
        }
    }

    fun paste() {
        val c = clip ?: return
        val dest = current ?: return
        if (c.files.any { isInside(dest, it) }) {
            message = context.getString(R.string.paste_into_itself)
            return
        }
        scope.launch {
            val total = withContext(Dispatchers.IO) { c.files.sumOf { totalSize(it) } }
            if (!c.cut && dest.usableSpace < total) {
                message = context.getString(R.string.not_enough_space, context.sizeText(total), context.sizeText(dest.usableSpace))
                return@launch
            }
            focusName = c.files.firstOrNull()?.name
            runJob(context.getString(if (c.cut) R.string.job_move else R.string.job_copy), total) { onBytes, cancelled ->
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
            Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextMain, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
            placeList.forEach { p ->
                val selected = current?.absolutePath?.startsWith(p.dir.absolutePath) == true && (p == root || p.shortcut && current?.absolutePath == p.dir.absolutePath)
                Row2(p.icon, p.label, p.detail, selected = selected, onClick = { go(p.dir) })
            }
        }
        // Dossier courant
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 48.dp, top = 32.dp)) {
            val separator = stringResource(R.string.path_separator)
            val path = current?.let { dir -> root?.let { r -> r.label + dir.absolutePath.removePrefix(r.dir.absolutePath).replace("/", separator) } ?: dir.absolutePath } ?: ""
            Text(path, fontSize = 18.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                clip?.let { c ->
                    Pill(stringResource(if (c.cut) R.string.move_here else R.string.paste_here, c.files.size), accent = true) { paste() }
                    Pill(stringResource(R.string.clear_clipboard)) { clip = null }
                }
                Pill(stringResource(R.string.new_folder)) {
                    val dir = current ?: return@Pill
                    ask = Ask(context.getString(R.string.new_folder), "", context.getString(R.string.create)) { name ->
                        val target = File(dir, name.trim())
                        when {
                            !validName(name) -> message = context.getString(R.string.invalid_name)
                            target.exists() -> message = context.getString(R.string.already_exists, name.trim())
                            !target.mkdir() -> message = context.getString(R.string.create_failed)
                            else -> { focusName = target.name; reload++ }
                        }
                    }
                }
                Pill(stringResource(R.string.sort_by, stringResource(sortLabel(sort)))) { sort = SortBy.entries[(sort.ordinal + 1) % SortBy.entries.size] }
                Pill(stringResource(if (showHidden) R.string.hidden_hide else R.string.hidden_show)) { showHidden = !showHidden }
            }
            Spacer(Modifier.height(18.dp))
            val list = entries
            when {
                list == null -> Text(stringResource(R.string.reading), color = TextMuted, fontSize = 18.sp)
                list.isEmpty() -> Text(stringResource(R.string.folder_empty), color = TextMuted, fontSize = 18.sp)
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
        val deleteLabel = stringResource(R.string.delete)
        val actions = buildList<Pair<String, () -> Unit>> {
            add(context.getString(R.string.open) to { if (e.directory) go(file) else openFile(context, file, false)?.let { message = it } })
            if (isArchive(e.name)) add(context.getString(R.string.extract_menu) to { archiveFor = e })
            if (!e.directory) add(context.getString(R.string.open_with_menu) to { openFile(context, file, true)?.let { message = it } })
            if (!e.directory) add(context.getString(R.string.send_to_phone) to { sendToPhone(context, listOf(file))?.let { message = it } })
            add(context.getString(R.string.copy) to { clip = Clip(listOf(file), cut = false); message = context.getString(R.string.copy_hint) })
            add(context.getString(R.string.cut) to { clip = Clip(listOf(file), cut = true); message = context.getString(R.string.cut_hint) })
            add(context.getString(R.string.rename) to {
                ask = Ask(context.getString(R.string.rename), e.name, context.getString(R.string.rename)) { name ->
                    val target = File(file.parentFile, name.trim())
                    when {
                        !validName(name) -> message = context.getString(R.string.invalid_name)
                        name.trim() == e.name -> Unit
                        target.exists() -> message = context.getString(R.string.already_exists, name.trim())
                        !file.renameTo(target) -> message = context.getString(R.string.rename_failed)
                        else -> { focusName = target.name; reload++ }
                    }
                }
            })
            add(deleteLabel to { confirmDelete = e })
            add(context.getString(R.string.properties) to {
                scope.launch {
                    val size = withContext(Dispatchers.IO) { totalSize(file) }
                    val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(e.modified))
                    info = e to buildString {
                        appendLine(e.path)
                        appendLine(context.getString(R.string.properties_size, context.sizeText(size)))
                        e.children?.let {
                            appendLine(context.getString(R.string.properties_content, context.resources.getQuantityString(R.plurals.folder_items, it, it)))
                        }
                        append(context.getString(R.string.properties_modified, date))
                    }
                }
            })
        }
        Dialog(e.name, onDismiss = { menuFor = null }) {
            actions.forEachIndexed { i, (label, run) ->
                MenuButton(label, first = i == 0, danger = label == deleteLabel) {
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
                message = context.getString(R.string.not_enough_space_short, context.sizeText(total))
                return@launch
            }
            runJob(context.getString(R.string.job_extract), total) { onBytes, cancelled -> extractArchive(file, into, onBytes, cancelled) }
        }
    }

    archiveFor?.let { e ->
        val file = File(e.path)
        val dir = file.parentFile ?: return@let
        val folder = archiveBaseName(e.name)
        Dialog(e.name, onDismiss = { archiveFor = null }) {
            MenuButton(stringResource(R.string.extract_into, folder), first = true) {
                archiveFor = null
                extract(e, File(dir, freeName(dir, folder)))
            }
            MenuButton(stringResource(R.string.extract_here)) { archiveFor = null; extract(e, dir) }
            MenuButton(stringResource(R.string.archive_show_content)) {
                archiveFor = null
                scope.launch {
                    val items = withContext(Dispatchers.IO) { runCatching { listArchive(file) } }
                    items.onSuccess { archiveContent = e to it }.onFailure { message = (it as? ArchiveException)?.let(context::archiveError) ?: context.getString(R.string.archive_unreadable) }
                }
            }
            MenuButton(stringResource(R.string.open_with_menu)) { archiveFor = null; openFile(context, file, true)?.let { message = it } }
        }
    }

    archiveContent?.let { (e, items) ->
        val fileCount = items.count { !it.directory }
        Dialog(context.resources.getQuantityString(R.plurals.archive_files, fileCount, e.name, fileCount), onDismiss = { archiveContent = null }) {
            LazyColumn(Modifier.height(320.dp)) {
                itemsIndexed(items.filterNot { it.directory }.take(500)) { i, item ->
                    MenuButton(
                        item.name + if (item.size >= 0) "  ·  ${context.sizeText(item.size)}" else "",
                        first = i == 0,
                    ) {}
                }
            }
            Spacer(Modifier.height(10.dp))
            MenuButton(stringResource(R.string.extract_menu)) { archiveContent = null; archiveFor = e }
        }
    }

    confirmDelete?.let { e ->
        Dialog(stringResource(R.string.delete_confirm, e.name), onDismiss = { confirmDelete = null }) {
            Text(
                stringResource(if (e.directory) R.string.delete_folder_warning else R.string.delete_file_warning),
                color = TextMuted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
            MenuButton(stringResource(R.string.cancel), first = true) { confirmDelete = null }
            MenuButton(stringResource(R.string.delete), danger = true) {
                confirmDelete = null
                val file = File(e.path)
                val failed = context.getString(R.string.delete_failed)
                runJob(context.getString(R.string.job_delete), 0) { _, _ ->
                    if (!file.deleteRecursively()) throw java.io.IOException(failed)
                }
                clip = clip?.let { c -> c.copy(files = c.files.filterNot { it == file }).takeIf { it.files.isNotEmpty() } }
            }
        }
    }

    info?.let { (e, text) ->
        Dialog(e.name, onDismiss = { info = null }) {
            Text(text, color = TextMain, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 12.dp))
            MenuButton(stringResource(R.string.close), first = true) { info = null }
        }
    }

    ask?.let { a ->
        AskDialog(a, onDismiss = { ask = null })
    }

    progress?.let { p ->
        Dialog(p.label, onDismiss = { cancel = true }) {
            val fraction = if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else 0f
            Text(
                if (p.total > 0) stringResource(R.string.job_progress, context.sizeText(p.done), context.sizeText(p.total), (fraction * 100).toInt())
                else stringResource(R.string.job_running),
                color = TextMain, fontSize = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Card, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.height(16.dp))
            MenuButton(stringResource(R.string.cancel), first = true) { cancel = true }
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
    val context = LocalContext.current
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
                e.directory -> e.children?.let { context.resources.getQuantityString(R.plurals.folder_items, it, it) }
                    ?: stringResource(R.string.folder)
                else -> context.sizeText(e.size)
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
        MenuButton(stringResource(R.string.cancel)) { onDismiss() }
    }
}
