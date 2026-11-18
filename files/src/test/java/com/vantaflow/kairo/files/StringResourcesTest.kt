package com.vantaflow.kairo.files

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Chaque texte de `values/` (français, par défaut) existe en anglais, espagnol et arabe, avec les
 * mêmes paramètres de format (%1$s, %2$d…). Lecture directe des XML : test JVM, sans Android.
 */
class StringResourcesTest {
    private val res = listOf(File("src/main/res"), File("files/src/main/res")).first { it.isDirectory }

    /** Clé → paramètres de format triés ; pour un pluriel, ceux de chaque quantité. */
    private fun load(dir: String): Map<String, List<String>> {
        val files = File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
        val out = mutableMapOf<String, List<String>>()
        for (file in files) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = doc.documentElement
            for (i in 0 until root.childNodes.length) {
                val el = root.childNodes.item(i) as? Element ?: continue
                if (el.getAttribute("translatable") == "false") continue
                val name = el.getAttribute("name")
                when (el.tagName) {
                    "string" -> out[name] = formatArgs(el.textContent)
                    "plurals" -> {
                        val items = el.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            out["$name#${item.getAttribute("quantity")}"] = formatArgs(item.textContent)
                        }
                    }
                }
                check(name !in seen.getOrPut(dir) { mutableSetOf() } || el.tagName == "plurals") { "$dir : clé « $name » en double" }
                seen.getValue(dir).add(name)
            }
        }
        return out
    }

    private val seen = mutableMapOf<String, MutableSet<String>>()

    private fun formatArgs(text: String): List<String> =
        Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXc]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryKeyWithSameFormatArgs() {
        val base = load("values")
        assertTrue("values/strings.xml est vide", base.isNotEmpty())
        val problems = mutableListOf<String>()
        for (lang in listOf("en", "es", "ar")) {
            val tr = load("values-$lang")
            // Pluriels : les quantités varient selon la langue (l'arabe en a six), seule « other » est comparée.
            fun singles(m: Map<String, List<String>>) = m.filterKeys { '#' !in it }
            fun plurals(m: Map<String, List<String>>) = m.keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
            for ((key, args) in singles(base)) {
                val other = tr[key]
                if (other == null) problems += "values-$lang : « $key » manquante"
                else if (other != args) problems += "values-$lang : « $key » a $other au lieu de $args"
            }
            for (key in plurals(base)) {
                val other = tr["$key#other"]
                if (other == null) problems += "values-$lang : pluriel « $key » sans quantité other"
                else if (other != base["$key#other"]) problems += "values-$lang : pluriel « $key » a $other au lieu de ${base["$key#other"]}"
            }
            (singles(tr).keys - singles(base).keys).forEach { problems += "values-$lang : « $it » absente de values" }
            (plurals(tr) - plurals(base)).forEach { problems += "values-$lang : pluriel « $it » absent de values" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun pluralsArgumentsMatchInEveryQuantity() {
        val problems = mutableListOf<String>()
        for (dir in listOf("values", "values-en", "values-es", "values-ar")) {
            val m = load(dir)
            m.keys.filter { '#' in it }.groupBy { it.substringBefore('#') }.forEach { (name, keys) ->
                val other = m["$name#other"] ?: return@forEach
                keys.forEach { k ->
                    // « one » / « zero » peuvent écrire le nombre en toutes lettres : pas plus de paramètres que « other ».
                    if (!other.containsAll(m.getValue(k))) problems += "$dir : $k a ${m[k]} (other : $other)"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
