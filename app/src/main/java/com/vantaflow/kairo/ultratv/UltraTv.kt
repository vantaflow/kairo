package com.vantaflow.kairo.ultratv

// Chaînes d'Ultra TV lues dans le fournisseur que l'appli réserve à Kairo.
// Logique pure : la lecture du curseur et l'affichage sont ailleurs. L'autre appli évolue
// (colonnes en plus, en moins, nulles ou d'un type inattendu) : rien ici ne lève d'exception.

const val ULTRA_TV_RELEASE = "com.ultratv.tv.nativeapp"
const val ULTRA_TV_DEBUG = "com.ultratv.tv.nativeapp.debug"

fun ultraTvAuthority(pkg: String) = "$pkg.kairo"

/** Paquet Ultra TV à interroger : la version publiée d'abord, sinon celle de mise au point. */
fun pickUltraTvPackage(installed: Set<String>): String? = when {
    ULTRA_TV_RELEASE in installed -> ULTRA_TV_RELEASE
    ULTRA_TV_DEBUG in installed -> ULTRA_TV_DEBUG
    else -> null
}

// Colonnes du fournisseur
const val COL_PROVIDER_ID = "provider_id"
const val COL_REMOTE_ID = "remote_id"
const val COL_NAME = "name"
const val COL_NUMBER = "number"
const val COL_LOGO = "logo"
const val COL_NOW_TITLE = "now_title"
const val COL_NOW_START_MS = "now_start_ms"
const val COL_NOW_END_MS = "now_end_ms"
const val COL_NOW_IMAGE = "now_image"
const val COL_NEXT_TITLE = "next_title"
const val COL_NEXT_START_MS = "next_start_ms"
const val COL_NEXT_END_MS = "next_end_ms"
const val COL_DEEP_LINK = "deep_link"
const val COL_LOCKED = "locked"
const val COL_KIDS = "kids"

val ALL_COLUMNS = listOf(
    COL_PROVIDER_ID, COL_REMOTE_ID, COL_NAME, COL_NUMBER, COL_LOGO,
    COL_NOW_TITLE, COL_NOW_START_MS, COL_NOW_END_MS, COL_NOW_IMAGE,
    COL_NEXT_TITLE, COL_NEXT_START_MS, COL_NEXT_END_MS, COL_DEEP_LINK, COL_LOCKED,
)

private const val DEEP_LINK_SCHEME = "ultratv://"

data class UtvChannel(
    val providerId: Long,
    val remoteId: String,
    val name: String,
    val number: String?,
    val logo: String?,
    val nowTitle: String?,
    val nowStartMs: Long,
    val nowEndMs: Long,
    val nowImage: String?,
    val nextTitle: String?,
    val nextStartMs: Long,
    val nextEndMs: Long,
    val deepLink: String,
    val locked: Boolean,
)

/** Sections d'Ultra TV ouvrables directement depuis l'accueil. */
enum class UtvSection(val uri: String) {
    Guide("ultratv://guide"),
    Live("ultratv://live"),
    Movies("ultratv://movies"),
    Series("ultratv://series"),
}

// Texte : chaîne ou entier ; vide ou fait d'espaces → null ; tout autre type → null
private fun textOf(v: Any?): String? = when (v) {
    is String -> v.trim().ifEmpty { null }
    is Long, is Int -> v.toString()
    else -> null
}

// Nombre : Long, Int ou texte lisible ; tout le reste → 0
private fun longOf(v: Any?): Long = when (v) {
    is Long -> v
    is Int -> v.toLong()
    is String -> v.trim().toLongOrNull() ?: 0L
    else -> 0L
}

// Indicateur (verrou, mode enfants) : en cas de doute, on penche vers le côté prudent (vrai).
// Booléen tel quel ; nombre non nul ; texte « true » / « yes » (casse et espaces ignorés) ou nombre non nul.
private fun flagOf(v: Any?): Boolean = when (v) {
    is Boolean -> v
    is Number -> v.toDouble() != 0.0
    is String -> v.trim().lowercase().let { t -> t == "true" || t == "yes" || (t.toDoubleOrNull()?.let { it != 0.0 } ?: false) }
    else -> false
}

