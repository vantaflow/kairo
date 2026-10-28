package com.tvhome.launcher.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.tvhome.launcher.BuildConfig
import com.tvhome.launcher.TvHomeApp
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.system.MemoryCleaner
import com.tvhome.launcher.system.formatMb
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted
import com.tvhome.launcher.update.Release
import com.tvhome.launcher.update.Updater
import com.tvhome.launcher.update.isNewer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Champ texte utilisable à la télécommande (le clavier Android s'ouvre au clic). */
@Composable
fun TvTextField(value: String, onChange: (String) -> Unit, width: Int = 360, secret: Boolean = false) {
    TvInput(value, onChange, Modifier.width(width.dp), secret = secret, fontSize = 16.sp)
}

@Composable
private fun container() = (LocalContext.current.applicationContext as TvHomeApp).container

/** Profils : liste, changement, création (enfant + PIN), suppression. */
@Composable
fun ProfileSettings() {
    val c = container()
    val scope = rememberCoroutineScope()
    val profiles by c.prefs.profiles.collectAsStateWithLifecycle(listOf(DefaultProfile))
    val current by c.prefs.currentProfile.collectAsStateWithLifecycle(DefaultProfile)
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Profil actif : ${current.name}", color = OnBackground, fontSize = 20.sp) }
        items(profiles, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    p.name + if (p.kids) " (enfant)" else "",
                    color = OnBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SmallButton("Utiliser", { scope.launch { c.prefs.switchProfile(p.id) } }, enabled = p.id != current.id)
                if (p.id != DEFAULT_PROFILE_ID) {
                    SmallButton("Supprimer", { scope.launch { c.prefs.removeProfile(p.id) } })
                }
            }
        }
        item { Text("Nouveau profil", color = OnBackgroundMuted, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nom", color = OnBackground)
                TvTextField(name, { name = it }, width = 240)
                Text("Enfant", color = OnBackground)
                Switch(checked = kids, onCheckedChange = { kids = it })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (kids) "PIN obligatoire (4 à 6 chiffres)" else "PIN (facultatif)", color = OnBackground)
                TvTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, width = 140, secret = true)
                // Un profil enfant exige un PIN d'au moins 4 chiffres.
                Button(
                    onClick = {
                        scope.launch { c.prefs.addProfile(name, kids, pin.ifBlank { null }) }
                        name = ""; pin = ""; kids = false
                    },
                    enabled = !kids || pin.length >= 4,
                ) { Text("Créer") }
            }
        }
        item {
            Text(
                "Chaque profil a ses favoris, son fond et ses applis masquées. Un profil enfant ne voit que ses favoris ; " +
                    "les réglages demandent son PIN.",
                color = OnBackgroundMuted,
            )
        }
    }
}

/** Sauvegarde et restauration des réglages dans un fichier récupérable par ADB. */
@Composable
fun BackupSettings() {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val file = remember { File(context.getExternalFilesDir(null) ?: context.filesDir, "tvhome-backup.json") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Sauvegarder favoris, thème, touches, profils… dans un fichier.", color = OnBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                scope.launch {
                    message = withContext(Dispatchers.IO) {
                        runCatching { file.writeText(c.prefs.exportJson()); "Sauvegarde enregistrée." }.getOrElse { "Échec : ${it.message}" }
                    }
                }
            }) { Text("Sauvegarder") }
            OutlinedButton(onClick = {
                scope.launch {
                    message = withContext(Dispatchers.IO) {
                        val json = runCatching { file.readText() }.getOrNull()
                        when {
                            json == null -> "Aucune sauvegarde trouvée."
                            c.prefs.importJson(json) -> "Réglages restaurés."
                            else -> "Fichier de sauvegarde invalide."
                        }
                    }
                }
            }) { Text("Restaurer") }
        }
        message?.let { Text(it, color = OnBackgroundMuted) }
        Text("Fichier sur la box :", color = OnBackgroundMuted)
        SelectionContainer {
            Text(
                "${file.absolutePath}\nadb pull ${file.absolutePath}\nadb push tvhome-backup.json ${file.absolutePath}",
                fontFamily = FontFamily.Monospace,
                color = OnBackground,
            )
        }
    }
}

/** Mise à jour depuis les versions publiées sur GitHub. */
@Composable
fun UpdateSettings() {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Dépôt public : le jeton n'est plus nécessaire (gardé s'il avait été enregistré).
    val token by c.prefs.githubToken.collectAsStateWithLifecycle("")
    var release by remember { mutableStateOf<Release?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val updater = remember { Updater(context) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Version installée : ${BuildConfig.VERSION_NAME}", color = OnBackground, fontSize = 20.sp)
        Button(onClick = {
            scope.launch {
                message = "Recherche…"
                val latest = withContext(Dispatchers.IO) { updater.latest(token) }
                release = latest?.takeIf { isNewer(it.tag, BuildConfig.VERSION_NAME) }
                message = when {
                    latest == null -> "Impossible de joindre GitHub (vérifiez le réseau)."
                    release == null -> "Kairo est à jour (${latest.tag})."
                    else -> "Nouvelle version ${latest.tag} disponible."
                }
            }
        }) { Text("Rechercher une mise à jour") }
        message?.let { Text(it, color = OnBackgroundMuted) }
        release?.let { r ->
            if (r.notes.isNotBlank()) Text(r.notes, color = OnBackgroundMuted)
            Button(onClick = {
                scope.launch {
                    message = "Téléchargement…"
                    val ok = withContext(Dispatchers.IO) { runCatching { updater.install(r, token) }.getOrDefault(false) }
                    message = if (ok) "Confirmez l'installation dans la fenêtre d'Android." else "Échec du téléchargement."
                }
            }) { Text("Installer ${r.tag}") }
        }
        Text(
            "Les versions sont publiées sur github.com/vantaflow/kairo. Sur une autre box, installez Kairo avec le code Downloader 5568887.",
            color = OnBackgroundMuted,
        )
    }
}

/** Mémoire : RAM disponible et fermeture des applis en arrière-plan. */
@Composable
fun MemorySettings(installedApps: List<AppInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleaner = remember { MemoryCleaner(context) }
    var state by remember { mutableStateOf(cleaner.state()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { state = cleaner.state() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Mémoire libre : ${formatMb(state.availableBytes)} sur ${formatMb(state.totalBytes)}",
            color = OnBackground,
            fontSize = 20.sp,
        )
        Button(onClick = {
            scope.launch {
                val before = state.availableBytes
                val count = withContext(Dispatchers.IO) { cleaner.clean(installedApps.map { it.packageName }) }
                kotlinx.coroutines.delay(1_500)
                state = cleaner.state()
                val freed = (state.availableBytes - before).coerceAtLeast(0)
                message = "$count applis fermées, ${formatMb(freed)} libérés."
            }
        }) { Text("Libérer la mémoire") }
        message?.let { Text(it, color = OnBackgroundMuted) }
        Text("Ferme les applis en arrière-plan ; elles redémarrent normalement à la prochaine ouverture.", color = OnBackgroundMuted)
    }
}
