package com.vantaflow.kairo.player

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat

/**
 * Rangée « Vidéos récentes » publiée pour l'accueil de Kairo (et de Google TV) : les dernières
 * vidéos ajoutées, republiée seulement quand la liste change.
 */
class RecentChannel(private val context: Context) {
    private val prefs = context.getSharedPreferences("recent_channel", Context.MODE_PRIVATE)
    private val helper = PreviewChannelHelper(context)

    fun publish(videos: List<Media>) {
        val top = videos.sortedByDescending { it.dateAdded }.take(MAX)
        val signature = top.joinToString("|") { it.uri.toString() }
        if (signature == prefs.getString("signature", null) && channelId() != null) return
        runCatching {
            val id = channelId() ?: helper.publishChannel(
                PreviewChannel.Builder()
                    .setDisplayName("Vidéos récentes")
                    .setInternalProviderId("recent")
                    .setLogo(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                    .setAppLinkIntentUri(Uri.parse(Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME)))
                    .build(),
            ).also { prefs.edit().putLong("channel", it).apply() }
            context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null)
            top.forEachIndexed { i, m ->
                helper.publishPreviewProgram(
                    PreviewProgram.Builder()
                        .setChannelId(id)
                        .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                        .setTitle(displayTitle(m.name))
                        .setPosterArtUri(ThumbProvider.uriFor(context, m.uri))
                        .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                        .setDurationMillis(m.durationMs.toInt())
                        .setIntentUri(Uri.parse(playIntent(context, m.uri).toUri(Intent.URI_INTENT_SCHEME)))
                        .setInternalProviderId(m.uri.toString())
                        .setWeight(MAX - i)
                        .build(),
                )
            }
            prefs.edit().putString("signature", signature).apply()
        }.onFailure { Log.w("KairoPlayer", "Rangée « Vidéos récentes » non publiée", it) }
    }

    /** Rangée déjà publiée ? (lecture directe : getPreviewChannel plante sur un champ vide.) */
    private fun channelId(): Long? = prefs.getLong("channel", -1).takeIf { id ->
        id >= 0 && runCatching {
            context.contentResolver.query(TvContractCompat.buildChannelUri(id), arrayOf("_id"), null, null, null)?.use { it.count > 0 }
        }.getOrNull() == true
    }

    private companion object {
        const val MAX = 10
    }
}
