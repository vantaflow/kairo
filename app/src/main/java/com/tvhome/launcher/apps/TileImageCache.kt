package com.tvhome.launcher.apps

/** Cache LRU des images de tuiles, partagé par l'accueil et la grille d'applis. */
class TileImageCache<T : Any>(private val maxEntries: Int = 64) {
    private val entries = LinkedHashMap<String, T>(16, 0.75f, true)

    @Synchronized
    fun getOrLoad(key: String, load: () -> T?): T? {
        entries[key]?.let { return it }
        val value = load() ?: return null
        entries[key] = value
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
        return value
    }

    @Synchronized
    fun clear() = entries.clear()
}
