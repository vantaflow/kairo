package com.vantaflow.kairo.spike

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat

data class ProbedChannel(
    val id: Long,
    val packageName: String,
    val displayName: String,
    val browsable: Boolean,
)

data class EpgProbeReport(
    val canReadChannels: Boolean,
    val channels: List<ProbedChannel>,
    val watchNextCount: Int,
    val previewProgramCount: Int,
    val error: String?,
)

/** Sonde l'accès du launcher aux chaînes et programmes publiés par les autres applis (TvProvider). */
object EpgProbe {
    fun run(context: Context): EpgProbeReport {
        val errors = mutableListOf<String>()
        val channels = try {
            readChannels(context)
        } catch (e: Exception) {
            errors += "channels: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
        val watchNext = count(context, TvContractCompat.WatchNextPrograms.CONTENT_URI, "watchNext", errors)
        val preview = count(context, TvContractCompat.PreviewPrograms.CONTENT_URI, "preview", errors)
        return EpgProbeReport(
            canReadChannels = channels != null,
            channels = channels.orEmpty(),
            watchNextCount = watchNext,
            previewProgramCount = preview,
            error = errors.takeIf { it.isNotEmpty() }?.joinToString(" | "),
        )
    }

    private fun readChannels(context: Context): List<ProbedChannel> {
        val projection = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_PACKAGE_NAME,
            TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
        )
        val cursor = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI, projection, null, null, null,
        ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ProbedChannel(
                            id = c.getLong(0),
                            packageName = c.getString(1).orEmpty(),
                            displayName = c.getString(2).orEmpty(),
                            browsable = c.getInt(3) == 1,
                        ),
                    )
                }
            }
        }
    }

    private fun count(context: Context, uri: Uri, label: String, errors: MutableList<String>): Int =
        try {
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count } ?: 0
        } catch (e: Exception) {
            errors += "$label: ${e.javaClass.simpleName}: ${e.message}"
            -1
        }
}
