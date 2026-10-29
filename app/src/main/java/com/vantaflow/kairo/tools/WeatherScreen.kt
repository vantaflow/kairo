package com.vantaflow.kairo.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.settings.SmallButton
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.weather.DayForecast
import com.vantaflow.kairo.weather.Forecast
import com.vantaflow.kairo.weather.HourForecast
import com.vantaflow.kairo.weather.uvLabel
import com.vantaflow.kairo.weather.weatherEmoji
import com.vantaflow.kairo.weather.weatherLabel
import com.vantaflow.kairo.weather.windDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DayName = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)
private val HourMin = DateTimeFormatter.ofPattern("HH:mm")
private val Glass = Color.White.copy(alpha = 0.12f)
private val GlassShape = RoundedCornerShape(22.dp)
private val Dim = Color.White.copy(alpha = 0.72f)

/** Les 24 heures affichées pour un jour (aujourd'hui : les 24 h à venir). */
fun hourlyFor(f: Forecast, day: Int): List<HourForecast> {
    val date = f.days.getOrNull(day)?.date ?: return emptyList()
    val start = if (day == 0) f.now.withMinute(0) else date.atStartOfDay()
    return f.hours.filter { !it.time.isBefore(start) }.take(24)
}

/** Heures étiquetées : toutes les 3 h. */
fun hoursFor(f: Forecast, day: Int): List<HourForecast> = hourlyFor(f, day).filterIndexed { i, _ -> i % 3 == 0 }.take(8)

/** Détails d'un jour (icône, libellé, valeur) ; aujourd'hui, en plus, pression et nuages. */
fun dayDetails(f: Forecast, day: Int): List<Triple<String, String, String>> {
    val d = f.days.getOrNull(day) ?: return emptyList()
    return buildList {
        d.sunrise?.let { add(Triple("🌅", "Lever", it.format(HourMin))) }
        d.sunset?.let { add(Triple("🌇", "Coucher", it.format(HourMin))) }
        add(Triple("☀️", "Indice UV", "${d.uvMax.roundToInt()} · ${uvLabel(d.uvMax)}"))
        add(Triple("💧", "Pluie", if (d.rainMm >= 0.1) "${"%.1f".format(Locale.FRENCH, d.rainMm)} mm" else "${d.rainChance} %"))
        add(Triple("💨", "Vent", "${d.windMax.roundToInt()} km/h ${windDirection(d.windDir)}".trim()))
        add(Triple("🌬️", "Rafales", "${d.gustMax.roundToInt()} km/h"))
        if (day == 0) {
            if (f.pressure > 0) add(Triple("🧭", "Pression", "${f.pressure} hPa"))
            if (f.cloudCover >= 0) add(Triple("☁️", "Nuages", "${f.cloudCover} %"))
        }
    }
}

/** Ciel du fond : bleu le jour, nuit étoilée, gris quand il pleut, orage sombre. */
private fun skyFor(code: Int, day: Boolean): List<Color> = when {
    code in 95..99 -> listOf(Color(0xFF151827), Color(0xFF3A3550))
    code in 51..67 || code in 80..82 -> listOf(Color(0xFF1E2A36), Color(0xFF4A5A6B))
    code in 71..77 || code == 85 || code == 86 -> listOf(Color(0xFF34445A), Color(0xFF8396AA))
    !day -> listOf(Color(0xFF070B1F), Color(0xFF233163))
    code >= 2 -> listOf(Color(0xFF213D5E), Color(0xFF6E8BA8))
    else -> listOf(Color(0xFF0E4E8F), Color(0xFF4FA3D8))
}

