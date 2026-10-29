package com.vantaflow.kairo.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat

/**
 * Coupe le suivi d'accessibilité de Compose dans les vues de [root].
 *
 * La box garde toujours des services d'accessibilité actifs (touches de la télécommande, service
 * du constructeur) : Compose reparcourt alors tout l'écran à chaque défilement, ce qui coûte
 * ~100 ms par seconde de navigation sur la KM7 Plus. Aucun lecteur d'écran n'est utilisé sur la
 * box : on vide la liste des services vue par Compose et on retire ses écouteurs.
 *
 * Les champs internes sont repérés par leur type (le nom est obfusqué par R8). Si la structure
 * ne correspond pas (autre version de Compose), rien n'est modifié.
 * À appeler une fois la vue attachée : Compose relit les services à l'attachement.
 */
fun disableComposeAccessibilityTracking(root: View) {
    val manager = root.context.getSystemService(AccessibilityManager::class.java) ?: return
    forEachView(root) { view ->
        val delegate = ViewCompat.getAccessibilityDelegate(view) ?: return@forEachView
        runCatching { neutralize(delegate, manager) }
    }
}

private fun neutralize(delegate: Any, manager: AccessibilityManager) {
    val fields = generateSequence<Class<*>>(delegate.javaClass) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }
        .onEach { it.isAccessible = true }
        .toList()
    // R8 peut remplacer le type déclaré par la classe de la lambda : on teste l'interface implémentée.
    val stateListener = fields.firstOrNull { AccessibilityManager.AccessibilityStateChangeListener::class.java.isAssignableFrom(it.type) }
        ?: return // pas le délégué de Compose
    val touchListener = fields.firstOrNull { AccessibilityManager.TouchExplorationStateChangeListener::class.java.isAssignableFrom(it.type) }
    val services = fields.firstOrNull { field ->
        field.type == List::class.java &&
            (field.get(delegate) as? List<*>)?.let { list -> list.isNotEmpty() && list.all { it is AccessibilityServiceInfo } } == true
    } ?: return
    (stateListener.get(delegate) as? AccessibilityManager.AccessibilityStateChangeListener)
        ?.let(manager::removeAccessibilityStateChangeListener)
    (touchListener?.get(delegate) as? AccessibilityManager.TouchExplorationStateChangeListener)
        ?.let(manager::removeTouchExplorationStateChangeListener)
    services.set(delegate, emptyList<AccessibilityServiceInfo>())
}

private fun forEachView(view: View, action: (View) -> Unit) {
    action(view)
    if (view is ViewGroup) for (i in 0 until view.childCount) forEachView(view.getChildAt(i), action)
}
