package com.vantaflow.kairo.home

import androidx.compose.runtime.Composable
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.apps.InstallState
import com.vantaflow.kairo.apps.OurApp
import com.vantaflow.kairo.network.NetState
import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.LiveChannel
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.tools.Tool
import com.vantaflow.kairo.ui.theme.HomeStyle

/** Ce que montre l'accueil, quel que soit son style. */
data class HomeData(
    val state: HomeUiState,
    val appLabels: Map<String, String> = emptyMap(),
    val netState: NetState? = null,
    /** Applis hors favoris, pour la rangée « Toutes les applis » (vide = rangée masquée). */
    val otherApps: List<AppInfo> = emptyList(),
    /** Dernières applis ouvertes, pour la rangée « Récentes » (vide = rangée masquée). */
    val recentApps: List<AppInfo> = emptyList(),
    /** Nos applis pas encore installées (vide = rangée masquée) et leur état d'installation. */
    val ourApps: List<OurApp> = emptyList(),
    val ourAppStates: Map<String, InstallState> = emptyMap(),
    /** Parmi [ourApps], celles déjà installées qui ont une mise à jour. */
    val ourAppUpdates: Set<String> = emptySet(),
    val nowPlaying: String? = null,
    /** Rangée « Outils » sous « Toutes les applis » et l'état de chaque outil ; vide = masquée. */
    val tools: List<Tool> = emptyList(),
    val toolSubtitles: Map<Tool, String> = emptyMap(),
    /** Favori en cours de déplacement : ← → le déplacent, OK ou Retour terminent. */
    val movingFavorite: String? = null,
    /** Onglet affiché (Pour vous, Films, Séries, Applis) ; null = pas d'onglets (catalogue indisponible). */
    val tab: HomeTab? = null,
    /** Onglets affichés (Réglages › Personnaliser). */
    val tabsShown: List<HomeTab> = HomeTab.entries,
    /** Rangées du catalogue (TMDB) déjà choisies pour l'onglet, et « Ma liste ». */
    val catalog: List<Channel> = emptyList(),
    val myList: List<Program> = emptyList(),
)

/** Ce que l'accueil peut demander, quel que soit son style. */
class HomeActions(
    val onLaunch: (AppInfo) -> Unit = {},
    val onFavoriteLongClick: (AppInfo) -> Unit = {},
    val onOpenApps: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onProgramClick: (Program) -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onOpenTv: (LiveChannel) -> Unit = {},
    val onInstallOurApp: (OurApp) -> Unit = {},
    /** Bouton « Outils » de l'en-tête (null = masqué). */
    val onOpenTools: (() -> Unit)? = null,
    val onOpenNowPlaying: () -> Unit = {},
    val onOpenTool: (Tool) -> Unit = {},
    val onMoveFavorite: (String, Int) -> Unit = { _, _ -> },
    val onMoveDone: () -> Unit = {},
    val onTab: (HomeTab) -> Unit = {},
)

/** Accueil « Classique » : les rangées, sans bannière. */
@Composable
fun ClassicHome(data: HomeData, actions: HomeActions, focus: HomeFocus) {
    HomeRowsScreen(data, actions, focus)
}

/** Accueil du style choisi (Réglages › Personnaliser). */
@Composable
fun HomeHost(style: HomeStyle, data: HomeData, actions: HomeActions, focus: HomeFocus) {
    when (style) {
        HomeStyle.Classic -> ClassicHome(data, actions, focus)
        // Pas encore de bannière : le style « Cinéma » montre l'accueil classique.
        HomeStyle.Cinema -> ClassicHome(data, actions, focus)
    }
}
