package com.vantaflow.kairo.system

/** Démarrages rapprochés à partir desquels Kairo passe en mode sans échec. */
const val SAFE_MODE_STARTS = 3

/** Fenêtre dans laquelle ces démarrages sont comptés. */
const val SAFE_MODE_WINDOW_MS = 120_000L

/** Temps d'accueil au premier plan, d'affilée, après lequel le démarrage est jugé réussi. */
const val STABLE_AFTER_MS = 30_000L

/**
 * Garde anti-boucle de plantage : chaque démarrage qui suit un plantage est noté ; s'il y en a
 * [SAFE_MODE_STARTS] en [SAFE_MODE_WINDOW_MS] sans accueil stable entre eux, Kairo démarre en mode
 * sans échec. Sans dépendance Android : le stockage est fourni par [load] et [save].
 */
class CrashGuard(
    private val load: () -> List<Long>,
    private val save: (List<Long>) -> Unit,
    private val now: () -> Long,
) {
    /**
     * Note un démarrage ; vrai s'il faut démarrer en mode sans échec. Ne lève jamais d'exception.
     *
     * Seul un démarrage qui suit un plantage ([previousExitWasCrash]) compte : un arrêt légitime
     * (mémoire basse, mise à jour, arrêt forcé) n'est pas une boucle de plantage. Un tel démarrage
     * n'efface pas non plus l'historique : s'il survient au milieu d'une boucle déjà repérée
     * ([SAFE_MODE_STARTS] plantages dans la fenêtre, par exemple Kairo tué par manque de mémoire
     * pendant le mode sans échec), Kairo reste en mode sans échec au lieu de replonger dans la boucle.
     */
    fun recordStart(previousExitWasCrash: Boolean): Boolean {
        val time = now()
        // Historique illisible : on repart de zéro plutôt que de bloquer le démarrage.
        val previous = runCatching { load() }.getOrDefault(emptyList())
        val inWindow = { t: Long -> t <= time && time - t < SAFE_MODE_WINDOW_MS }
        if (!previousExitWasCrash) return previous.count(inWindow) >= SAFE_MODE_STARTS
        val recent = (previous + time).filter(inWindow)
        runCatching { save(recent) }
        return recent.size >= SAFE_MODE_STARTS
    }

    /** L'accueil est resté au premier plan [STABLE_AFTER_MS] : les démarrages précédents sont oubliés. */
    fun markStable() {
        runCatching { save(emptyList()) }
    }

    /** « Redémarrer Kairo normalement » : le prochain démarrage repart en mode normal. */
    fun reset() {
        runCatching { save(emptyList()) }
    }
}
