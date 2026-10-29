package com.tvhome.launcher.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import com.tvhome.launcher.ui.TvIcon
import com.tvhome.launcher.ui.TvIconImage
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.network.NetState
import com.tvhome.launcher.network.NetworkMonitor
import com.tvhome.launcher.network.NetworkSettings
import com.tvhome.launcher.system.BoxSettings
import com.tvhome.launcher.ui.theme.Background
import com.tvhome.launcher.ui.theme.SurfaceColor
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.flow.Flow

enum class Group(val title: String) { App("Kairo"), Box("Paramètres de la box") }

enum class Section(val title: String, val group: Group, val icon: TvIcon, val description: String) {
    Setup("Assistant de configuration", Group.App, TvIcon.Check, "Ce qui est prêt et ce qu'il reste à activer."),
    Favorites("Favoris", Group.App, TvIcon.Favorites, "L'ordre et le contenu de la rangée Favoris."),
    Wallpaper("Fond d'écran", Group.App, TvIcon.Wallpaper, "Images Pixabay, dégradés ou couleur unie."),
    Theme("Thème", Group.App, TvIcon.Theme, "Couleurs, arrondi et zoom des tuiles."),
    Layout("Affichage de l'accueil", Group.App, TvIcon.Layout, "Ce qui apparaît sur l'écran d'accueil."),
    Weather("Météo", Group.App, TvIcon.Weather, "Météo affichée sous l'heure."),
    Reco("Recommandations", Group.App, TvIcon.Reco, "Rangées proposées par vos applis."),
    Clock("Heure", Group.App, TvIcon.Clock, "Format et synchronisation de l'heure."),
    Remote("Touches de la télécommande", Group.App, TvIcon.Remote, "YouTube, Netflix, Prime Video… : l'appli de votre choix."),
    Profiles("Profils", Group.App, TvIcon.Profiles, "Un accueil par personne, profil enfant protégé."),
    Backup("Sauvegarde", Group.App, TvIcon.Backup, "Sauvegarder et restaurer vos réglages."),
    Update("Mise à jour", Group.App, TvIcon.Update, "Installer la dernière version de Kairo."),
    Network("Réseau (Wi-Fi, Ethernet)", Group.Box, TvIcon.Network, "Connexion actuelle et réseaux Wi-Fi."),
    Memory("Mémoire", Group.Box, TvIcon.Memory, "Mémoire libre et fermeture des applis en arrière-plan."),
    Android("Paramètres Android", Group.Box, TvIcon.Android, "Raccourcis vers les paramètres de la box."),
}


/**
 * Réglages en panneau latéral à droite, par-dessus l'accueil (inspiré de Projectivy) :
 * les changements s'appliquent en direct derrière. Menu des sections → OK ouvre la section,
 * Retour revient au menu puis ferme le panneau.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    installedApps: List<AppInfo>,
    networkMonitor: NetworkMonitor,
    networkState: Flow<NetState>,
    onClose: () -> Unit = {},
    /** Section ouverte d'emblée (l'assistant au premier lancement). */
    initialSection: Section? = null,
) {
    var opened by rememberSaveable { mutableStateOf(initialSection) }
    var lastSection by rememberSaveable { mutableStateOf(initialSection ?: Section.Favorites) }
    BackHandler { if (opened != null) opened = null else onClose() }
    // Filet de sécurité : si l'élément qui avait le focus disparaît, le panneau le reprend
    // au lieu de le laisser retomber sur l'accueil derrière.
    val panelFocus = remember { FocusRequester() }
    var panelHasFocus by remember { mutableStateOf(true) }
    LaunchedEffect(panelHasFocus) {
        if (panelHasFocus) return@LaunchedEffect
        // Laisse d'abord le menu ou la section poser leur propre focus.
        repeat(15) { withFrameNanos { } }
        if (!panelHasFocus) runCatching { panelFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        // Voile progressif : l'accueil reste visible à gauche.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0.35f to Color.Transparent, 0.62f to Background.copy(alpha = 0.8f)),
            ),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .width(480.dp)
                .fillMaxHeight()
                .background(SurfaceColor, RoundedCornerShape(24.dp))
                // Le focus reste dans le panneau tant qu'il est ouvert.
                .focusRequester(panelFocus)
                .onFocusChanged { panelHasFocus = it.hasFocus }
                .focusGroup()
                .focusProperties { exit = { FocusRequester.Cancel } },
        ) {
            val section = opened
            if (section == null) {
                SettingsMenu(selected = lastSection) {
                    lastSection = it
                    opened = it
                }
            } else {
                Row(
                    Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvIconImage(section.icon, OnBackground, size = 26.dp)
                    Text(section.title, fontSize = 24.sp, color = OnBackground, maxLines = 1)
                }
                Text(
                    section.description,
                    fontSize = 14.sp,
                    color = OnBackgroundMuted,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 12.dp),
                )
                // À l'ouverture, le focus va au premier réglage de la section : l'élément du menu
                // qui l'avait disparaît, sinon le focus retomberait sur l'accueil derrière.
                val contentFocus = remember(section) { FocusRequester() }
                var contentHasFocus by remember(section) { mutableStateOf(false) }
                LaunchedEffect(section) {
                    // Le contenu peut arriver après quelques images (listes, données chargées).
                    repeat(30) {
                        if (contentHasFocus) return@LaunchedEffect
                        withFrameNanos { }
                        runCatching { contentFocus.requestFocus() }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        .focusRequester(contentFocus)
                        .onFocusChanged { contentHasFocus = it.hasFocus }
                        .focusGroup(),
                ) {
                    SectionContent(section, vm, installedApps, networkMonitor, networkState) { opened = null }
                }
            }
        }
    }
}

