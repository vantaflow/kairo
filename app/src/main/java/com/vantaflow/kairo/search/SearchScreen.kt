package com.vantaflow.kairo.search

import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.MaterialTheme
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.compose.foundation.layout.Spacer
import com.vantaflow.kairo.ui.TvIcon
import com.vantaflow.kairo.ui.TvIconImage
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.home.AppTile
import com.vantaflow.kairo.reco.ProgramCard
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Recherche globale : applis installées, contenus recommandés, puis recherche dans les applis vidéo. */
@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    programs: List<Program>,
    appLabels: Map<String, String>,
    onLaunchApp: (AppInfo) -> Unit,
    onProgramClick: (Program) -> Unit,
    onSearchIn: (SearchTarget) -> Unit,
    startWithVoice: Boolean = false,
    /** Texte déjà saisi (tapé sur le téléphone). */
    initialQuery: String = "",
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val field = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
        }
    }
    fun listen() {
        runCatching {
            voice.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR"),
            )
        }
    }
    // Texte venu du téléphone : focus sur le premier résultat, sans ouvrir le clavier de la box.
    val firstResult = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        when {
            startWithVoice -> listen()
            initialQuery.isNotBlank() -> repeat(10) {
                withFrameNanos { }
                if (runCatching { firstResult.requestFocus() }.isSuccess) return@LaunchedEffect
            }.also { runCatching { field.requestFocus() } }
            else -> runCatching { field.requestFocus() }
        }
    }

    val foundApps = searchApps(query, apps)
    val foundPrograms = searchPrograms(query, programs)
    val installed = apps.map { it.packageName }.toSet()
    val pm = LocalContext.current.packageManager
    val targets = if (query.isBlank()) emptyList() else searchTargets(query, installed) { pm.resolveActivity(it, 0) != null }

    LazyColumn(
        Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 72.dp, end = 72.dp, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rechercher", fontSize = 32.sp, color = OnBackground)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = OnBackground, fontSize = 22.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .focusRequester(field)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        // Bas : vers les résultats (le champ ne garde pas la flèche pour lui).
                        .onPreviewKeyEvent { e ->
                            if (e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                if (e.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else {
                                false
                            }
                        }
                        .weight(1f)
                        .background(Color.White.copy(alpha = if (fieldFocused) 0.14f else 0.08f), RoundedCornerShape(12.dp))
                        .border(2.dp, if (fieldFocused) accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                )
                Button(onClick = ::listen) {
                    TvIconImage(TvIcon.Mic, LocalContentColor.current, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Dicter", maxLines = 1, softWrap = false)
                }
            }
        }
        if (query.isNotBlank() && foundApps.isEmpty() && foundPrograms.isEmpty() && targets.isEmpty()) {
            item { Text("Aucun résultat pour « $query ».", color = OnBackgroundMuted) }
        }
        if (query.isBlank()) {
            item {
                Text(
                    "Tapez le nom d'une appli ou d'un film, ou appuyez sur Dicter (ou sur la touche Recherche de la télécommande).",
                    color = OnBackgroundMuted,
                    fontSize = 18.sp,
                )
            }
        }
        if (foundApps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Applications", fontSize = 20.sp, color = OnBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        itemsIndexed(foundApps, key = { _, app -> app.packageName }) { i, app ->
                            AppTile(
                                app = app, onClick = { onLaunchApp(app) }, onLongClick = {},
                                modifier = if (i == 0) Modifier.focusRequester(firstResult) else Modifier,
                            )
                        }
                    }
                }
            }
        }
        if (foundPrograms.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Contenus", fontSize = 20.sp, color = OnBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        itemsIndexed(foundPrograms, key = { _, p -> "${p.packageName}:${p.id}" }) { i, p ->
                            ProgramCard(
                                p, appLabels[p.packageName], onClick = { onProgramClick(p) },
                                modifier = if (i == 0 && foundApps.isEmpty()) Modifier.focusRequester(firstResult) else Modifier,
                            )
                        }
                    }
                }
            }
        }
        if (targets.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chercher « $query » dans…", fontSize = 20.sp, color = OnBackground)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        targets.forEach { t -> OutlinedButton(onClick = { onSearchIn(t) }) { Text(t.label) } }
                    }
                }
            }
        }
    }
}
