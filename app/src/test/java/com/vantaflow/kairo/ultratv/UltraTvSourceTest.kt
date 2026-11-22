package com.vantaflow.kairo.ultratv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UltraTvSourceTest {
    /** Avance l'horloge virtuelle jusqu'à [ms] depuis le début du test. */
    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    private fun row(name: String) = mapOf<String, Any?>(
        COL_PROVIDER_ID to 1L,
        COL_NAME to name,
        COL_DEEP_LINK to "ultratv://live/$name",
        COL_NOW_TITLE to "Journal",
    )

    /** Fournisseur en mémoire : réponses par autorité et chemin, appels notés, observateurs comptés. */
    private class FakeProvider {
        val answers = mutableMapOf<String, List<Map<String, Any?>>?>()
        val calls = mutableListOf<String>()
        var latencyMs = 0L
        val observers = mutableMapOf<String, MutableSharedFlow<Unit>>()

        fun answer(authority: String, path: String, rows: List<Map<String, Any?>>?) {
            answers["$authority/$path"] = rows
        }

        val query: suspend (String, String) -> List<Map<String, Any?>>? = { authority, path ->
            calls += "$authority/$path"
            if (latencyMs > 0) delay(latencyMs)
            answers["$authority/$path"]
        }

        val changes: (String) -> MutableSharedFlow<Unit> = { authority ->
            observers.getOrPut(authority) { MutableSharedFlow(extraBufferCapacity = 16) }
        }

        fun observing(authority: String) = observers[authority]?.subscriptionCount?.value ?: 0
        fun notify(authority: String) = observers.getValue(authority).tryEmit(Unit)
        fun count(authority: String, path: String) = calls.count { it == "$authority/$path" }
    }

    /**
     * Source branchée sur [provider] ; la box est allumée depuis [bootMs] au début du test (10 min par
     * défaut : période calme passée) ; [owner] : paquet qui déclare chaque autorité.
     */
    private fun TestScope.source(
        provider: FakeProvider,
        installed: kotlinx.coroutines.flow.Flow<Set<String>>,
        bootMs: Long = 600_000L,
        owner: (String) -> String? = { it.removeSuffix(".kairo") },
    ) = UltraTvSource(installed, provider.query, provider.changes, owner, { bootMs + testScheduler.currentTime }, backgroundScope)

    private val release = ultraTvAuthority(ULTRA_TV_RELEASE)
    private val debug = ultraTvAuthority(ULTRA_TV_DEBUG)

    @Test fun nothingIsReadBeforeStart() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.refresh()
        at(120_000)
        assertEquals(emptyList<String>(), provider.calls)
        assertEquals(UtvState(), source.state.value)
        assertEquals(0, provider.observing(release))
    }

    @Test fun absentAppGivesEmptyStateAndNoQuery() = runTest {
        val provider = FakeProvider()
        val source = source(provider, MutableStateFlow(setOf("org.autre.appli")))
        source.start()
        at(1_000)
        source.refresh()
        at(60_000)
        assertEquals(emptyList<String>(), provider.calls)
        assertEquals(UtvState(), source.state.value)
        assertTrue(provider.observers.isEmpty())
    }

    @Test fun channelsAndKidsAreLoadedAfterStart() = runTest {
        val provider = FakeProvider().apply {
            answer(release, "now", listOf(row("B"), row("A"), mapOf(COL_NAME to "sans lien")))
            answer(release, "profile", listOf(mapOf(COL_KIDS to 1L)))
        }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE, "org.autre.appli")))
        source.start()
        at(0)
        val state = source.state.value
        assertEquals(ULTRA_TV_RELEASE, state.pkg)
        // Ordre du fournisseur, ligne invalide écartée
        assertEquals(listOf("B", "A"), state.channels.map { it.name })
        assertTrue(state.kids)
        assertEquals(listOf("$release/now", "$release/profile"), provider.calls)
        assertEquals(1, provider.observing(release))
    }

    @Test fun failedQueryKeepsPreviousState() = runTest {
        val provider = FakeProvider().apply {
            answer(release, "now", listOf(row("A")))
            answer(release, "profile", listOf(mapOf(COL_KIDS to 1L)))
        }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.start()
        at(0)
        val loaded = source.state.value
        assertEquals(listOf("A"), loaded.channels.map { it.name })
        provider.answer(release, "now", null)
        at(31_000)
        provider.notify(release)
        at(32_000)
        assertEquals(2, provider.count(release, "now"))
        assertEquals(loaded, source.state.value)
        // Aucun nouvel essai sans nouvelle demande
        at(300_000)
        assertEquals(2, provider.count(release, "now"))
    }

    @Test fun slowQueryTimesOutAndKeepsPreviousState() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.start()
        at(0)
        val loaded = source.state.value
        provider.answer(release, "now", listOf(row("B")))
        provider.latencyMs = 15_000
        at(31_000)
        provider.notify(release)
        // Réponse arrivée après 10 s : ignorée
        at(60_000)
        assertEquals(2, provider.count(release, "now"))
        assertEquals(loaded, source.state.value)
        at(300_000)
        assertEquals(2, provider.count(release, "now"))
    }

    @Test fun emptyResultClearsState() = runTest {
        val provider = FakeProvider().apply {
            answer(release, "now", listOf(row("A")))
            answer(release, "profile", listOf(mapOf(COL_KIDS to 1L)))
        }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.start()
        at(0)
        assertEquals(1, source.state.value.channels.size)
        provider.answer(release, "now", emptyList())
        at(31_000)
        provider.notify(release)
        at(32_000)
        assertEquals(UtvState(pkg = ULTRA_TV_RELEASE), source.state.value)
        assertFalse(source.state.value.kids)
    }

    @Test fun changesAreRateLimitedToOnePerThirtySeconds() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.start()
        at(0)
        assertEquals(1, provider.count(release, "now"))
        // Première lecture loin derrière : 5 notifications en 10 s
        for (t in listOf(40_000L, 42_500L, 45_000L, 47_500L, 50_000L)) {
            at(t)
            provider.notify(release)
            runCurrent()
        }
        at(51_000)
        assertEquals(2, provider.count(release, "now"))
        at(69_999)
        assertEquals(2, provider.count(release, "now"))
        at(70_000)
        assertEquals(3, provider.count(release, "now"))
        at(300_000)
        assertEquals(3, provider.count(release, "now"))
    }

    @Test fun uninstallClearsStateAndStopsObserving() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val installed = MutableStateFlow(setOf(ULTRA_TV_RELEASE))
        val source = source(provider, installed)
        source.start()
        at(0)
        assertEquals(1, provider.observing(release))
        installed.value = setOf("org.autre.appli")
        at(1_000)
        assertEquals(UtvState(), source.state.value)
        assertEquals(0, provider.observing(release))
        val before = provider.calls.size
        source.refresh()
        at(120_000)
        assertEquals(before, provider.calls.size)
    }

    @Test fun switchingFromDebugToReleaseUsesReleaseAuthority() = runTest {
        val provider = FakeProvider().apply {
            answer(debug, "now", listOf(row("Debug")))
            answer(release, "now", listOf(row("Release")))
        }
        val installed = MutableStateFlow(setOf(ULTRA_TV_DEBUG))
        val source = source(provider, installed)
        source.start()
        at(0)
        assertEquals(ULTRA_TV_DEBUG, source.state.value.pkg)
        assertEquals(1, provider.observing(debug))
        assertNull(provider.observers[release])
        installed.value = setOf(ULTRA_TV_DEBUG, ULTRA_TV_RELEASE)
        at(1_000)
        assertEquals(ULTRA_TV_RELEASE, source.state.value.pkg)
        assertEquals(listOf("Release"), source.state.value.channels.map { it.name })
        assertEquals(0, provider.observing(debug))
        assertEquals(1, provider.observing(release))
        // Une notification de l'ancienne autorité ne déclenche plus rien
        val before = provider.calls.size
        provider.notify(debug)
        at(120_000)
        assertEquals(before, provider.calls.size)
    }

    @Test fun refreshReadsAtMostOncePerFiveMinutes() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)))
        source.start()
        at(0)
        assertEquals(1, provider.count(release, "now"))
        // Retours sur l'accueil rapprochés : ignorés, sans lecture différée
        for (t in listOf(31_000L, 120_000L, 299_999L)) {
            at(t)
            source.refresh()
            runCurrent()
        }
        at(299_999)
        assertEquals(1, provider.count(release, "now"))
        at(300_000)
        source.refresh()
        runCurrent()
        assertEquals(2, provider.count(release, "now"))
        // Les notifications gardent leur limite de 30 s
        at(330_000)
        provider.notify(release)
        runCurrent()
        assertEquals(3, provider.count(release, "now"))
        // Lecture par notification récente : retour sur l'accueil ignoré
        at(400_000)
        source.refresh()
        at(700_000)
        assertEquals(3, provider.count(release, "now"))
    }

    @Test fun noReadDuringQuietPeriodAfterBoot() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        // Box allumée depuis 40 s au début du test
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)), bootMs = 40_000L)
        source.start()
        at(10_000)
        provider.notify(release)
        source.refresh()
        at(139_999)
        assertEquals(emptyList<String>(), provider.calls)
        assertEquals(UtvState(pkg = ULTRA_TV_RELEASE), source.state.value)
        // 180 s de vie de la box : une seule lecture
        at(140_000)
        assertEquals(1, provider.count(release, "now"))
        assertEquals(listOf("A"), source.state.value.channels.map { it.name })
        at(165_000)
        assertEquals(1, provider.count(release, "now"))
    }

    @Test fun startLongAfterBootReadsImmediately() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)), bootMs = 600_000L)
        source.start()
        runCurrent()
        assertEquals(1, provider.count(release, "now"))
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun authorityOwnedByAnotherAppIsNeverRead() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)), owner = { "org.imposteur" })
        source.start()
        at(0)
        provider.notify(release)
        at(31_000)
        source.refresh()
        at(400_000)
        assertEquals(emptyList<String>(), provider.calls)
        assertEquals(UtvState(), source.state.value)
    }

    @Test fun missingOwnerIsNeverRead() = runTest {
        val provider = FakeProvider().apply { answer(release, "now", listOf(row("A"))) }
        val source = source(provider, MutableStateFlow(setOf(ULTRA_TV_RELEASE)), owner = { null })
        source.start()
        at(60_000)
        assertEquals(emptyList<String>(), provider.calls)
        assertEquals(UtvState(), source.state.value)
    }
}
