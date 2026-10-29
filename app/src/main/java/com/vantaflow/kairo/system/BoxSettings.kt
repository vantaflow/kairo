package com.vantaflow.kairo.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Paramètres Android de la box : un raccourci par écran système. */
@Composable
fun BoxSettings() {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Ces raccourcis ouvrent les écrans des paramètres Android de la box.", color = OnBackgroundMuted) }
        items(SettingsShortcuts, key = { it.title }) { s ->
            ListItem(selected = false, onClick = { context.openSettings(s) }, headlineContent = { Text(s.title) })
        }
    }
}