/** Position de [section] dans la liste du menu : titre, puis pour chaque groupe son intitulé et ses sections. */
private fun menuIndexOf(section: Section): Int {
    var index = 1
    for (group in Group.entries) {
        index++ // intitulé du groupe
        for (s in Section.entries.filter { it.group == group }) {
            if (s == section) return index
            index++
        }
    }
    return 0
}

/** Liste des sections du panneau, avec icônes ; la dernière section ouverte reprend le focus. */
@Composable
private fun SettingsMenu(selected: Section, onOpen: (Section) -> Unit) {
    val initial = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var menuHasFocus by remember { mutableStateOf(false) }
    // Au retour d'une section, sa ligne peut être hors écran (donc pas encore composée) :
    // on fait défiler jusqu'à elle, puis on redemande le focus jusqu'à ce qu'il soit posé.
    LaunchedEffect(Unit) {
        listState.scrollToItem(menuIndexOf(selected))
        repeat(30) {
            if (menuHasFocus) return@LaunchedEffect
            withFrameNanos { }
            runCatching { initial.requestFocus() }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.onFocusChanged { menuHasFocus = it.hasFocus },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                Modifier.padding(start = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvIconImage(TvIcon.Settings, OnBackground, size = 26.dp)
                Text("Réglages", fontSize = 24.sp, color = OnBackground)
            }
        }
        Group.entries.forEach { group ->
            item(key = group.name) {
                Text(
                    group.title.uppercase(),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = OnBackgroundMuted,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            items(Section.entries.filter { it.group == group }, key = { it.name }) { s ->
                ListItem(
                    selected = false,
                    onClick = { onOpen(s) },
                    headlineContent = { Text(s.title, maxLines = 1) },
                    leadingContent = { TvIconImage(s.icon, LocalContentColor.current) },
                    trailingContent = { Text("›", fontSize = 20.sp) },
                    modifier = if (s == selected) Modifier.focusRequester(initial) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SectionContent(
    section: Section,
    vm: SettingsViewModel,
    installedApps: List<AppInfo>,
    networkMonitor: NetworkMonitor,
    networkState: Flow<NetState>,
    /** Retour au menu des réglages. */
    onBack: () -> Unit,
) {
    run {
            when (section) {
                Section.Favorites -> FavoritesSettings(vm, installedApps)
                Section.Wallpaper -> WallpaperSettings(vm)
                Section.Theme -> ThemeSettings(vm)
                Section.Layout -> LayoutSettings(vm)
                Section.Weather -> WeatherSettings(vm)
                Section.Reco -> RecoSettings(vm, installedApps)
                Section.Clock -> ClockSettings(vm)
                Section.Remote -> RemoteSettings(vm, installedApps)
                Section.Profiles -> ProfileSettings()
                Section.Backup -> BackupSettings()
                Section.Update -> UpdateSettings()
                Section.Memory -> MemorySettings(installedApps)
                Section.Setup -> SetupWizard(onDone = onBack)
                Section.Network -> NetworkSettings(networkMonitor, networkState)
                Section.Android -> BoxSettings()
            }
    }
}
