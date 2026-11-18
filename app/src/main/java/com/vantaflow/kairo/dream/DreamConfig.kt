package com.vantaflow.kairo.dream

import androidx.annotation.StringRes
import com.vantaflow.kairo.R

/** Réglages de l'écran de veille Kairo. */
data class DreamConfig(
    /** pixabay (photos par thème), photos (celles de la box et des clés USB) ou gradient (dégradés animés). */
    val mode: String = "pixabay",
    val query: String = "nature landscape",
    val palette: String = "aurore",
    /** Durée d'une image, en secondes. */
    val intervalSec: Int = 45,
    /** fade, slow (fondu lent) ou kenburns (zoom lent). */
    val transition: String = "kenburns",
    val showClock: Boolean = true,
    /** Citation du jour en haut de l'écran. */
    val showQuote: Boolean = true,
)

/** Palette d'un dégradé animé : les couleurs se fondent lentement les unes dans les autres. */
data class Palette(val id: String, @StringRes val name: Int, val colors: List<Long>)

val Palettes: List<Palette> = listOf(
    Palette("aurore", R.string.dreamscreen_palette_aurore, listOf(0xFF0B3D5C, 0xFF1D7A6E, 0xFF5B3F8C, 0xFF0F2A4A)),
    Palette("ocean", R.string.dreamscreen_palette_ocean, listOf(0xFF03224C, 0xFF0B5E8C, 0xFF1AA0B8, 0xFF062B47)),
    Palette("crepuscule", R.string.dreamscreen_palette_crepuscule, listOf(0xFF2B1055, 0xFF7A2C67, 0xFFC0504D, 0xFF3A1C4A)),
    Palette("foret", R.string.dreamscreen_palette_foret, listOf(0xFF0F2A1D, 0xFF2E5E3A, 0xFF6B7F3A, 0xFF16352A)),
    Palette("neon", R.string.dreamscreen_palette_neon, listOf(0xFF12002B, 0xFF5A1CB5, 0xFF0099C8, 0xFFB5179E)),
    Palette("braise", R.string.dreamscreen_palette_braise, listOf(0xFF2A0A06, 0xFF7A1F0E, 0xFFC4561A, 0xFF3D1208)),
)

fun paletteById(id: String) = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

// Choix des réglages : valeur enregistrée → libellé affiché (ressource).
val DreamIntervals: List<Pair<Int, Int>> = listOf(
    20 to R.string.dreamscreen_interval_20s, 45 to R.string.dreamscreen_interval_45s,
    120 to R.string.dreamscreen_interval_2min, 300 to R.string.dreamscreen_interval_5min,
)
val DreamTransitions: List<Pair<String, Int>> = listOf(
    "fade" to R.string.dreamscreen_transition_fade, "slow" to R.string.dreamscreen_transition_slow,
    "kenburns" to R.string.dreamscreen_transition_kenburns,
)
val DreamModes: List<Pair<String, Int>> = listOf(
    "pixabay" to R.string.dreamscreen_mode_pixabay, "photos" to R.string.dreamscreen_mode_photos,
    "gradient" to R.string.dreamscreen_mode_gradient,
)
