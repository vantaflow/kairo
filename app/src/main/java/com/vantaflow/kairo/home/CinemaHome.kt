package com.vantaflow.kairo.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vantaflow.kairo.catalog.catalogUri
import com.vantaflow.kairo.remote.HomeFocus

/**
 * Accueil « Cinéma » : la bannière en tête des rangées et, derrière, l'image du contenu qu'elle montre.
 * Sans contenu (sources coupées, profil enfant, onglet Applis, catalogue pas encore chargé), pas de
 * bannière : l'accueil se comporte comme le style Classique. Un seul appel aux rangées dans les deux
 * cas : la bannière qui apparaît ou disparaît ne recrée pas l'accueil (ni ne déplace le focus).
 */
@Composable
fun CinemaHome(data: HomeData, actions: HomeActions, focus: HomeFocus) {
    // Contenu montré par la bannière focalisée : lu seulement par le fond et par le fond « héros »,
    // pour ne pas recomposer tout l'accueil à chaque changement de contenu.
    val current = remember { mutableStateOf<BannerItem?>(null) }
    val items = data.banner
    HomeRowsScreen(
        data = data,
        actions = actions,
        focus = focus,
        banner = if (items.isEmpty()) null else { first ->
            CinemaBanner(
                items = items,
                inMyList = { catalogUri(it) in data.myListIds },
                onOpen = actions.onProgramClick,
                onToggleMyList = actions.onToggleMyList,
                onCurrent = { current.value = it },
                primaryFocus = first,
                appLabels = data.appLabels,
            )
        },
        backdrop = { CinemaBackdrop(current) },
        bannerCurrent = current,
    )
}
