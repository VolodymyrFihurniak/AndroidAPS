package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.roundToInt

/**
 * autoISF build, personal prediction: the plugin's own forecasts of the last [KEEP_MS], one every 5 minutes, for the
 * live check ([LiveScore]). About 90 kB, in a file of the plugin.
 */
@SingleIn(AppScope::class)
@Inject
class ForecastArchive(preferences: Preferences, files: LearnedFiles) {

    private val kept = KeptRecords(files, LearnedFiles.FORECASTS, preferences, LearnedStringKey.Forecasts, KEEP_MS, { it.timestamp }, ::encodeForecast, ::decodeForecast)

    /** Keeps [forecast] unless one was kept less than 5 minutes before it */
    suspend fun add(forecast: LearnedForecast, now: Long) {
        val record = KeptForecast.of(forecast) ?: return
        kept.update(now) { forecasts -> with(kept) { forecasts.addSpaced(record) } }
    }

    /** The kept forecasts of [from]..[to], oldest first */
    suspend fun range(from: Long, to: Long): List<KeptForecast> = kept.range(from, to)

    /** Saves now, for example when the plugin stops */
    suspend fun save() = kept.save()

    companion object {

        /** The 7 days of the live check, and the 2 hours after the last forecast it can score */
        const val KEEP_MS = LiveScore.WINDOW_MS + 86_400_000L

        private const val MINUTE_MS = 60_000L

        /** "minute,bg,linear 30,60,120[,gru 30,60,120]", BG in whole mg/dl */
        fun encodeForecast(forecast: KeptForecast): String =
            (listOf(forecast.timestamp / MINUTE_MS) + (listOf(forecast.bg) + forecast.linear + forecast.gru.orEmpty()).map { it.roundToInt() })
                .joinToString(",")

        /** Null when it cannot be read */
        fun decodeForecast(entry: String): KeptForecast? {
            val parts = entry.split(',')
            if (parts.size != 5 && parts.size != 8) return null
            val minute = parts[0].toLongOrNull() ?: return null
            val values = parts.drop(1).map { it.toDoubleOrNull() ?: return null }
            return KeptForecast(minute * MINUTE_MS, values[0], values.subList(1, 4), if (values.size == 7) values.subList(4, 7) else null)
        }
    }
}
