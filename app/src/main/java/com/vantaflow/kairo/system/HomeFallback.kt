package com.vantaflow.kairo.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.vantaflow.kairo.HomeActivity

/** Plantages en mode sans échec à partir desquels Kairo se retire de l'accueil. */
const val SAFE_MODE_CRASHES_BEFORE_WITHDRAW = 2

/** Fichier de préférences de la garde anti-boucle (démarrages notés, dernier filet). */
const val CRASH_GUARD_PREFS = "crash_guard"

/**
 * Dernier filet : vrai si Kairo doit se retirer de l'accueil. La sortie précédente est un plantage,
 * le lancement précédent était déjà en mode sans échec, c'est au moins le
 * [SAFE_MODE_CRASHES_BEFORE_WITHDRAW]ᵉ plantage en mode sans échec, et un autre accueil peut prendre
 * le relais. Sans autre accueil : jamais (la box resterait sans accueil du tout).
 */
fun shouldWithdrawFromHome(
    previousExitWasCrash: Boolean,
    previousRunWasSafeMode: Boolean,
    crashesInSafeMode: Int,
    otherHomeAvailable: Boolean,
): Boolean = previousExitWasCrash && previousRunWasSafeMode &&
    crashesInSafeMode >= SAFE_MODE_CRASHES_BEFORE_WITHDRAW && otherHomeAvailable

/** Plantages en mode sans échec après ce démarrage : +1 si le lancement précédent, en mode sans échec, a planté. */
fun nextSafeModeCrashCount(previousExitWasCrash: Boolean, previousRunWasSafeMode: Boolean, crashesInSafeMode: Int): Int =
    if (previousExitWasCrash && previousRunWasSafeMode) crashesInSafeMode + 1 else crashesInSafeMode

/**
 * Les plantages en mode sans échec sont oubliés : Kairo repart en mode normal après une sortie
 * non plantée (la boucle est passée), même sans accueil stable.
 */
fun forgetsSafeModeCrashes(safeMode: Boolean, previousExitWasCrash: Boolean): Boolean = !safeMode && !previousExitWasCrash

/** Activité d'accueil déclarée sur la box. */
data class HomeCandidate(val packageName: String, val className: String)

/** Paquets des accueils de secours du système (affichés seulement pendant le démarrage). */
private val SYSTEM_FALLBACK_HOME_PACKAGES = setOf("com.android.settings", "com.android.tv.settings")

/**
 * Un autre accueil que Kairo peut prendre le relais ? Les accueils de secours du système
 * (`FallbackHome`, paquets des paramètres) ne comptent pas : ils ne servent qu'au démarrage.
 */
fun hasOtherHome(homes: List<HomeCandidate>, ownPackage: String): Boolean = homes.any {
    it.packageName != ownPackage &&
        it.packageName !in SYSTEM_FALLBACK_HOME_PACKAGES &&
        !it.className.contains("FallbackHome")
}

/**
 * Ce que fait un démarrage d'après l'indicateur de retrait et l'état réel des composants.
 * [stop] : Kairo reste retiré, le démarrage s'arrête là. [restore] : retiré mais plus aucun autre
 * accueil, Kairo reprend l'accueil (puis démarre en mode sans échec). [homeEnabled] et
 * [recoveryEnabled] : état à réappliquer au composant, null s'il est déjà bon.
 */
data class StartupPlan(val stop: Boolean, val restore: Boolean, val homeEnabled: Boolean?, val recoveryEnabled: Boolean?)

/**
 * Plan de démarrage. L'indicateur de retrait fait foi : Android n'écrit l'état des composants sur
 * disque que quelques secondes après le changement, une coupure de courant peut donc laisser les
 * composants en retard sur l'indicateur ; ils sont réalignés à chaque démarrage. Sans indicateur,
 * l'accueil de Kairo est toujours rendu (retrait ou remise en place interrompus). Retiré mais sans
 * autre accueil ([otherHomeAvailable] faux, ignoré hors retrait) : remise en place, la box ne reste
 * jamais sans accueil. [otherHomeAvailable] null (requête au PackageManager en échec) : on n'en
 * conclut rien, Kairo reste retiré.
 */
