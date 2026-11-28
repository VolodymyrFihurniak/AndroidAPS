package app.aaps.ui.compose.overview.sheets

import app.aaps.core.interfaces.aps.APSResult
import kotlin.math.roundToInt

/*
 * autoISF build: the last hour of runs for the Loop sheet. It is a plain function of the stored results, so a unit test
 * can check it without a database.
 */

/** The strip covers this many minutes, in steps of [RUN_STRIP_STEP_MINUTES]. */
internal const val RUN_STRIP_MINUTES = 60
internal const val RUN_STRIP_STEP_MINUTES = 5

/** A temp basal the algorithm asked for without a duration counts for this long. */
private const val DEFAULT_TBR_MINUTES = 30
private const val MINUTE_MS = 60_000L

/** The share of the profile basal the run asked for, null when it asked for no temp basal or the share is not known. */
internal fun requestedPercent(result: APSResult, profileBasal: Double?): Int? = when {
    !result.isTempBasalRequested               -> null
    result.usePercent                          -> result.percent
    profileBasal != null && profileBasal > 0.0 -> (result.rate / profileBasal * 100).roundToInt()
    else                                       -> null
}

/**
 * Builds the strip of the last [RUN_STRIP_MINUTES] before [now] from the stored [results] (any order, older ones help to
 * know the temp basal that was running at the start).
 *
 * A step shows the temp basal the algorithm asked for. A run that asks for no change keeps the temp basal it asked for
 * before, until that one ends; after that the profile runs (100). A step without a run stays empty. The SMB of a step is
 * the sum of the runs in it. [basalAt] gives the profile basal at a time, U/h, or null when there is no profile.
 */
internal fun buildRunStrip(
    results: List<APSResult>,
    now: Long,
    basalAt: (Long) -> Double?,
    hours: List<SheetTimeTick>,
    startText: String,
    endText: String
): LoopRunStrip {
    val steps = RUN_STRIP_MINUTES / RUN_STRIP_STEP_MINUTES
    val stepMs = RUN_STRIP_STEP_MINUTES * MINUTE_MS
    val start = now - RUN_STRIP_MINUTES * MINUTE_MS
    val percent = MutableList<Int?>(steps) { null }
    val smb = MutableList(steps) { 0.0 }
    var asked = 100
    var askedUntil = Long.MIN_VALUE
    results.filter { it.date <= now }.sortedBy { it.date }.forEach { result ->
        val share = requestedPercent(result, basalAt(result.date))
        if (share != null) {
            asked = share
            askedUntil = result.date + (if (result.duration > 0) result.duration else DEFAULT_TBR_MINUTES) * MINUTE_MS
        }
        if (result.date >= start) {
            val step = ((result.date - start) / stepMs).toInt().coerceIn(0, steps - 1)
            percent[step] = if (result.date < askedUntil) asked else 100
            smb[step] += result.smb
        }
    }
    return LoopRunStrip(percent, smb, RUN_STRIP_MINUTES, startText, endText, hours)
}
