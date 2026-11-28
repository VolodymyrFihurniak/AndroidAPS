package app.aaps.ui.compose.testing

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.UiStringsValues
import app.aaps.ui.compose.overview.sheets.TileSheetSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * autoISF build: a real [TileSheetSource] over made up data, so a sheet can be tested and drawn from what the app
 * really works out (and not from numbers typed into a test).
 *
 * The day: 28 Nov 2026, 20:40 UTC. The profile basal is 0.80 U/h until 12:00, 0.85 until 22:00 and 0.90 after.
 * The loop has set a temp basal every 10 minutes since 14:30, the last one (30 minutes) 10 minutes ago.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TileSheetSourceFixture(val screen: AapsScreenFixture = AapsScreenFixture()) {

    val overview = OverviewViewModelFixture(screen)
    val profile: EffectiveProfile = mock()

    /** The temp basal that runs now, null when none runs */
    var running: TB? = null
    private val stored = mutableListOf<TB>()

    private val minute = 60_000L
    val now = NOW
    val midnight = now - now % DAY

    init {
        whenever(screen.dateUtil.now()).thenReturn(now)
        whenever(screen.dateUtil.beginOfDay(any())).thenAnswer { (it.arguments[0] as Long).let { t -> t - t % DAY } }
        whenever(screen.dateUtil.timeString(any<Long>())).thenAnswer {
            val minutes = ((it.arguments[0] as Long) % DAY / minute).toInt()
            "%02d:%02d".format(minutes / 60, minutes % 60)
        }
        whenever(profile.getBasal(any<Long>())).thenAnswer {
            val hour = ((it.arguments[0] as Long) % DAY / (60 * minute)).toInt()
            when {
                hour < 12 -> 0.80
                hour < 22 -> 0.85
                else      -> 0.90
            }
        }
        whenever(profile.getBasal()).thenReturn(0.85)
        runBlocking {
            whenever(overview.profileFunction.getProfile()).thenReturn(profile)
            whenever(overview.loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
            whenever(overview.persistenceLayer.getRunningModeActiveAtOrNull(any())).thenReturn(null)
            whenever(overview.persistenceLayer.getTemporaryBasalsStartingFromTime(any(), any())).thenAnswer { stored.toList() }
            whenever(overview.processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenAnswer { running }
        }
        overview.pumpDescription.isTempBasalCapable = true
        pumpAnswered(minutesAgo = 2)
        loopDay()
    }

    /** The pump gave data this many minutes ago */
    fun pumpAnswered(minutesAgo: Long) {
        whenever(overview.activePump.lastDataTime).thenReturn(MutableStateFlow(now - minutesAgo * minute))
    }

    /** A percent temp basal that started [startMinutesAgo] minutes ago and lasts [durationMinutes] */
    fun percentTbr(startMinutesAgo: Long, durationMinutes: Long, percent: Int): TB =
        TB(timestamp = now - startMinutesAgo * minute, type = TB.Type.NORMAL, isAbsolute = false, rate = percent.toDouble(), duration = durationMinutes * minute)

    /**
     * The loop of the afternoon: a temp basal every 10 minutes from 14:30 (36 of them, each cut by the next), high after
     * a meal, zero while the SMBs work, then the last one with [runningPercent] and 30 minutes, started 10 minutes ago.
     */
    fun loopDay(runningPercent: Int = 110) {
        stored.clear()
        val percents = listOf(
            100, 100, 100, 100, 100, 100, 100, 100, 100, 100,
            140, 140, 100, 0, 0, 0, 80, 100, 100, 100,
            100, 100, 160, 220, 260, 200, 140, 100, 100, 60,
            0, 0, 0, 100, 120, 100
        )
        var startAgo = 370L
        for (p in percents) {
            stored += percentTbr(startAgo, 10, p)
            startAgo -= 10
        }
        val tbr = percentTbr(startAgo, 30, runningPercent)
        stored += tbr
        running = tbr
    }

    /** No temp basal runs now: the last one ended when the loop's last 10 minutes began */
    fun noTbrNow() {
        loopDay()
        stored.removeAt(stored.lastIndex)
        running = null
    }

    fun source(): TileSheetSource = TileSheetSource(
        overview.persistenceLayer, overview.profileFunction, screen.profileUtil, mock(), overview.activePlugin, overview.loop,
        overview.iobCobCalculator, overview.processedTbrEbData, screen.preferences, screen.dateUtil,
        generatedTextResolver(overview.rh, "ui" to UiStringsValues::textOf), screen.decimalFormatter, overview.batchExecutor,
        mock(), overview.rxBus, screen.config, mock(), mock(), mock(), mock(), overview.processedDeviceStatusData,
        CoroutineScope(UnconfinedTestDispatcher())
    )

    companion object {

        /** 28 Nov 2026, 20:40 UTC */
        const val NOW = 1_795_898_400_000L
        const val DAY = 86_400_000L
    }
}