fun planStartup(withdrawn: Boolean, homeEnabled: Boolean, recoveryEnabled: Boolean, otherHomeAvailable: Boolean?): StartupPlan = when {
    withdrawn && otherHomeAvailable != false -> StartupPlan(
        stop = true, restore = false,
        homeEnabled = false.takeIf { homeEnabled },
        recoveryEnabled = true.takeIf { !recoveryEnabled },
    )
    withdrawn -> StartupPlan(stop = false, restore = true, homeEnabled = null, recoveryEnabled = null)
    else -> StartupPlan(
        stop = false, restore = false,
        homeEnabled = true.takeIf { !homeEnabled },
        recoveryEnabled = false.takeIf { recoveryEnabled },
    )
}

/**
 * Issue du démarrage une fois le [plan] appliqué ([homeApplied] : l'état voulu de l'accueil a pu
 * être écrit). L'accueil ne peut pas être désactivé : remise en place ([restore]) ; si elle échoue
 * aussi, Kairo reste retiré (un autre accueil existe). Plus d'autre accueil : remise en place,
 * réessayée une fois, puis démarrage en mode sans échec quoi qu'il arrive.
 */
fun resolveStartup(plan: StartupPlan, homeApplied: Boolean, restore: () -> Boolean): StartupOutcome = when {
    plan.stop && homeApplied -> StartupOutcome.Withdrawn
    plan.stop -> if (restore()) StartupOutcome.Restored else StartupOutcome.Withdrawn
    plan.restore -> {
        restore() || restore()
        StartupOutcome.Restored
    }
    else -> StartupOutcome.Normal
}

/** Issue de la vérification de démarrage. */
enum class StartupOutcome {
    /** Rien de particulier : démarrage habituel. */
    Normal,
    /** Kairo reste retiré : le démarrage s'arrête là. */
    Withdrawn,
    /**
     * Kairo était retiré mais plus aucun autre accueil n'existe : il a repris l'accueil et reste en mode
     * sans échec jusqu'à « Redémarrer Kairo normalement ».
     */
    Restored,
}

/**
 * Façade Android du dernier filet. N'utilise que les `SharedPreferences` [CRASH_GUARD_PREFS] et le
 * `PackageManager` ; chaque appel est protégé et ne lève jamais d'exception (c'est le dernier recours).
 */