/** Longueur maximale d'un lien (deep_link, logo, image) ; au-delà, la valeur est refusée. */
private const val MAX_URL_LENGTH = 2_000

/** Longueur maximale d'un texte affiché (nom, numéro, titres) ; au-delà, il est coupé. */
private const val MAX_TEXT_LENGTH = 200

private val CONTROL_CHARS = Regex("[\\x00-\\x1F]")

// Texte affiché : caractères de contrôle remplacés par des espaces, coupé sans séparer une paire de substitution.
private fun shortTextOf(v: Any?): String? {
    val text = textOf(v)?.replace(CONTROL_CHARS, " ")?.trim()?.ifEmpty { null } ?: return null
    if (text.length <= MAX_TEXT_LENGTH) return text
    val end = if (text[MAX_TEXT_LENGTH - 1].isHighSurrogate()) MAX_TEXT_LENGTH - 1 else MAX_TEXT_LENGTH
    return text.substring(0, end)
}

private fun urlOf(v: Any?): String? = textOf(v)?.takeIf { it.length <= MAX_URL_LENGTH }

/** Une ligne du curseur (nom de colonne → valeur) ; null si elle n'a pas de nom ou de lien Ultra TV. */
fun utvChannelOf(row: Map<String, Any?>): UtvChannel? = try {
    channelOf(row)
} catch (e: Exception) {
    // Seules les exceptions : une Error (mémoire épuisée…) n'est pas masquée.
    null
}

private fun channelOf(row: Map<String, Any?>): UtvChannel? {
    val name = shortTextOf(row[COL_NAME]) ?: return null
    val deepLink = textOf(row[COL_DEEP_LINK]) ?: return null
    if (!deepLink.startsWith(DEEP_LINK_SCHEME)) return null
    // Lien trop long ou avec caractère de contrôle : ligne refusée.
    if (deepLink.length > MAX_URL_LENGTH || deepLink.any { it.code < 32 }) return null
    return UtvChannel(
        providerId = longOf(row[COL_PROVIDER_ID]),
        remoteId = textOf(row[COL_REMOTE_ID]) ?: "",
        name = name,
        number = shortTextOf(row[COL_NUMBER]),
        logo = urlOf(row[COL_LOGO]),
        nowTitle = shortTextOf(row[COL_NOW_TITLE]),
        nowStartMs = longOf(row[COL_NOW_START_MS]),
        nowEndMs = longOf(row[COL_NOW_END_MS]),
        nowImage = urlOf(row[COL_NOW_IMAGE]),
        nextTitle = shortTextOf(row[COL_NEXT_TITLE]),
        nextStartMs = longOf(row[COL_NEXT_START_MS]),
        nextEndMs = longOf(row[COL_NEXT_END_MS]),
        deepLink = deepLink,
        locked = flagOf(row[COL_LOCKED]),
    )
}

/** Avancement du programme en cours, de 0 à 100 ; null si la chaîne est verrouillée ou l'horaire inconnu. */
fun nowProgressPercent(channel: UtvChannel, nowMs: Long): Int? {
    val start = channel.nowStartMs
    val end = channel.nowEndMs
    if (channel.locked || start == 0L || end == 0L || end <= start) return null
    if (nowMs < start || nowMs > end) return null
    // Calcul en flottant : pas de dépassement quelles que soient les valeurs reçues
    val ratio = (nowMs.toDouble() - start.toDouble()) / (end.toDouble() - start.toDouble())
    return (ratio * 100).toInt().coerceIn(0, 100)
}

/** Mode enfants d'Ultra TV (ligne absente → false). */
fun utvKidsOf(row: Map<String, Any?>?): Boolean = row != null && flagOf(row[COL_KIDS])
