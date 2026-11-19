package app.aaps.ui.compose.overview

import app.aaps.core.data.model.RM
import app.aaps.ui.compose.overview.chips.BASAL_DATA_MAX_AGE_MS
import app.aaps.ui.compose.overview.chips.BasalCardState
import app.aaps.ui.compose.overview.chips.BasalDirection
import app.aaps.ui.compose.overview.chips.BasalSnapshot
import app.aaps.ui.compose.overview.chips.BasalStatus
import app.aaps.ui.compose.overview.chips.BasalTbr
import app.aaps.ui.compose.overview.chips.basalCardState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** autoISF build: every state of the basal card on the overview */
class BasalCardTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun state(
        profileBasal: Double? = 0.5,
        tbr: BasalTbr? = null,
        ps: Int = 100,
        timeshift: Int = 0,
        mode: RM.Mode? = RM.Mode.CLOSED_LOOP,
        pumpDataTime: Long = now - minute,
        requestedRate: Double? = null,
        requestedAt: Long = now - minute
    ): BasalCardState = basalCardState(
        BasalSnapshot(
            now = now, profileBasal = profileBasal, tbr = tbr, profileSwitchPercentage = ps, profileSwitchTimeshiftHours = timeshift,
            runningMode = mode, pumpDataTime = pumpDataTime, requestedRate = requestedRate, requestedAt = requestedAt
        )
    )

    @Test
    fun profileBasalIsHundredPercent() {
        val s = state()
        assertEquals(BasalStatus.PROFILE, s.status)
        assertEquals(0.5, s.rate)
        assertEquals(100, s.percentOfProfile)
        assertEquals(BasalDirection.SAME, s.direction)
        assertNull(s.tbrRemainingMinutes)
        assertNull(s.profileSwitchPercentage)
    }

    @Test
    fun higherTbrShowsRateRemainingTimeAndPercent() {
        // 30 min TBR started 18 min ago: 12 min left
        val s = state(tbr = BasalTbr(rate = 0.75, start = now - 18 * minute, durationMs = 30 * minute))
        assertEquals(BasalStatus.TBR, s.status)
        assertEquals(0.75, s.rate)
        assertEquals(150, s.percentOfProfile)
        assertEquals(BasalDirection.HIGHER, s.direction)
        assertEquals(12, s.tbrRemainingMinutes)
    }

    @Test
    fun lowerTbr() {
        val s = state(tbr = BasalTbr(rate = 0.2, start = now - minute, durationMs = 30 * minute))
        assertEquals(40, s.percentOfProfile)
        assertEquals(BasalDirection.LOWER, s.direction)
    }

    @Test
    fun zeroTbrIsZeroPercent() {
        val s = state(tbr = BasalTbr(rate = 0.0, start = now - minute, durationMs = 30 * minute))
        assertEquals(0.0, s.rate)
        assertEquals(0, s.percentOfProfile)
        assertEquals(BasalDirection.LOWER, s.direction)
    }

    @Test
    fun profileSwitchIsKeptApartFromTheTbr() {
        // The profile basal already has the 120 % of the switch in it: 0.5 * 1.2 = 0.6
        val s = state(profileBasal = 0.6, ps = 120, timeshift = 2, tbr = BasalTbr(rate = 0.9, start = now, durationMs = 30 * minute))
        assertEquals(120, s.profileSwitchPercentage)
        assertEquals(2, s.profileSwitchTimeshiftHours)
        // The TBR is compared with the switched profile, not with the switch
        assertEquals(150, s.percentOfProfile)
    }

    @Test
    fun profileSwitchBelowHundredWithoutTbr() {
        val s = state(profileBasal = 0.4, ps = 80)
        assertEquals(BasalStatus.PROFILE, s.status)
        assertEquals(0.4, s.rate)
        assertEquals(100, s.percentOfProfile)
        assertEquals(80, s.profileSwitchPercentage)
    }

    @Test
    fun zeroProfileBasalHasNoPercent() {
        val s = state(profileBasal = 0.0)
        assertEquals(0.0, s.rate)
        assertNull(s.percentOfProfile)
    }

    @Test
    fun suspendedByPumpGivesNothing() {
        val s = state(mode = RM.Mode.SUSPENDED_BY_PUMP, tbr = BasalTbr(rate = 1.0, start = now, durationMs = 30 * minute))
        assertEquals(BasalStatus.SUSPENDED_BY_PUMP, s.status)
        assertEquals(0.0, s.rate)
        assertEquals(0, s.percentOfProfile)
        assertNull(s.tbrRemainingMinutes)
    }

    @Test
    fun disconnectedPumpIsShownEvenWithoutRecentData() {
        val s = state(mode = RM.Mode.DISCONNECTED_PUMP, pumpDataTime = now - 2 * BASAL_DATA_MAX_AGE_MS)
        assertEquals(BasalStatus.PUMP_DISCONNECTED, s.status)
        assertEquals(0.0, s.rate)
    }

    @Test
    fun oldPumpDataIsNotShownAsTheBasalNow() {
        val s = state(pumpDataTime = now - BASAL_DATA_MAX_AGE_MS - 1, tbr = BasalTbr(rate = 0.75, start = now, durationMs = 30 * minute))
        assertEquals(BasalStatus.NO_RECENT_DATA, s.status)
        assertNull(s.rate)
        assertNull(s.percentOfProfile)
        assertEquals(15L, s.dataAgeMinutes)
    }

    @Test
    fun pumpThatNeverGaveDataIsNotKnown() {
        val s = state(pumpDataTime = 0L)
        assertEquals(BasalStatus.NO_RECENT_DATA, s.status)
        assertNull(s.rate)
        assertNull(s.dataAgeMinutes)
    }

    @Test
    fun noProfile() {
        val s = state(profileBasal = null)
        assertEquals(BasalStatus.NO_PROFILE, s.status)
        assertNull(s.rate)
        assertNull(s.percentOfProfile)
    }

    @Test
    fun newProfileSegmentChangesThePercent() {
        // The same TBR against two profile segments: the card is worked out again with the new basal
        val tbr = BasalTbr(rate = 0.6, start = now, durationMs = 30 * minute)
        assertEquals(120, state(profileBasal = 0.5, tbr = tbr).percentOfProfile)
        assertEquals(75, state(profileBasal = 0.8, tbr = tbr).percentOfProfile)
    }

    @Test
    fun loopRequestNotSetYetIsShownApart() {
        val s = state(requestedRate = 0.9)
        assertEquals(0.5, s.rate)
        assertEquals(0.9, s.pendingRate)
    }

    @Test
    fun loopRequestThatMatchesTheRateIsNotPending() {
        assertNull(state(requestedRate = 0.5).pendingRate)
    }

    @Test
    fun oldLoopRequestIsNotPending() {
        assertNull(state(requestedRate = 0.9, requestedAt = now - 16 * minute).pendingRate)
    }

    @Test
    fun noPendingRequestWhileTheRateIsNotKnown() {
        assertNull(state(requestedRate = 0.9, pumpDataTime = 0L).pendingRate)
    }
}
