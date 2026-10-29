package com.vantaflow.kairo.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.settings.SmallButton
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.weather.DayForecast
import com.vantaflow.kairo.weather.Forecast
import com.vantaflow.kairo.weather.HourForecast
import com.vantaflow.kairo.weather.weatherEmoji
import com.vantaflow.kairo.weather.weatherLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DayName = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)

/** Heures affichées pour un jour : toutes les 3 h (aujourd'hui : les 24 h à venir). */
fun hoursFor(f: Forecast, day: Int): List<HourForecast> {
    val date = f.days.getOrNull(day)?.date ?: return emptyList()
    val start = if (day == 0) f.now.withMinute(0) else date.atStartOfDay()
    return f.hours.filter { !it.time.isBefore(start) }.filterIndexed { i, _ -> i % 3 == 0 }.take(8)
}

/** Ciel du fond : bleu le jour, nuit étoilée, gris quand il pleut, orage sombre. */
private fun skyFor(code: Int, day: Boolean): List<Color> = when {
    code in 95..99 -> listOf(Color(0xFF1B1F2E), Color(0xFF3A3550))
    code in 51..67 || code in 80..82 -> listOf(Color(0xFF26323F), Color(0xFF4A5A6B))
    code in 71..77 || code == 85 || code == 86 -> listOf(Color(0xFF3B4A5C), Color(0xFF8396AA))
    !day -> listOf(Color(0xFF0B1026), Color(0xFF233163))
    code >= 2 -> listOf(Color(0xFF2C4A6B), Color(0xFF6E8BA8))
    else -> listOf(Color(0xFF1565A8), Color(0xFF4FA3D8))
}

/** Écran Météo : maintenant, heure par heure et 7 jours (← → change le jour). */
@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var day by remember { mutableIntStateOf(0) }
    val retry = remember { FocusRequester() }
    val firstDay = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        failed = false
        val city = container.prefs.weatherCity.first()
        val fahrenheit = container.prefs.useFahrenheit.first()
        val f = withContext(Dispatchers.IO) { runCatching { container.forecast(city, fahrenheit) }.getOrNull() }
        forecast = f
        failed = f == null
    }

    val f = forecast
    val sky = skyFor(f?.days?.getOrNull(day)?.code ?: f?.code ?: 0, day != 0 || f?.isDay != false)
    val top by animateColorAsState(sky[0], tween(900), label = "ciel")
    val bottom by animateColorAsState(sky[1], tween(900), label = "ciel")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        when {
            f != null -> Column(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp)) {
                Text(f.place, fontSize = 30.sp, color = OnBackground)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherEmoji(f.code, !f.isDay), fontSize = 90.sp)
                    Spacer(Modifier.width(24.dp))
                    Text("${f.temperature.roundToInt()}°", fontSize = 110.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Spacer(Modifier.width(32.dp))
                    Column {
                        Text(weatherLabel(f.code), fontSize = 34.sp, color = Color.White)
                        Text(
                            buildList {
                                add("Ressenti ${f.feelsLike.roundToInt()}°")
                                if (f.humidity >= 0) add("Humidité ${f.humidity} %")
                                add("Vent ${f.windKmh.roundToInt()} km/h")
                            }.joinToString(" · "),
                            fontSize = 22.sp, color = OnBackgroundMuted,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(if (day == 0) "Aujourd'hui, heure par heure" else dayTitle(f.days[day], day), fontSize = 20.sp, color = OnBackgroundMuted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    hoursFor(f, day).forEach { HourCell(it) }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    f.days.forEachIndexed { i, d ->
                        DayCard(
                            d, i, selected = i == day,
                            modifier = Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(firstDay) else Modifier)
                                .onFocusChanged { if (it.isFocused) day = i },
                        )
                    }
                }
                LaunchedEffect(Unit) { runCatching { firstDay.requestFocus() } }
            }
            failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Météo indisponible", fontSize = 34.sp, color = Color.White)
                Text("Vérifiez la connexion, ou la ville choisie dans Réglages › Météo.", fontSize = 20.sp, color = OnBackgroundMuted)
                Box(Modifier.focusRequester(retry)) { SmallButton("Réessayer", { attempt++ }) }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
            }
            else -> Text("Chargement de la météo…", fontSize = 26.sp, color = OnBackgroundMuted, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun dayTitle(d: DayForecast, index: Int) = when (index) {
    1 -> "Demain, heure par heure"
    else -> d.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() } + ", heure par heure"
}

@Composable
private fun HourCell(h: HourForecast) {
    Column(
        Modifier.width(112.dp).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${h.time.hour} h", fontSize = 18.sp, color = OnBackgroundMuted)
        Text(weatherEmoji(h.code, h.time.hour < 7 || h.time.hour >= 20), fontSize = 34.sp)
        Text("${h.temperature.roundToInt()}°", fontSize = 24.sp, color = Color.White)
        Text(if (h.rainChance >= 20) "💧 ${h.rainChance} %" else " ", fontSize = 14.sp, color = OnBackgroundMuted)
    }
}

@Composable
private fun DayCard(d: DayForecast, index: Int, selected: Boolean, modifier: Modifier) {
    val name = when (index) {
        0 -> "Aujourd'hui"
        1 -> "Demain"
        else -> d.date.format(DayName).replaceFirstChar { it.uppercase() }.removeSuffix(".")
    }
    Surface(
        onClick = {},
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (selected) 0.22f else 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF101216),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(weatherEmoji(d.code), fontSize = 40.sp)
            Text("${d.max.roundToInt()}°  ${d.min.roundToInt()}°", fontSize = 24.sp)
            Text(if (d.rainChance >= 20) "💧 ${d.rainChance} %" else weatherLabel(d.code), fontSize = 15.sp, maxLines = 1)
        }
    }
}
