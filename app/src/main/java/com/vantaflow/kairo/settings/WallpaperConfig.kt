package com.vantaflow.kairo.settings

/** Fond d'écran de l'accueil. Les couleurs sont en ARGB (0xAARRGGBB). */
sealed interface WallpaperConfig {
    data class Solid(val argb: Long) : WallpaperConfig
    data class Gradient(val startArgb: Long, val endArgb: Long, val angleDeg: Int) : WallpaperConfig

    /** Images Pixabay sur le thème [query], renouvelées toutes les [intervalMin] minutes. */
    data class Pixabay(val query: String, val intervalMin: Int) : WallpaperConfig

    companion object {
        val Default: WallpaperConfig = Gradient(0xFF1C2A3A, 0xFF101216, 135)
    }
}
