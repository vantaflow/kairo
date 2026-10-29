package com.tvhome.launcher.reco

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/**
 * Chaîne d'un tuner (TNT, satellite, IPTV déclarée au système) ou entrée HDMI de la box.
 * [uri] s'ouvre avec ACTION_VIEW dans l'appli TV du système ; à défaut, [packageName] est lancé.
 */
data class LiveChannel(
    val key: String,
    val packageName: String,
    val number: String?,
    val name: String,
    val uri: String,
    val logoUri: String? = null,
)

/** Ordre des chaînes : par numéro (1, 2, 10, 10.1…), puis par nom ; sans numéro à la fin. */
fun sortLiveChannels(channels: List<LiveChannel>): List<LiveChannel> =
    channels.sortedWith(
        compareBy<LiveChannel>({ numberKey(it.number).first }, { numberKey(it.number).second }, { it.name.lowercase() }),
    )

private fun numberKey(number: String?): Pair<Int, Int> {
    val parts = number?.split('.', '-', ' ')?.mapNotNull { it.toIntOrNull() }.orEmpty()
    return (parts.getOrNull(0) ?: Int.MAX_VALUE) to (parts.getOrNull(1) ?: 0)
}

/** Entrées physiques (HDMI, composite…) déclarées par la box ; vide sur une box sans entrée. */
fun passthroughInputs(context: Context): List<LiveChannel> {
    val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return try {
        manager.tvInputList.filter { it.isPassthroughInput && !it.isHidden(context) }.map { input ->
            LiveChannel(
                key = "input-${input.id}",
                packageName = input.serviceInfo.packageName,
                number = null,
                name = input.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                    ?: input.loadLabel(context).toString().ifBlank { inputTypeName(input.type) },
                uri = TvContract.buildChannelUriForPassthroughInput(input.id).toString(),
            )
        }
    } catch (_: RuntimeException) {
        // Service TV absent ou défaillant sur certaines box : pas de rangée plutôt qu'un plantage.
        emptyList()
    }
}

private fun inputTypeName(type: Int) = when (type) {
    TvInputInfo.TYPE_HDMI -> "HDMI"
    TvInputInfo.TYPE_COMPOSITE -> "Composite"
    TvInputInfo.TYPE_COMPONENT -> "Composante"
    TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
    else -> "Entrée"
}
