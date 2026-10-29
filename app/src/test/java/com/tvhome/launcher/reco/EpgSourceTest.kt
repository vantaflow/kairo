package com.tvhome.launcher.reco

import android.database.MatrixCursor
import androidx.tvprovider.media.tv.TvContractCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpgSourceTest {
    @Test
    fun securityExceptionGivesEmpty() {
        val source = TvProviderEpgSource(EpgQuery { _, _ -> throw SecurityException("no EPG access") })
        assertEquals(emptyList<Channel>(), source.channels())
        assertEquals(emptyList<Program>(), source.watchNext())
        assertFalse(source.hasAccess())
    }

    @Test
    fun providerRuntimeErrorGivesEmpty() {
        val source = TvProviderEpgSource(EpgQuery { _, _ -> throw IllegalStateException("provider mort") })
        assertEquals(emptyList<Channel>(), source.channels())
        assertEquals(emptyList<Program>(), source.watchNext())
    }

    @Test
    fun noPermissionMeansNoAccessEvenIfQueryWorks() {
        val source = TvProviderEpgSource(EpgQuery { _, _ -> MatrixCursor(arrayOf("_id")) }, permissionGranted = { false })
        source.channels()
        assertFalse(source.hasAccess())
    }

    @Test
    fun watchNextSortedByLastEngagement() {
        val source = TvProviderEpgSource(EpgQuery { uri, _ ->
            if (uri != TvContractCompat.WatchNextPrograms.CONTENT_URI) null
            else MatrixCursor(arrayOf("_id", "package_name", "title", "last_engagement_time_utc_millis")).apply {
                addRow(arrayOf<Any?>(1L, "a", "ancien", 100L))
                addRow(arrayOf<Any?>(2L, "a", "récent", 300L))
                addRow(arrayOf<Any?>(3L, "a", "moyen", 200L))
            }
        })
        assertEquals(listOf("récent", "moyen", "ancien"), source.watchNext().map { it.title })
    }

    @Test
    fun previewProgramsGroupedByChannel() {
        val source = TvProviderEpgSource(EpgQuery { uri, _ ->
            when (uri) {
                TvContractCompat.Channels.CONTENT_URI -> MatrixCursor(arrayOf("_id", "package_name", "display_name", "browsable")).apply {
                    addRow(arrayOf<Any?>(7L, "com.youtube", "Tendances", 1))
                    addRow(arrayOf<Any?>(8L, "com.netflix", "Masquée", 0))
                }
                TvContractCompat.PreviewPrograms.CONTENT_URI -> MatrixCursor(
                    arrayOf("_id", "channel_id", "title", "episode_title", "poster_art_uri", "intent_uri",
                        "last_playback_position_millis", "duration_millis", "browsable"),
                ).apply {
                    addRow(arrayOf<Any?>(1L, 7L, "Vidéo A", null, "https://img/a.jpg", "intent:a", 0L, 0L, 1))
                    addRow(arrayOf<Any?>(2L, 8L, "Vidéo B", null, null, null, 0L, 0L, 1))
                }
                else -> null
            }
        })
        // Sans le launcher Google, personne n'approuve les chaînes : elles restent affichées (masquables dans les réglages).
        val channels = source.channels()
        assertEquals(listOf(7L, 8L), channels.map { it.id })
        assertEquals("Vidéo A", channels.first().programs.single().title)
        assertEquals("com.youtube", channels.first().programs.single().packageName)
        assertTrue(source.hasAccess())
    }

    @Test
    fun liveChannelsKeepTunerChannelsOnlySortedByNumber() {
        val source = TvProviderEpgSource(EpgQuery { uri, _ ->
            if (uri != TvContractCompat.Channels.CONTENT_URI) null
            else MatrixCursor(arrayOf("_id", "package_name", "display_name", "display_number", "type", "input_id", "browsable")).apply {
                addRow(arrayOf<Any?>(1L, "com.youtube", "Tendances", null, TvContractCompat.Channels.TYPE_PREVIEW, null, 1))
                addRow(arrayOf<Any?>(2L, "com.tuner", "France 5", "10", "TYPE_DVB_T", "com.tuner/.Input", 1))
                addRow(arrayOf<Any?>(3L, "com.tuner", "TF1", "1", "TYPE_DVB_T", "com.tuner/.Input", 1))
                addRow(arrayOf<Any?>(4L, "com.tuner", "Masquée", "2", "TYPE_DVB_T", "com.tuner/.Input", 0))
                addRow(arrayOf<Any?>(5L, "com.tuner", "", "3", "TYPE_DVB_T", "com.tuner/.Input", 1))
            }
        })
        val live = source.liveChannels()
        assertEquals(listOf("TF1", "Chaîne 3", "France 5"), live.map { it.name })
        assertEquals(TvContractCompat.buildChannelUri(3L).toString(), live.first().uri)
    }

    @Test
    fun noTunerMeansNoLiveChannels() {
        val source = TvProviderEpgSource(EpgQuery { _, _ -> throw SecurityException("no EPG access") })
        assertEquals(emptyList<LiveChannel>(), source.liveChannels())
    }

    @Test
    fun liveChannelSortHandlesSubNumbersAndMissingNumbers() {
        fun ch(n: String?, name: String) = LiveChannel(name, "p", n, name, "u")
        val sorted = sortLiveChannels(listOf(ch(null, "Z"), ch("5.2", "B"), ch("5.1", "A"), ch("12", "C")))
        assertEquals(listOf("A", "B", "C", "Z"), sorted.map { it.name })
    }
}
