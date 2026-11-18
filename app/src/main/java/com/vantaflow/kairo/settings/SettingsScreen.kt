package com.vantaflow.kairo.settings

import com.vantaflow.kairo.system.InputsSettings
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
import com.vantaflow.kairo.ui.TvIcon
import com.vantaflow.kairo.ui.TvIconImage
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
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.network.NetState
import com.vantaflow.kairo.network.NetworkMonitor
import com.vantaflow.kairo.network.NetworkSettings
import com.vantaflow.kairo.system.BoxSettings
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.SurfaceColor
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.flow.Flow
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

enum class Group(@StringRes val title: Int) { App(R.string.settings_group_app), Box(R.string.settings_group_box) }

enum class Section(@StringRes val title: Int, val group: Group, val icon: TvIcon, @StringRes val description: Int) {
    Setup(R.string.section_setup, Group.App, TvIcon.Check, R.string.section_setup_desc),
    Favorites(R.string.section_favorites, Group.App, TvIcon.Favorites, R.string.section_favorites_desc),
    Wallpaper(R.string.section_wallpaper, Group.App, TvIcon.Wallpaper, R.string.section_wallpaper_desc),
    Theme(R.string.section_theme, Group.App, TvIcon.Theme, R.string.section_theme_desc),
    Layout(R.string.section_layout, Group.App, TvIcon.Layout, R.string.section_layout_desc),
    Dream(R.string.section_dream, Group.App, TvIcon.Moon, R.string.section_dream_desc),
    Weather(R.string.section_weather, Group.App, TvIcon.Weather, R.string.section_weather_desc),
    Reco(R.string.section_reco, Group.App, TvIcon.Reco, R.string.section_reco_desc),
    Clock(R.string.section_clock, Group.App, TvIcon.Clock, R.string.section_clock_desc),
    Language(R.string.section_language, Group.App, TvIcon.Language, R.string.section_language_desc),
    Remote(R.string.section_remote, Group.App, TvIcon.Remote, R.string.section_remote_desc),
    AdBlock(R.string.section_adblock, Group.App, TvIcon.Shield, R.string.section_adblock_desc),
    Phone(R.string.section_phone, Group.App, TvIcon.Phone, R.string.section_phone_desc),
    Profiles(R.string.section_profiles, Group.App, TvIcon.Profiles, R.string.section_profiles_desc),
    Backup(R.string.section_backup, Group.App, TvIcon.Backup, R.string.section_backup_desc),
    Update(R.string.section_update, Group.App, TvIcon.Update, R.string.section_update_desc),
    Power(R.string.section_power, Group.Box, TvIcon.Timer, R.string.section_power_desc),
    Inputs(R.string.section_inputs, Group.Box, TvIcon.Android, R.string.section_inputs_desc),
    Network(R.string.section_network, Group.Box, TvIcon.Network, R.string.section_network_desc),
    Memory(R.string.section_memory, Group.Box, TvIcon.Memory, R.string.section_memory_desc),
    Android(R.string.section_android, Group.Box, TvIcon.Android, R.string.section_android_desc),
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
    /** Relance l'accueil animé du premier lancement. */
    onReplayOnboarding: () -> Unit = {},
) {
    // Clé = section demandée : la touche Entrées, panneau déjà ouvert, va bien à sa section.
    var opened by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    var lastSection by rememberSaveable(initialSection) { mutableStateOf(initialSection ?: Section.Favorites) }
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
                .width(540.dp)
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
                    Text(stringResource(section.title), fontSize = 24.sp, color = OnBackground, maxLines = 1)
                }
                Text(
                    stringResource(section.description),
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
                    SectionContent(section, vm, installedApps, networkMonitor, networkState, onReplayOnboarding) { opened = null }
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
        // Défilement seulement si la ligne n'est pas déjà à l'écran : sinon la liste sautait
        // (titre masqué puis réaffiché) à chaque ouverture du panneau.
        withFrameNanos { }
        val index = menuIndexOf(selected)
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index && it.offset >= 0 }) listState.scrollToItem(index)
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
                Text(stringResource(R.string.settings_title), fontSize = 24.sp, color = OnBackground)
            }
        }
        Group.entries.forEach { group ->
            item(key = group.name) {
                Text(
                    stringResource(group.title).uppercase(),
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
                    headlineContent = { Text(stringResource(s.title), maxLines = 1) },
                    leadingContent = { TvIconImage(s.icon, LocalContentColor.current) },
                    trailingContent = { Text(stringResource(R.string.chevron_forward), fontSize = 20.sp) },
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
    onReplayOnboarding: () -> Unit,
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
                Section.AdBlock -> com.vantaflow.kairo.adblock.AdBlockSettings()
                Section.Dream -> DreamSettings()
                Section.Reco -> RecoSettings(vm, installedApps)
                Section.Clock -> ClockSettings(vm)
                Section.Language -> LanguageSettings()
                Section.Remote -> RemoteSettings(vm, installedApps)
                Section.Phone -> PhoneRemoteSettings()
                Section.Profiles -> ProfileSettings()
                Section.Backup -> BackupSettings()
                Section.Update -> UpdateSettings()
                Section.Memory -> MemorySettings(installedApps)
                Section.Setup -> SetupWizard(onDone = onBack, onReplay = onReplayOnboarding)
                Section.Network -> NetworkSettings(networkMonitor, networkState)
                Section.Android -> BoxSettings()
                Section.Inputs -> InputsSettings()
                Section.Power -> com.vantaflow.kairo.system.PowerSettings()
            }
    }
}