/** Écran Météo : maintenant, détails, courbe heure par heure et 7 jours (← → change le jour). */
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

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            // Halo de lumière en haut à gauche (fixe, aucune animation).
            .drawBehind {
                val c = Offset(size.width * 0.12f, size.height * 0.1f)
                val r = size.width * 0.45f
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = c, radius = r), radius = r, center = c)
            },
    ) {
        when {
            f != null -> Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Now(f, Modifier.width(430.dp).fillMaxHeight())
                    DetailsGrid(dayDetails(f, day), Modifier.weight(1f).fillMaxHeight())
                }
                Column(Modifier.fillMaxWidth().weight(1f).background(Glass, GlassShape).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        when (day) {
                            0 -> "Les prochaines 24 heures"
                            1 -> "Demain, heure par heure"
                            else -> f.days[day].date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() } + ", heure par heure"
                        },
                        fontSize = 18.sp, color = Dim,
                    )
                    TemperatureCurve(hourlyFor(f, day), Modifier.fillMaxSize())
                }
                val lo = f.days.minOf { it.min }
                val hi = f.days.maxOf { it.max }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    f.days.forEachIndexed { i, d ->
                        DayCard(
                            d, i, selected = i == day, lo = lo, hi = hi,
                            modifier = Modifier.weight(1f)
                                .then(if (i == 0) Modifier.focusRequester(firstDay) else Modifier)
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

/** Carte « maintenant » : ville, grande température, ciel, ressenti et min / max du jour. */
@Composable
private fun Now(f: Forecast, modifier: Modifier) {
    val today = f.days.firstOrNull()
    Column(modifier.background(Glass, GlassShape).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(f.place, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        Text(
            f.now.format(DateTimeFormatter.ofPattern("EEEE d MMMM · HH:mm", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
            fontSize = 18.sp, color = Dim,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weatherEmoji(f.code, !f.isDay), fontSize = 58.sp)
            Spacer(Modifier.width(12.dp))
            Text("${f.temperature.roundToInt()}°", fontSize = 80.sp, fontWeight = FontWeight.Light, color = Color.White)
            Spacer(Modifier.width(22.dp))
            Column {
                Text(weatherLabel(f.code), fontSize = 22.sp, color = Color.White, maxLines = 1)
                if (today != null) Text("↑ ${today.max.roundToInt()}°   ↓ ${today.min.roundToInt()}°", fontSize = 19.sp, color = Dim)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            buildList {
                add("Ressenti ${f.feelsLike.roundToInt()}°")
                if (f.humidity >= 0) add("Humidité ${f.humidity} %")
            }.joinToString("  ·  "),
            fontSize = 17.sp, color = Dim, maxLines = 1,
        )
    }
}

/** Détails en 2 rangées de cartes vitrées : icône, libellé, valeur. */
@Composable
private fun DetailsGrid(items: List<Triple<String, String, String>>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (icon, label, value) ->
                    Column(Modifier.weight(1f).fillMaxHeight().background(Glass, GlassShape).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp, color = Dim, maxLines = 1)
                        }
                        Text(value, fontSize = 21.sp, color = Color.White, maxLines = 1)
                    }
                }
                // Rangée incomplète : les cartes gardent la même largeur.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Courbe des températures sur 24 h, remplie en dégradé ; toutes les 3 h : heure, symbole et
 * température. Un seul Canvas : changer de jour ne fait que la redessiner.
 */
@Composable
private fun TemperatureCurve(hours: List<HourForecast>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val tempStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    val hourStyle = TextStyle(color = Dim, fontSize = 16.sp)
    val iconStyle = TextStyle(fontSize = 22.sp)
    Canvas(modifier) {
        if (hours.size < 2) return@Canvas
        val lo = hours.minOf { it.temperature }
        val hi = hours.maxOf { it.temperature }
        val span = (hi - lo).coerceAtLeast(1.0)
        val top = 30.dp.toPx()
        val bottom = size.height - 54.dp.toPx()
        val padX = 30.dp.toPx()
        val stepX = (size.width - 2 * padX) / (hours.size - 1)
        val pts = hours.mapIndexed { i, h ->
            Offset(padX + i * stepX, (bottom - 10.dp.toPx() - (h.temperature - lo) / span * (bottom - top - 10.dp.toPx())).toFloat())
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mid = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(mid, pts[i - 1].y, mid, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, bottom)
            lineTo(pts.first().x, bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Color(0x55FFD27A), Color(0x00FFD27A)), startY = top, endY = bottom))
        drawPath(line, Brush.horizontalGradient(listOf(Color(0xFF8FD3FF), Color(0xFFFFD27A), Color(0xFFFF9E6B))), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        hours.forEachIndexed { i, h ->
            if (i % 3 != 0) return@forEachIndexed
            val p = pts[i]
            drawCircle(Color.White, radius = 5.dp.toPx(), center = p)
            val t = measurer.measure("${h.temperature.roundToInt()}°", tempStyle)
            drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y - t.size.height - 6.dp.toPx()))
            // Bas : symbole puis heure, sous la courbe.
            val hr = measurer.measure("${h.time.hour} h", hourStyle)
            drawText(hr, topLeft = Offset(p.x - hr.size.width / 2, size.height - hr.size.height))
            val e = measurer.measure(weatherEmoji(h.code, h.time.hour < 7 || h.time.hour >= 20), iconStyle)
            drawText(e, topLeft = Offset(p.x - e.size.width / 2, size.height - hr.size.height - e.size.height))
        }
    }
}

/** Carte d'un jour : nom, symbole, min / max et barre de l'écart sur l'échelle de la semaine. */
@Composable
private fun DayCard(d: DayForecast, index: Int, selected: Boolean, lo: Double, hi: Double, modifier: Modifier) {
    val name = when (index) {
        0 -> "Aujourd'hui"
        1 -> "Demain"
        else -> d.date.format(DayName).replaceFirstChar { it.uppercase() }.removeSuffix(".")
    }
    Surface(
        onClick = {},
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = GlassShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (selected) 0.24f else 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF101216),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(weatherEmoji(d.code), fontSize = 28.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${d.min.roundToInt()}°", fontSize = 19.sp)
                Text("${d.max.roundToInt()}°", fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            val span = (hi - lo).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color.Black.copy(alpha = 0.18f), cornerRadius = r)
                val x0 = ((d.min - lo) / span * size.width).toFloat()
                val x1 = ((d.max - lo) / span * size.width).toFloat()
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF8FD3FF), Color(0xFFFF9E6B)), startX = 0f, endX = size.width),
                    topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(size.height), size.height), cornerRadius = r,
                )
            }
            Text(if (d.rainChance >= 20) "💧 ${d.rainChance} %" else weatherLabel(d.code), fontSize = 13.sp, maxLines = 1)
        }
    }
}
