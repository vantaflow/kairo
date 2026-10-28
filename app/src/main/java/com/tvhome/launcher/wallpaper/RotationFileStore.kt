package com.tvhome.launcher.wallpaper

import java.io.File

/**
 * Liste d'images Pixabay en cours, gardée dans un petit fichier :
 * 1re ligne = index suivant et date, 2e ligne = thème, puis une URL par ligne.
 */
class RotationFileStore(private val file: File) {
    @Synchronized
    fun load(): RotationState = runCatching {
        val lines = file.readLines()
        val (index, fetchedAt) = lines.first().split(' ').let { it[0].toInt() to (it.getOrNull(1)?.toLong() ?: 0L) }
        RotationState(lines.drop(2).filter { it.isNotBlank() }, index, fetchedAt, lines.getOrElse(1) { "" })
    }.getOrElse { RotationState(emptyList(), 0) }

    @Synchronized
    fun store(state: RotationState) {
        runCatching { file.writeText((listOf("${state.nextIndex} ${state.fetchedAtMs}", state.query) + state.urls).joinToString("\n")) }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}
