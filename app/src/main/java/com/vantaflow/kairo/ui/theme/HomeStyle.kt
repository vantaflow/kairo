package com.vantaflow.kairo.ui.theme

/** Style de l'écran d'accueil, choisi par profil. */
enum class HomeStyle(val id: String) { Classic("classic"), Cinema("cinema") }

/** Style correspondant à un identifiant enregistré ; inconnu ou absent = classique. */
fun homeStyleById(id: String?): HomeStyle = HomeStyle.entries.firstOrNull { it.id == id } ?: HomeStyle.Classic

/** Origine des contenus mis en avant dans la bannière de l'accueil. */
enum class BannerSource(val id: String) { MyList("my_list"), WatchNext("watch_next"), Catalog("catalog") }

/** Sources enregistrées ; absentes = les trois, identifiants inconnus ignorés. */
fun bannerSourcesOf(ids: Set<String>?): Set<BannerSource> =
    if (ids == null) BannerSource.entries.toSet()
    else BannerSource.entries.filter { it.id in ids }.toSet()
