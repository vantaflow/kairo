package com.vantaflow.kairo.reco

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat

const val READ_EPG_DATA = "com.android.providers.tv.permission.READ_EPG_DATA"
const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

/** Autorisations nécessaires pour lire les contenus publiés par les autres applis. */
val EPG_PERMISSIONS = arrayOf(READ_EPG_DATA, READ_TV_LISTINGS)

interface EpgSource {
    fun channels(): List<Channel>
    fun watchNext(): List<Program>
    fun hasAccess(): Boolean

    /** Chaînes des tuners de la box (vide sans tuner). */
    fun liveChannels(): List<LiveChannel> = emptyList()
}

/** Requête sur le TvProvider : (uri, colonnes) → curseur. */
fun interface EpgQuery {
    fun query(uri: Uri, projection: Array<String>): Cursor?
}

/**
 * Lit les chaînes et le « À suivre » publiés par les applis (permission READ_EPG_DATA,
 * accordée via ADB). Sans accès, tout est vide : l'accueil reste celui sans recommandations.
 */
class TvProviderEpgSource(
    private val query: EpgQuery,
    private val permissionGranted: () -> Boolean = { true },
) : EpgSource {
    constructor(context: Context) : this(
        EpgQuery { uri, projection -> context.contentResolver.query(uri, projection, null, null, null) },
        // Sans ces permissions, le TvProvider ne lève rien : il ne renvoie que nos propres lignes.
        { EPG_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } },
    )

    @Volatile private var accessible = false

    override fun hasAccess(): Boolean = accessible && permissionGranted()

    override fun channels(): List<Channel> {
        // Pas de filtre « browsable » : sans le launcher Google, personne n'approuve les chaînes.
        val meta = safeRead(TvContractCompat.Channels.CONTENT_URI, CHANNEL_COLUMNS) { c ->
            Triple(c.long("_id"), c.string("package_name").orEmpty(), c.string("display_name").orEmpty())
        } ?: return emptyList()
        val programs = safeRead(TvContractCompat.PreviewPrograms.CONTENT_URI, PREVIEW_COLUMNS) { c ->
            if (c.int("browsable") == 0) null else c.long("channel_id") to c
                .let { cursor -> readProgram(cursor, packageName = "") }
        }.orEmpty().groupBy({ it.first }, { it.second })
        return meta.map { (id, pkg, name) ->
            Channel(id, pkg, name, programs[id].orEmpty().map { it.copy(packageName = pkg) })
        }
    }

    override fun watchNext(): List<Program> =
        safeRead(TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_COLUMNS) { c ->
            if (c.int("browsable") == 0) null else c.long("last_engagement_time_utc_millis") to readProgram(c, c.string("package_name").orEmpty())
        }.orEmpty().sortedByDescending { it.first }.map { (t, p) -> p.copy(engagedAt = t) }

    override fun liveChannels(): List<LiveChannel> =
        safeRead(TvContractCompat.Channels.CONTENT_URI, LIVE_COLUMNS) { c ->
            val id = c.long("_id")
            val pkg = c.string("package_name").orEmpty()
            // Une vraie chaîne a une entrée TV ; les rangées de recommandations (type « preview ») non.
            val live = c.string("type") != TvContractCompat.Channels.TYPE_PREVIEW &&
                !c.string("input_id").isNullOrBlank() && c.int("browsable") != 0 && pkg != OWN_PACKAGE_NAME
            if (!live) return@safeRead null
            val number = c.string("display_number")?.takeIf { it.isNotBlank() }
            LiveChannel(
                key = "channel-$id",
                packageName = pkg,
                number = number,
                name = c.string("display_name")?.takeIf { it.isNotBlank() } ?: "Chaîne ${number.orEmpty()}".trim(),
                uri = TvContractCompat.buildChannelUri(id).toString(),
                logoUri = TvContractCompat.buildChannelLogoUri(id).toString(),
            )
        }.orEmpty().let(::sortLiveChannels)

    private fun readProgram(c: Cursor, packageName: String) = Program(
        id = c.long("_id"),
        packageName = packageName,
        title = c.string("title").orEmpty(),
        subtitle = c.string("episode_title"),
        posterUri = c.string("poster_art_uri") ?: c.string("thumbnail_uri"),
        intentUri = c.string("intent_uri"),
        progressPercent = progressPercent(c.long("last_playback_position_millis"), c.long("duration_millis")),
        description = c.string("short_description"),
    )

    private fun <T> safeRead(uri: Uri, projection: Array<String>, map: (Cursor) -> T?): List<T>? =
        try {
            val cursor = query.query(uri, projection) ?: return null
            accessible = true
            cursor.use { c -> buildList { while (c.moveToNext()) map(c)?.let(::add) } }
        } catch (_: SecurityException) {
            accessible = false
            null
        } catch (_: RuntimeException) {
            // Colonne inconnue, provider indisponible… : on ignore la source plutôt que de planter.
            null
        }

    private fun Cursor.index(name: String) = getColumnIndex(name)
    private fun Cursor.string(name: String): String? = index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun Cursor.long(name: String): Long = index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: 0L
    private fun Cursor.int(name: String): Int = index(name).takeIf { it >= 0 && !isNull(it) }?.let(::getInt) ?: 1

    private companion object {
        val CHANNEL_COLUMNS = arrayOf("_id", "package_name", "display_name")
        val LIVE_COLUMNS = arrayOf("_id", "package_name", "display_name", "display_number", "type", "input_id", "browsable")
        const val OWN_PACKAGE_NAME = "com.vantaflow.kairo"
        val PROGRAM_COLUMNS = arrayOf(
            "_id", "title", "episode_title", "poster_art_uri", "thumbnail_uri", "intent_uri",
            "last_playback_position_millis", "duration_millis", "short_description", "browsable",
        )
        val PREVIEW_COLUMNS = PROGRAM_COLUMNS + "channel_id"
        val WATCH_NEXT_COLUMNS = PROGRAM_COLUMNS + "package_name" + "last_engagement_time_utc_millis"
    }
}
