package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * autoISF build: three small additions to the AutoISF loop result, ported from iAPS
 * (github.com/Artificial-Pancreas/iAPS, MIT licence, (c) Ivan Valkou and the iAPS contributors):
 * `aisfBasal` in OpenAPS.swift (keto protection), `b30` in autoisf.js and `autocr` in determine-basal.js.
 *
 * All three are pure functions, so the rules can be tested without the plugin. They are off by default.
 */
object AutoIsfExtras {

    /** Length of the temp basal that keto protection and B30 ask for. */
    const val TEMP_MINUTES = 30

    data class KetoSettings(
        val enabled: Boolean,
        /** true: only when IOB is below minus one hour of basal. false: always instead of a zero temp. */
        val onlyWithNegativeIob: Boolean,
        /** Rate in percent of the profile basal, limited to 5..50 %. */
        val percent: Int,
        /** Fixed rate in U/h, limited to 0..2 U/h. Used instead of [percent] when above 0. */
        val absoluteRate: Double
    )

    /**
     * Keto protection: a zero temp that runs for a long time can lead to ketoacidosis. When the loop asks for
     * no insulin at all, give a small safety basal instead.
     *
     * Returns the safety rate in U/h, or null when the loop result stays as it is.
     *
     * Guards (iAPS has the first five, the last one is added in this build):
     * - the setting is on and the profile basal is above 0,
     * - the loop gives no SMB in this run,
     * - the rate the loop wants is 0 U/h,
     * - with [KetoSettings.onlyWithNegativeIob]: IOB is below minus one hour of profile basal,
     * - the safety rate is not above max basal,
     * - BG is not below [minBgMgdl] (the low end of the target). Below the target a zero temp is right.
     */
    fun ketoRate(
        settings: KetoSettings,
        iob: Double,
        profileBasal: Double,
        wantedRate: Double,
        smbUnits: Double,
        bgMgdl: Double,
        minBgMgdl: Double,
        maxBasal: Double
    ): Double? {
        if (!settings.enabled) return null
        if (profileBasal <= 0.0) return null
        if (smbUnits > 0.0) return null
        if (wantedRate > 0.0) return null
        if (bgMgdl < minBgMgdl) return null
        if (settings.onlyWithNegativeIob && iob >= -profileBasal) return null
        val rate =
            if (settings.absoluteRate > 0.0) settings.absoluteRate.coerceIn(0.0, 2.0)
            else profileBasal * settings.percent.coerceIn(5, 50) / 100.0
        val limited = min(round2(rate), maxBasal)
        return if (limited > 0.0) limited else null
    }

    data class B30Settings(
        val enabled: Boolean,
        /** A manual bolus of at least this many units starts B30. */
        val minBolus: Double,
        /** B30 needs a temp target below this value (mg/dL). */
        val targetBelowMgdl: Double,
        /** SMBs stay off while BG is below this value (mg/dL) and delta is not above [upperDeltaMgdl]. */
        val upperBgMgdl: Double,
        val upperDeltaMgdl: Double,
        /** B30 rate = profile basal * factor. */
        val factor: Double,
        /** Minutes after the bolus that B30 runs. */
        val durationMinutes: Int
    )

    data class B30Result(
        val rate: Double,
        val minutesLeft: Int,
        /** true: the SMB of this run must be dropped (iAPS: "SMBs disabled (B30)"). */
        val stopSmb: Boolean
    )

    /**
     * B30: after a manual meal bolus with a low temp target, run a high temp basal for some minutes, so more
     * insulin is active early. iAPS replaces the loop rate in any case. This build is more careful:
     * B30 never lowers what the loop wants, and it does not run when the loop asks for less than the
     * profile basal (then the loop sees a reason to hold back insulin, for example a predicted low).
     *
     * @param minutesSinceBolus minutes since the newest manual (not SMB) bolus, null when there is none.
     * @param wantedRate the rate the loop asks for, or the running rate when the loop keeps the current temp.
     */
    fun b30(
        settings: B30Settings,
        tempTargetMgdl: Double?,
        lastBolusUnits: Double,
        minutesSinceBolus: Double?,
        profileBasal: Double,
        wantedRate: Double,
        bgMgdl: Double,
        deltaMgdl: Double,
        maxBasal: Double
    ): B30Result? {
        if (!settings.enabled) return null
        if (tempTargetMgdl == null || tempTargetMgdl >= settings.targetBelowMgdl) return null
        if (minutesSinceBolus == null || minutesSinceBolus < 0.0 || minutesSinceBolus > settings.durationMinutes) return null
        if (lastBolusUnits < settings.minBolus) return null
        if (profileBasal <= 0.0) return null
        if (wantedRate < profileBasal) return null
        val rate = min(round2(profileBasal * settings.factor), maxBasal)
        if (rate <= wantedRate) return null
        val stopSmb = deltaMgdl <= settings.upperDeltaMgdl && bgMgdl < settings.upperBgMgdl
        val left = max(0, (settings.durationMinutes - minutesSinceBolus).toInt())
        return B30Result(rate = rate, minutesLeft = left, stopSmb = stopSmb)
    }

    /**
     * Auto CR: change the carb ratio by the same factor as AutoISF changes the ISF (iAPS: `carb_ratio / aisf`).
     * The carbs effect (ISF / CR) then stays the one of the profile, so COB predictions do not move when
     * AutoISF makes the ISF stronger. Only the loop uses it, the bolus wizard keeps the profile CR.
     */
    fun autoCr(profileCr: Double, profileIsf: Double, autoIsf: Double): Double {
        if (profileCr <= 0.0 || profileIsf <= 0.0 || autoIsf <= 0.0) return profileCr
        if (!autoIsf.isFinite()) return profileCr
        return round(profileCr * autoIsf / profileIsf * 10.0) / 10.0
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
