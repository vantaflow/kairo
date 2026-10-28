package com.tvhome.launcher.settings

import java.security.MessageDigest

const val DEFAULT_PROFILE_ID = "default"

/** Profil : favoris, fond et applis masquées propres ; un profil enfant ne voit que ses favoris. */
data class Profile(val id: String, val name: String, val kids: Boolean, val pinHash: String?)

val DefaultProfile = Profile(DEFAULT_PROFILE_ID, "Principal", kids = false, pinHash = null)

fun hashPin(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest("tvhome:$pin".toByteArray())
        .joinToString("") { "%02x".format(it) }

/** Vrai si [input] correspond au PIN ; un profil sans PIN accepte tout. */
fun checkPin(input: String, pinHash: String?): Boolean = pinHash == null || hashPin(input) == pinHash

/** Verrou du PIN : après 5 essais faux, 30 s d'attente (puis le compteur repart). */
class PinGuard(private val now: () -> Long = System::currentTimeMillis) {
    private var failures = 0
    private var lockedUntil = 0L

    fun lockedForMs(): Long = (lockedUntil - now()).coerceAtLeast(0)

    /** Vrai si le PIN est bon et que le verrou n'est pas actif. */
    fun attempt(input: String, pinHash: String?): Boolean {
        if (lockedForMs() > 0) return false
        if (checkPin(input, pinHash)) {
            failures = 0
            return true
        }
        if (++failures >= MAX_FAILURES) {
            failures = 0
            lockedUntil = now() + LOCK_MS
        }
        return false
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 30_000L
    }
}

/** Format stocké : une ligne par profil, champs séparés par une tabulation. */
internal fun encodeProfiles(profiles: List<Profile>): String =
    profiles.joinToString("\n") { listOf(it.id, it.name.replace('\t', ' '), it.kids, it.pinHash.orEmpty()).joinToString("\t") }

internal fun decodeProfiles(raw: String?): List<Profile> =
    raw.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4 || f[0].isBlank()) null else Profile(f[0], f[1], f[2].toBoolean(), f[3].ifBlank { null })
    }.toList()
