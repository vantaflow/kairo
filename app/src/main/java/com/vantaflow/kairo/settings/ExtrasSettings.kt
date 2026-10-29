package com.vantaflow.kairo.settings

import com.vantaflow.kairo.home.installLabel
import com.vantaflow.kairo.apps.InstallState
import com.vantaflow.kairo.apps.RemoteApk
import com.vantaflow.kairo.apps.OurApps
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
import com.vantaflow.kairo.BuildConfig
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.system.MemoryCleaner
import com.vantaflow.kairo.system.formatMb
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.update.Release
import com.vantaflow.kairo.update.Updater
import com.vantaflow.kairo.update.isNewer
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
        item { SettingLabel("Nouveau profil") }
        item { TvInput(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Nom du profil (OK pour saisir)") }
        item { SwitchRow("Profil enfant", kids, { kids = it }, "Ne voit que ses favoris ; réglages protégés par le PIN.") }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvInput(
                    pin,
                    { pin = it.filter(Char::isDigit).take(6) },
                    Modifier.weight(1f),
                    secret = true,
                    placeholder = if (kids) "PIN, 4 à 6 chiffres" else "PIN (facultatif)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                )
                // Un nom est requis ; un profil enfant exige un PIN d'au moins 4 chiffres.
                SmallButton(
                    "Créer",
                    {
                        // Valeurs lues avant de vider le formulaire (la coroutine s'exécute après).
                        val newName = name.trim()
                        val newKids = kids
                        val newPin = pin.ifBlank { null }
                        scope.launch { c.prefs.addProfile(newName, newKids, newPin) }
                        name = ""; pin = ""; kids = false
                    },
                    enabled = name.isNotBlank() && (!kids || pin.length >= 4),
                )
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
                        // Fichier déposé par ADB (autre propriétaire) : on le remplace au lieu de l'écraser.
                        runCatching { file.delete(); file.writeText(c.prefs.exportJson()); "Sauvegarde enregistrée." }.getOrElse { "Échec : ${it.message}" }
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
        Text(
            "${file.absolutePath}\nadb pull ${file.absolutePath}\nadb push tvhome-backup.json ${file.absolutePath}",
            fontFamily = FontFamily.Monospace,
            color = OnBackground,
        )
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
        OurAppsSection()
    }
}

/** Nos applis : version installée, version publiée, installation ou mise à jour en un clic. */
@Composable
private fun OurAppsSection() {
    val store = container().ourApps
    val states by store.states.collectAsStateWithLifecycle()
    var remote by remember { mutableStateOf<Map<String, RemoteApk?>>(emptyMap()) }
    LaunchedEffect(Unit) { remote = OurApps.associate { it.id to store.latest(it) } }
    Text("Nos applications", color = OnBackground, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
    OurApps.forEach { app ->
        val installed = store.installedVersion(app)
        val r = remote[app.id]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(app.name, color = OnBackground)
                Text(
                    when {
                        installed == null -> "Non installée" + (r?.let { " · ${it.version} disponible" } ?: "")
                        store.hasUpdate(app, r) -> "Version $installed · ${r!!.version} disponible"
                        else -> "Version $installed · à jour"
                    },
                    color = OnBackgroundMuted,
                    fontSize = 14.sp,
                )
                states[app.id]?.takeIf { it != InstallState.Idle }?.let { Text(installLabel(it), color = OnBackgroundMuted, fontSize = 14.sp) }
            }
            when {
                installed == null -> SmallButton("Installer", { store.install(app) })
                store.hasUpdate(app, r) -> SmallButton("Mettre à jour", { store.install(app) })
            }
        }
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
        AutoCleanSettings()
    }
}

/** Nettoyage automatique : interrupteur et délai avant de fermer une appli inutilisée. */
@Composable
fun AutoCleanSettings() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as com.vantaflow.kairo.TvHomeApp).container.prefs
    val scope = rememberCoroutineScope()
    val on by prefs.autoClean.collectAsStateWithLifecycle(true)
    val idle by prefs.idleCloseMin.collectAsStateWithLifecycle(30)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow(
            "Nettoyage automatique", on, { v -> scope.launch { prefs.setAutoClean(v) } },
            "Mémoire basse (moins de 25 % libre) : les applis en arrière-plan sont fermées, sauf la dernière utilisée.",
        )
        if (on) {
            SettingLabel("Fermer une appli inutilisée depuis")
            ChoiceRow {
                com.vantaflow.kairo.system.IdleCloseChoices.forEach { (min, label) ->
                    ChoiceChip(idle == min, { scope.launch { prefs.setIdleCloseMin(min) } }) { Text(label) }
                }
            }
        }
    }
}