class HomeFallback(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences(CRASH_GUARD_PREFS, Context.MODE_PRIVATE) }

    /** Kairo s'est retiré de l'accueil : rien d'autre ne doit tourner tant que l'utilisateur ne l'a pas remis. */
    fun isWithdrawn(): Boolean = runCatching { prefs.getBoolean(KEY_WITHDRAWN, false) }.getOrDefault(false)

    /**
     * À chaque démarrage, en tout premier : réaligne les composants sur l'indicateur de retrait (voir
     * [planStartup]), à chaque fois, sans rien supposer de ce qui a été écrit avant une coupure.
     * Si l'accueil de Kairo ne peut pas être désactivé, Kairo ne reste pas retiré (l'accueil, encore
     * actif, se relancerait sans fin) : il reprend l'accueil.
     */
    fun repairAtStartup(): StartupOutcome = runCatching {
        val withdrawn = prefs.getBoolean(KEY_WITHDRAWN, false)
        val plan = planStartup(
            withdrawn = withdrawn,
            homeEnabled = isEnabled(HOME_ACTIVITY, enabledByDefault = true),
            recoveryEnabled = isEnabled(RECOVERY_ACTIVITY, enabledByDefault = false),
            otherHomeAvailable = if (withdrawn) otherHomeAvailable() else false,
        )
        // Activations avant désactivations : il reste toujours une entrée vers Kairo.
        val changes = listOfNotNull(
            plan.recoveryEnabled?.let { RECOVERY_ACTIVITY to it },
            plan.homeEnabled?.let { HOME_ACTIVITY to it },
        ).sortedByDescending { it.second }
        var homeApplied = true
        for ((component, enabled) in changes) {
            val ok = runCatching { setEnabled(component, enabled) }.isSuccess
            if (component == HOME_ACTIVITY) homeApplied = ok
        }
        resolveStartup(plan, homeApplied, ::restore).also { outcome ->
            // Remise en place automatique : le mode sans échec tient au-delà de ce process (restore()
            // a effacé l'historique) ; « Redémarrer Kairo normalement » le lève.
            if (outcome == StartupOutcome.Restored) runCatching { prefs.edit().putBoolean(KEY_FORCED_SAFE_MODE, true).commit() }
        }
    }.getOrElse { if (isWithdrawn()) StartupOutcome.Withdrawn else StartupOutcome.Normal }

    /**
     * À chaque démarrage, avant tout le reste : compte un plantage du mode sans échec et retire Kairo de
     * l'accueil s'il le faut. Vrai si Kairo vient de se retirer (le démarrage doit s'arrêter là).
     */
    fun withdrawIfSafeModeKeepsCrashing(previousExitWasCrash: Boolean): Boolean = runCatching {
        val previousRunWasSafeMode = prefs.getBoolean(KEY_LAST_RUN_SAFE_MODE, false)
        val before = prefs.getInt(KEY_SAFE_MODE_CRASHES, 0)
        val crashes = nextSafeModeCrashCount(previousExitWasCrash, previousRunWasSafeMode, before)
        if (crashes != before) prefs.edit().putInt(KEY_SAFE_MODE_CRASHES, crashes).commit()
        // Les autres accueils (requête au PackageManager) ne sont cherchés qu'après un plantage du mode sans échec.
        val crashedInSafeMode = previousExitWasCrash && previousRunWasSafeMode
        crashedInSafeMode &&
            shouldWithdrawFromHome(previousExitWasCrash, previousRunWasSafeMode, crashes, otherHomeAvailable() == true) &&
            withdraw()
    }.getOrDefault(false)

    /**
     * À chaque démarrage (retiré compris), en tout premier : le process précédent a-t-il planté ?
     * Les traces de sortie jamais vues au démarrage précédent sont celles des sorties survenues depuis,
     * sans se fier à leur date (voir [newExitTraces]) ; les traces actuelles sont mémorisées pour le
     * démarrage suivant. Traces illisibles : rien n'est compté ni mémorisé.
     */
    fun previousExit(): PreviousExit = runCatching {
        val current = exitTraces(context) ?: return NO_EXIT
        val seen = decodeSeenExitKeys(prefs.getString(KEY_SEEN_EXITS, null))
        prefs.edit().putString(KEY_SEEN_EXITS, encodeSeenExitKeys(rememberExitKeys(seen, current))).commit()
        previousExit(seen, current, bootIsRecent(android.os.SystemClock.elapsedRealtime()))
    }.getOrDefault(NO_EXIT)

    /**
     * Mode du lancement en cours, relu au démarrage suivant (écrit seulement s'il change). Un démarrage
     * en mode normal après une sortie non plantée oublie aussi les plantages en mode sans échec.
     */
    fun recordRun(safeMode: Boolean, previousExitWasCrash: Boolean) {
        runCatching {
            if (prefs.getBoolean(KEY_LAST_RUN_SAFE_MODE, false) != safeMode) {
                prefs.edit().putBoolean(KEY_LAST_RUN_SAFE_MODE, safeMode).commit()
            }
        }
        if (forgetsSafeModeCrashes(safeMode, previousExitWasCrash)) resetCrashes()
    }

    /**
     * Accueil de secours disponible (un autre accueil activé) ; null si la requête a échoué.
     * Appel au PackageManager : hors du fil principal.
     */
    fun fallbackHomeAvailable(): Boolean? = otherHomeAvailable()

    /**
     * Mode sans échec imposé par une remise en place automatique (voir [StartupOutcome.Restored]) ;
     * levé seulement par « Redémarrer Kairo normalement ».
     */
    fun isSafeModeForced(): Boolean = runCatching { prefs.getBoolean(KEY_FORCED_SAFE_MODE, false) }.getOrDefault(false)

    /**
     * Démarrage réussi (`TvHomeApp.markStable()`, mode normal seulement) ou « Redémarrer Kairo
     * normalement » : les plantages en mode sans échec sont oubliés et l'indicateur de mode sans échec
     * imposé est effacé. En mode sans échec, seul ce bouton peut le lever : `markStable()` n'y est
     * jamais appelé (pas de porte de démarrage).
     */
    fun resetCrashes() {
        runCatching {
            if (prefs.contains(KEY_SAFE_MODE_CRASHES) || prefs.contains(KEY_FORCED_SAFE_MODE)) {
                prefs.edit().remove(KEY_SAFE_MODE_CRASHES).remove(KEY_FORCED_SAFE_MODE).commit()
            }
        }
    }

    /**
     * « Remettre Kairo comme accueil » : accueil de Kairo réactivé, toute la garde remise à zéro
     * (démarrages notés, compteurs, indicateur de retrait ; seules les traces de sortie déjà vues
     * sont gardées), activité de réparation désactivée.
     * Faux si l'accueil n'a pas pu être réactivé (rien d'autre n'est alors changé).
     */
    fun restore(): Boolean = runCatching {
        setEnabled(HOME_ACTIVITY, true)
        val seenExits = prefs.getString(KEY_SEEN_EXITS, null)
        // Sur un éditeur, clear() passe avant les écritures : les traces vues restent.
        prefs.edit().clear().apply { if (seenExits != null) putString(KEY_SEEN_EXITS, seenExits) }.commit()
        runCatching { setEnabled(RECOVERY_ACTIVITY, false) }
        true
    }.getOrDefault(false)

    /**
     * Activité de réparation activée, accueil de Kairo désactivé, puis indicateur écrit. L'indicateur
     * n'est écrit qu'une fois l'accueil réellement désactivé : sinon l'accueil démarrerait dans un
     * process sans conteneur.
     */
    private fun withdraw(): Boolean {
        runCatching { setEnabled(RECOVERY_ACTIVITY, true) }
        val disabled = runCatching { setEnabled(HOME_ACTIVITY, false) }.isSuccess
        if (!disabled) {
            runCatching { setEnabled(RECOVERY_ACTIVITY, false) }
            return false
        }
        runCatching { prefs.edit().putBoolean(KEY_WITHDRAWN, true).commit() }
        return true
    }

    /**
     * Un autre accueil activé existe sur la box (requête visible grâce au bloc `<queries>` du
     * manifeste) ; null si la requête a échoué : on ne sait pas.
     */
    private fun otherHomeAvailable(): Boolean? = runCatching {
        val homes = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { HomeCandidate(it.activityInfo.packageName, it.activityInfo.name) }
        hasOtherHome(homes, context.packageName)
    }.getOrNull()

    /** État réel du composant ; « par défaut » = celui du manifeste. */
    private fun isEnabled(className: String, enabledByDefault: Boolean): Boolean =
        when (context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, className))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> enabledByDefault
            else -> false
        }

    private fun setEnabled(className: String, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, className),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val KEY_WITHDRAWN = "withdrawn"
        const val KEY_LAST_RUN_SAFE_MODE = "last_run_safe_mode"
        const val KEY_SAFE_MODE_CRASHES = "safe_mode_crashes"
        const val KEY_SEEN_EXITS = "seen_exits"
        val NO_EXIT = PreviousExit(crashed = false, countsForSafeMode = false)
        const val KEY_FORCED_SAFE_MODE = "forced_safe_mode"
        const val RECOVERY_ACTIVITY = "com.vantaflow.kairo.system.RecoveryActivity"
        val HOME_ACTIVITY: String = HomeActivity::class.java.name
    }
}
