package com.vantaflow.kairo.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Thème visuel du launcher : accent (sélection, boutons, interrupteurs), contour de focus,
 * arrondi et zoom des tuiles, fond des écrans et surfaces (en-tête, panneau des réglages).
 */
data class LauncherTheme(
    val id: String,
    val name: String,
    val accent: Long,
    val focusBorder: Long,
    val tileRadiusDp: Int,
    val focusScale: Float,
    val background: Long = 0xFF101216,
    val surface: Long = 0xFF1F2227,
)

val Themes: List<LauncherTheme> = listOf(
    LauncherTheme("google-tv", "Google TV", accent = 0xFF8AB4F8, focusBorder = 0xFFFFFFFF, tileRadiusDp = 12, focusScale = 1.1f,
        background = 0xFF101216, surface = 0xFF1F2227),
    LauncherTheme("bleu-nuit", "Bleu nuit", accent = 0xFF7FB2FF, focusBorder = 0xFF7FB2FF, tileRadiusDp = 16, focusScale = 1.08f,
        background = 0xFF0A1428, surface = 0xFF16264A),
    LauncherTheme("ambre", "Ambre", accent = 0xFFFFB74D, focusBorder = 0xFFFFB74D, tileRadiusDp = 12, focusScale = 1.1f,
        background = 0xFF1A1209, surface = 0xFF33240F),
    LauncherTheme("emeraude", "Émeraude", accent = 0xFF4ADE80, focusBorder = 0xFF4ADE80, tileRadiusDp = 14, focusScale = 1.08f,
        background = 0xFF07170F, surface = 0xFF12301F),
    LauncherTheme("violet", "Violet", accent = 0xFFC4A1FF, focusBorder = 0xFFC4A1FF, tileRadiusDp = 18, focusScale = 1.1f,
        background = 0xFF120D22, surface = 0xFF261B45),
    LauncherTheme("minimal", "Minimal", accent = 0xFFE8EAED, focusBorder = 0xFFE8EAED, tileRadiusDp = 4, focusScale = 1.05f,
        background = 0xFF000000, surface = 0xFF161616),
)

fun themeById(id: String?): LauncherTheme = Themes.firstOrNull { it.id == id } ?: Themes.first()

const val CUSTOM_THEME_ID = "custom"

/** Thème effectif : le thème personnalisé si choisi, sinon un thème prédéfini. */
fun themeFor(id: String?, custom: LauncherTheme): LauncherTheme =
    if (id == CUSTOM_THEME_ID) custom else themeById(id)

/** Éléments et tailles de l'accueil réglables par l'utilisateur. */
data class HomeLayout(
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showFavoritesLabel: Boolean = true,
    val showRowTitles: Boolean = true,
    /** Rangée « Récentes » : les dernières applis ouvertes depuis Kairo. */
    val showRecentApps: Boolean = true,
    /** Rangées « Chaînes TV » et « Entrées » (seulement sur une box avec tuner ou HDMI). */
    val showLiveTv: Boolean = true,
    val clockSizeSp: Int = 72,
    val tileWidthDp: Int = 192,
    /** Transition du fond « héros » d'un contenu à l'autre : fade, zoom, slide ou none. */
    val heroTransition: String = "fade",
    /** Durée des animations en % de la normale : 60 rapide, 100 normale, 150 douce, 200 très douce. */
    val animationPercent: Int = 100,
) {
    /** Durée [baseMs] ajustée à la vitesse choisie. */
    fun ms(baseMs: Int): Int = baseMs * animationPercent / 100
}

val LocalHomeLayout = staticCompositionLocalOf { HomeLayout() }

val LocalLauncherTheme = staticCompositionLocalOf { Themes.first() }
