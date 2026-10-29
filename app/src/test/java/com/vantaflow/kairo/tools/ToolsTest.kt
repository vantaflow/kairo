package com.vantaflow.kairo.tools

import com.vantaflow.kairo.weather.parseForecast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolsTest {
    private fun forecastJson(): JSONObject {
        val times = (0 until 48).map { "\"2026-10-%02dT%02d:00\"".format(29 + it / 24, it % 24) }
        val temps = (0 until 48).map { it.toString() }
        val codes = (0 until 48).map { "0" }
        return JSONObject(
            """{"current":{"time":"2026-10-29T16:30","temperature_2m":24.1,"apparent_temperature":25.1,
               "relative_humidity_2m":61,"wind_speed_10m":8.0,"weather_code":1,"is_day":1},
               "hourly":{"time":[${times.joinToString()}],"temperature_2m":[${temps.joinToString()}],
                 "weather_code":[${codes.joinToString()}],"precipitation_probability":[${codes.joinToString()}]},
               "daily":{"time":["2026-10-29","2026-10-30"],"weather_code":[1,61],"temperature_2m_max":[25,20],
                 "temperature_2m_min":[15,12],"precipitation_probability_max":[0,80]}}""",
        )
    }

    @Test
    fun parsesForecastAndPicksHours() {
        val f = parseForecast(forecastJson(), "Casablanca")!!
        assertEquals(2, f.days.size)
        assertEquals(80, f.days[1].rainChance)
        // Aujourd'hui : à partir de 16 h, toutes les 3 h.
        assertEquals(listOf(16, 19, 22, 1, 4, 7, 10, 13), hoursFor(f, 0).map { it.time.hour })
        // Demain : la journée entière.
        assertEquals(listOf(0, 3, 6, 9, 12, 15, 18, 21), hoursFor(f, 1).map { it.time.hour })
    }

    @Test
    fun incompleteForecastIsNull() {
        assertNull(parseForecast(JSONObject("""{"current":{"temperature_2m":1}}"""), "X"))
    }

    @Test
    fun formatsMinutes() {
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2 h", formatMinutes(120))
        assertEquals("1 h 30", formatMinutes(90))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sleepTimerWarnsThenSleepsAndCanBeExtended() {
        val scope = TestScope()
        var slept = 0
        val timer = SleepTimer(scope, onSleep = { slept++ }, now = { scope.testScheduler.currentTime })
        timer.start(15)
        assertEquals(15, timer.remainingMinutes())
        scope.advanceTimeBy(14 * 60_000L + 1)
        scope.runCurrent()
        assertEquals(SLEEP_WARNING_SEC, timer.warning.value)
        timer.extend()
        assertNull(timer.warning.value)
        assertEquals(16, timer.remainingMinutes())
        scope.advanceTimeBy(16 * 60_000L + 1_000)
        scope.runCurrent()
        assertEquals(1, slept)
        assertNull(timer.endsAt.value)
    }
}

class QuotesTest {
    @org.junit.Test
    fun sameQuoteAllDayAndChangesNextDay() {
        val d = java.time.LocalDate.of(2026, 10, 29)
        org.junit.Assert.assertEquals(com.vantaflow.kairo.dream.quoteOfDay(d), com.vantaflow.kairo.dream.quoteOfDay(d))
        org.junit.Assert.assertNotEquals(com.vantaflow.kairo.dream.quoteOfDay(d), com.vantaflow.kairo.dream.quoteOfDay(d.plusDays(1)))
    }

    @org.junit.Test
    fun noRepeatWithinAYear() {
        val start = java.time.LocalDate.of(2027, 1, 1)
        val year = (0L until 365L).map { com.vantaflow.kairo.dream.quoteOfDay(start.plusDays(it)).text }
        org.junit.Assert.assertEquals(365, year.toSet().size)
    }
}
