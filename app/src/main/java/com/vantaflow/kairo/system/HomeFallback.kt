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
 * Façade Android du dernier filet. N'utilise que les `SharedPreferences` [CRASH_GUARD_PREFS] et le
 * `PackageManager` ; chaque appel est protégé et ne lève jamais d'exception (c'est le dernier recours).
 */
class HomeFallback(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences(CRASH_GUARD_PREFS, Context.MODE_PRIVATE) }

    /** Kairo s'est retiré de l'accueil : rien d'autre ne doit tourner tant que l'utilisateur ne l'a pas remis. */
    fun isWithdrawn(): Boolean = runCatching { prefs.getBoolean(KEY_WITHDRAWN, false) }.getOrDefault(false)

    /**
     * À chaque démarrage, avant tout le reste : compte un plantage du mode sans échec et retire Kairo de
     * l'accueil s'il le faut. Vrai si Kairo vient de se retirer (le démarrage doit s'arrêter là).
     */
    fun withdrawIfSafeModeKeepsCrashing(previousExitWasCrash: Boolean): Boolean = runCatching {
        val previousRunWasSafeMode = prefs.getBoolean(KEY_LAST_RUN_SAFE_MODE, false)
        val before = prefs.getInt(KEY_SAFE_MODE_CRASHES, 0)
        val crashes = nextSafeModeCrashCount(previousExitWasCrash, previousRunWasSafeMode, before)
        if (crashes != before) prefs.edit().putInt(KEY_SAFE_MODE_CRASHES, crashes).commit()
        // Les autres accueils ne sont cherchés que si tout le reste désigne déjà un retrait.
        shouldWithdrawFromHome(previousExitWasCrash, previousRunWasSafeMode, crashes, otherHomeAvailable = true) &&
            shouldWithdrawFromHome(previousExitWasCrash, previousRunWasSafeMode, crashes, otherHomeAvailable()) &&
            withdraw()
    }.getOrDefault(false)

    /** Mode du lancement en cours, relu au démarrage suivant (écrit seulement s'il change). */
    fun recordRun(safeMode: Boolean) {
        runCatching {
            if (prefs.getBoolean(KEY_LAST_RUN_SAFE_MODE, false) != safeMode) {
                prefs.edit().putBoolean(KEY_LAST_RUN_SAFE_MODE, safeMode).commit()
            }
        }
    }

    /** Accueil stable ou « Redémarrer Kairo normalement » : les plantages en mode sans échec sont oubliés. */
    fun resetCrashes() {
        runCatching {
            if (prefs.contains(KEY_SAFE_MODE_CRASHES)) prefs.edit().remove(KEY_SAFE_MODE_CRASHES).commit()
        }
    }

    /**
     * « Remettre Kairo comme accueil » : accueil de Kairo réactivé, toute la garde remise à zéro
     * (démarrages notés, compteurs, indicateur de retrait), activité de réparation désactivée.
     * Faux si l'accueil n'a pas pu être réactivé (rien d'autre n'est alors changé).
     */
    fun restore(): Boolean = runCatching {
        setEnabled(HomeActivity::class.java.name, true)
        prefs.edit().clear().commit()
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
        val disabled = runCatching { setEnabled(HomeActivity::class.java.name, false) }.isSuccess
        if (!disabled) {
            runCatching { setEnabled(RECOVERY_ACTIVITY, false) }
            return false
        }
        runCatching { prefs.edit().putBoolean(KEY_WITHDRAWN, true).commit() }
        return true
    }

    /** Accueils activés de la box (requête visible grâce au bloc `<queries>` du manifeste). */
    private fun otherHomeAvailable(): Boolean = runCatching {
        val homes = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { HomeCandidate(it.activityInfo.packageName, it.activityInfo.name) }
        hasOtherHome(homes, context.packageName)
    }.getOrDefault(false)

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
        const val RECOVERY_ACTIVITY = "com.vantaflow.kairo.system.RecoveryActivity"
    }
}
