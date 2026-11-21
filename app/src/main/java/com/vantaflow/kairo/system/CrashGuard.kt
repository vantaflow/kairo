package com.vantaflow.kairo.system

/** Démarrages rapprochés à partir desquels Kairo passe en mode sans échec. */
const val SAFE_MODE_STARTS = 3

/** Fenêtre dans laquelle ces démarrages sont comptés. */
const val SAFE_MODE_WINDOW_MS = 120_000L

/** Temps d'accueil au premier plan, d'affilée, après lequel le démarrage est jugé réussi. */
const val STABLE_AFTER_MS = 30_000L

/**
 * Garde anti-boucle de plantage : chaque démarrage du process est noté ; s'il y en a
 * [SAFE_MODE_STARTS] en [SAFE_MODE_WINDOW_MS] sans accueil stable entre eux, Kairo démarre en mode
 * sans échec. Sans dépendance Android : le stockage est fourni par [load] et [save].
 */
class CrashGuard(
    private val load: () -> List<Long>,
    private val save: (List<Long>) -> Unit,
    private val now: () -> Long,
) {
    /** Note un démarrage ; vrai s'il faut démarrer en mode sans échec. Ne lève jamais d'exception. */
    fun recordStart(): Boolean {
        val time = now()
        // Historique illisible : on repart de zéro plutôt que de bloquer le démarrage.
        val previous = runCatching { load() }.getOrDefault(emptyList())
        val recent = (previous + time).filter { it <= time && time - it < SAFE_MODE_WINDOW_MS }
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
