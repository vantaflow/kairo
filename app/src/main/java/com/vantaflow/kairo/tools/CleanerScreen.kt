package com.vantaflow.kairo.tools

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.imageLoader
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.settings.SmallButton
import com.vantaflow.kairo.settings.openSafely
import com.vantaflow.kairo.system.MemoryCleaner
import com.vantaflow.kairo.system.formatMb
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.ui.theme.SurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Espace d'un support : libre et total. */
data class Space(val label: String, val free: Long, val total: Long) {
    val usedRatio get() = if (total <= 0) 0f else (1f - free.toFloat() / total).coerceIn(0f, 1f)
}

/** Place prise par une appli (appli + données + cache). */
data class AppSpace(val packageName: String, val label: String, val total: Long, val cache: Long)

fun dirSize(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf(::dirSize) ?: 0L

/** Stockage interne puis clés USB / cartes branchées. */
fun storageSpaces(context: Context): List<Space> {
    val internal = StatFs(Environment.getDataDirectory().path).let { Space("Stockage interne", it.availableBytes, it.totalBytes) }
    val external = context.getSystemService(StorageManager::class.java).storageVolumes
        .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        .mapNotNull { v -> v.directory?.let { d -> runCatching { StatFs(d.path) }.getOrNull()?.let { Space(v.getDescription(context), it.availableBytes, it.totalBytes) } } }
    return listOf(internal) + external
}

fun hasUsageAccess(context: Context): Boolean =
    context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

/** Les applis qui prennent le plus de place (nécessite l'accès aux données d'utilisation). */
fun heaviestApps(context: Context, apps: List<AppInfo>, count: Int = 8): List<AppSpace> {
    val stats = context.getSystemService(StorageStatsManager::class.java)
    return apps.mapNotNull { app ->
        runCatching {
            val s = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
            AppSpace(app.packageName, app.label, s.appBytes + s.dataBytes, s.cacheBytes)
        }.getOrNull()
    }.sortedByDescending { it.total }.take(count)
}

/** Nettoyeur : mémoire, stockage, cache de Kairo et applis les plus lourdes. */
@Composable
fun CleanerScreen(installedApps: List<AppInfo>) {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    val scope = rememberCoroutineScope()
    val cleaner = remember { MemoryCleaner(context) }
    var memory by remember { mutableStateOf(cleaner.state()) }
    var spaces by remember { mutableStateOf<List<Space>>(emptyList()) }
    var kairoCache by remember { mutableStateOf(0L) }
    var heavy by remember { mutableStateOf<List<AppSpace>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }

    LaunchedEffect(refresh) {
        memory = cleaner.state()
        withContext(Dispatchers.IO) {
            spaces = storageSpaces(context)
            kairoCache = dirSize(context.cacheDir)
            heavy = if (hasUsageAccess(context)) heaviestApps(context, installedApps) else null
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun optimize() = scope.launch {
        busy = true
        val before = cleaner.state().availableBytes
        val closed = withContext(Dispatchers.IO) {
            val n = cleaner.clean(installedApps.map { it.packageName })
            // Cache des images vidé par Coil lui-même (il garde son index cohérent).
            context.imageLoader.diskCache?.clear()
            context.cacheDir.listFiles()?.filter { it.name != "image_cache" }?.forEach { it.deleteRecursively() }
            n
        }
        container.tileImages.clear()
        context.imageLoader.memoryCache?.clear()
        delay(1_500)
        val freed = (cleaner.state().availableBytes - before).coerceAtLeast(0)
        message = "$closed applis fermées en arrière-plan, ${formatMb(freed)} de mémoire et ${formatMb(kairoCache)} de cache libérés."
        busy = false
        refresh++
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2E2A), Background)))
            .padding(start = 72.dp, end = 72.dp, top = 44.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Nettoyeur", fontSize = 40.sp, fontWeight = FontWeight.Medium, color = OnBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Gauge("Mémoire vive", memory.availableBytes, memory.totalBytes, Modifier.weight(1f))
            spaces.forEach { Gauge(it.label, it.free, it.total, Modifier.weight(1f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.focusRequester(first)) {
                SmallButton(if (busy) "Nettoyage…" else "Tout optimiser", ::optimize, enabled = !busy)
            }
            Text("Ferme les applis en arrière-plan et vide le cache de Kairo (${formatMb(kairoCache)}).", color = OnBackgroundMuted, fontSize = 18.sp)
        }
        message?.let { Text("✓ $it", color = AccentColor, fontSize = 18.sp) }
        Text("Applis les plus lourdes", fontSize = 22.sp, color = OnBackground)
        val list = heavy
        if (list == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Kairo a besoin de l'accès aux données d'utilisation pour mesurer chaque appli.", color = OnBackgroundMuted, fontSize = 18.sp)
                SmallButton("Autoriser", {
                    openSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                items(list, key = { it.packageName }) { app ->
                    AppSpaceRow(app) {
                        openSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    }
                }
            }
        }
    }
}

@Composable
private fun Gauge(label: String, free: Long, total: Long, modifier: Modifier) {
    val used = if (total <= 0) 0f else (1f - free.toFloat() / total).coerceIn(0f, 1f)
    val shown by animateFloatAsState(used, tween(900), label = "jauge")
    val color = when {
        used > 0.9f -> Color(0xFFFF6B6B)
        used > 0.75f -> Color(0xFFFFB74D)
        else -> Color(0xFF4DD0A1)
    }
    Column(modifier.background(SurfaceColor.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 18.sp, color = OnBackgroundMuted)
        Text("${formatMb(free)} libres", fontSize = 30.sp, color = OnBackground)
        Box(Modifier.fillMaxWidth().height(12.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(color, RoundedCornerShape(6.dp)))
        }
        Text("sur ${formatMb(total)} · ${(used * 100).toInt()} % utilisé", fontSize = 15.sp, color = OnBackgroundMuted)
    }
}

@Composable
private fun AppSpaceRow(app: AppSpace, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor.copy(alpha = 0.6f),
            contentColor = OnBackground,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF101216),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(app.label, fontSize = 19.sp, modifier = Modifier.weight(1f))
            Text(formatMb(app.total), fontSize = 19.sp, modifier = Modifier.width(140.dp))
            Text(if (app.cache > 0) "cache ${formatMb(app.cache)} · OK pour vider" else "", fontSize = 16.sp, modifier = Modifier.width(300.dp))
        }
    }
}
