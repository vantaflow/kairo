package com.vantaflow.kairo.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vantaflow.kairo.R
import com.vantaflow.kairo.i18n.AppLocale

/** Langues proposées : chaque nom est écrit dans sa propre langue (sauf « Langue de la box »). */
private val LanguageNames = listOf(
    "fr" to "Français",
    "en" to "English",
    "es" to "Español",
    "ar" to "العربية",
)

/** Réglages › Langue : langue de la box (par défaut) ou une langue traduite, appliquée aussitôt. */
@Composable
fun LanguageSettings() {
    val context = LocalContext.current
    val current = remember { AppLocale.saved(context) }
    val systemName = remember {
        AppLocale.systemLocale().let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingHint(stringResource(R.string.language_hint))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val choices = listOf(AppLocale.SYSTEM to null) + LanguageNames
            items(choices, key = { it.first.ifEmpty { "system" } }) { (code, name) ->
                ListItem(
                    selected = code == current,
                    onClick = { AppLocale.set(context, code) },
                    headlineContent = { Text(name ?: stringResource(R.string.language_system), maxLines = 1) },
                    supportingContent = if (name == null) ({ Text(systemName, maxLines = 1) }) else null,
                    trailingContent = if (code == current) ({ Text("✓") }) else null,
                )
            }
        }
    }
}
