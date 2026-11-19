package app.aaps.ui.compose.overview.chips

import app.aaps.core.data.model.RM
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * autoISF build: the basal card of the overview, worked out without any text or Android, so every state can be
 * tested on its own.
 *
 * Nothing here decides anything about insulin. It only reads what AAPS already stored: the profile, the running
 * temp basal (TBR), the running mode and when the pump last answered.
 */

/** Pump data older than this is not shown as the basal running now: three loop runs. */
const val BASAL_DATA_MAX_AGE_MS = 15 * 60_000L

/** A loop request is shown as not done yet only while it is this new. */
const val BASAL_REQUEST_MAX_AGE_MS = 15 * 60_000L

/** Rates closer than this are the same rate (the pumps round to 0.01 U/h or coarser). */
private const val SAME_RATE = 0.01

/** The temp basal running on the pump, as stored by AAPS after the pump confirmed it. */
data class BasalTbr(
    /** Absolute rate in U/h, also for a percent TBR or an extended bolus used as a TBR. */
    val rate: Double,
    val start: Long,
    val durationMs: Long
)

/** What the basal card is built from. Read in one place (`ChipsViewModel`). */
data class BasalSnapshot(
    val now: Long,
    /** Basal of the active profile now in U/h, with the percentage of a profile switch in it. Null without a profile. */
    val profileBasal: Double?,
    /** The running TBR, null when the pump runs the profile. */
    val tbr: BasalTbr?,
    /** Percentage of the active profile switch, 100 when there is none. */
    val profileSwitchPercentage: Int = 100,
    /** Time shift of the active profile switch in hours. */
    val profileSwitchTimeshiftHours: Int = 0,
    val runningMode: RM.Mode? = null,
    /** When the pump (on a client: the pump of the master) last gave data. 0 when never. */
    val pumpDataTime: Long,
    /** A TBR the loop asked for and the pump has not set yet, in U/h. */
    val requestedRate: Double? = null,
    val requestedAt: Long = 0L
)

enum class BasalStatus {

    /** The pump runs the basal of the active profile. */
    PROFILE,

    /** A temp basal runs. */
    TBR,

    /** The pump stopped all delivery by itself. */
    SUSPENDED_BY_PUMP,

    /** The user disconnected the pump: AAPS gives a zero temp basal. */
    PUMP_DISCONNECTED,

    /** The pump did not give data for [BASAL_DATA_MAX_AGE_MS]: the rate is not known. */
    NO_RECENT_DATA,

    /** No active profile: nothing to compare with. */
    NO_PROFILE
}

/** Whether the rate is above, below or equal to the profile. */
enum class BasalDirection { HIGHER, LOWER, SAME }

data class BasalCardState(
    val status: BasalStatus,
    /** U/h given now, null when it is not known (shown as "—"). */
    val rate: Double?,
    /** [rate] as percent of the profile basal now, null when it cannot be worked out (shown as "—"). */
    val percentOfProfile: Int?,
    val direction: BasalDirection,
    /** The profile basal now in U/h, null without a profile. */
    val profileBasal: Double?,
    /** Percentage of the active profile switch, null when it is 100 %. */
    val profileSwitchPercentage: Int?,
    /** Time shift of the active profile switch in hours, null when there is none. */
    val profileSwitchTimeshiftHours: Int?,
    /** Minutes left of the running TBR, null when none runs. */
    val tbrRemainingMinutes: Int?,
    /** Minutes since the pump gave data, null when it never did. */
    val dataAgeMinutes: Long?,
    /** A loop request the pump has not set yet, in U/h. */
    val pendingRate: Double?
)

fun basalCardState(s: BasalSnapshot): BasalCardState {
    val dataAgeMinutes = if (s.pumpDataTime > 0) ((s.now - s.pumpDataTime).coerceAtLeast(0L) / 60_000L) else null
    val fresh = s.pumpDataTime > 0 && s.now - s.pumpDataTime <= BASAL_DATA_MAX_AGE_MS
    val profileBasal = s.profileBasal
    val tbrRemaining = s.tbr?.let { ceil((it.start + it.durationMs - s.now).coerceAtLeast(0L) / 60_000.0).toInt() }
    val psPercentage = s.profileSwitchPercentage.takeIf { it != 100 }
    val psTimeshift = s.profileSwitchTimeshiftHours.takeIf { it != 0 }

    val status: BasalStatus
    val rate: Double?
    when {
        profileBasal == null                          -> { status = BasalStatus.NO_PROFILE; rate = null }
        // Before the age check: a disconnected pump is not expected to answer, and nothing goes into the body
        s.runningMode == RM.Mode.DISCONNECTED_PUMP    -> { status = BasalStatus.PUMP_DISCONNECTED; rate = 0.0 }
        !fresh                                        -> { status = BasalStatus.NO_RECENT_DATA; rate = null }
        s.runningMode == RM.Mode.SUSPENDED_BY_PUMP    -> { status = BasalStatus.SUSPENDED_BY_PUMP; rate = 0.0 }
        s.tbr != null                                 -> { status = BasalStatus.TBR; rate = s.tbr.rate }
        else                                          -> { status = BasalStatus.PROFILE; rate = profileBasal }
    }
    val percent = if (rate != null && profileBasal != null && profileBasal > 0.0) (rate / profileBasal * 100).roundToInt() else null
    val direction = when {
        rate == null || profileBasal == null -> BasalDirection.SAME
        rate > profileBasal + SAME_RATE      -> BasalDirection.HIGHER
        rate < profileBasal - SAME_RATE      -> BasalDirection.LOWER
        else                                 -> BasalDirection.SAME
    }
    val pending = s.requestedRate?.takeIf {
        (status == BasalStatus.PROFILE || status == BasalStatus.TBR) &&
            s.now - s.requestedAt <= BASAL_REQUEST_MAX_AGE_MS &&
            rate != null && abs(it - rate) >= SAME_RATE
    }
    return BasalCardState(
        status = status,
        rate = rate,
        percentOfProfile = percent,
        direction = direction,
        profileBasal = profileBasal,
        profileSwitchPercentage = psPercentage,
        profileSwitchTimeshiftHours = psTimeshift,
        tbrRemainingMinutes = if (status == BasalStatus.TBR) tbrRemaining else null,
        dataAgeMinutes = dataAgeMinutes,
        pendingRate = pending
    )
}
