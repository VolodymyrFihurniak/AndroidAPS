package app.aaps.plugins.aps.learned

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.db.PersistenceLayer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class HistoryReaderTest {

    private val hour = 3_600_000L
    private val day0 = 1_767_225_600_000L
    private val day1 = day0 + 24 * hour
    private val iCfg = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)

    private fun eps(timestamp: Long, isf: Double) = EPS(
        timestamp = timestamp,
        basalBlocks = listOf(Block(24 * hour, 0.8)),
        isfBlocks = listOf(Block(24 * hour, isf)),
        icBlocks = listOf(Block(24 * hour, 10.0)),
        targetBlocks = listOf(TargetBlock(24 * hour, 90.0, 110.0)),
        glucoseUnit = GlucoseUnit.MGDL,
        originalProfileName = "", originalCustomizedName = "", originalTimeshift = 0, originalPercentage = 100,
        originalDuration = 0, originalEnd = 0, iCfg = iCfg
    )

    private fun persistence(
        switches: List<EPS> = emptyList(),
        boluses: List<BS> = emptyList(),
        tempBasals: List<TB> = emptyList(),
        runs: List<APSResult> = emptyList(),
        bg: List<GV> = emptyList()
    ): PersistenceLayer = mock {
        // Both ends count, as in the database
        on { getBgReadingsDataFromTimeToTime(any(), any(), any()) } doAnswer { call ->
            bg.filter { it.timestamp >= call.getArgument<Long>(0) && it.timestamp <= call.getArgument<Long>(1) }
        }
        on { getBolusesFromTimeToTime(any(), any(), any()) } doReturn boluses
        on { getTemporaryBasalsStartingFromTimeToTime(any(), any(), any()) } doReturn tempBasals
        on { getExtendedBolusesStartingFromTimeToTime(any(), any(), any()) } doReturn emptyList()
        on { getCarbsFromTimeToTimeExpanded(any(), any(), any()) } doReturn emptyList()
        on { getEffectiveProfileSwitchesFromTimeToTime(any(), any(), any()) } doReturn switches
        on { getEffectiveProfileSwitchActiveAt(any()) } doReturn null
        on { getTherapyEventDataFromToTime(any(), any()) } doReturn emptyList()
        on { getValidCalibrationEntriesSince(any()) } doReturn emptyList()
        on { getTemporaryTargetActiveAt(any()) } doReturn null
        on { getApsResults(any(), any()) } doReturn runs
    }

    @Test
    fun `profile timeline uses the switch active at the time`() = runBlocking {
        val reader = HistoryReader(persistence(switches = listOf(eps(day0, 50.0), eps(day1, 70.0))))
        val input = reader.read(day0 - hour, day1 + 2 * hour)
        assertThat(input.profile.valuesAt(day0 + hour)?.isfMgdl).isEqualTo(50.0)
        assertThat(input.profile.valuesAt(day1 + hour)?.isfMgdl).isEqualTo(70.0)
        assertThat(input.profile.valuesAt(day0 - 1)).isNull()
        assertThat(input.profile.valuesAt(day0 + hour)?.targetMgdl).isEqualTo(100.0)
        assertThat(input.profile.valuesAt(day0 + hour)?.basal).isEqualTo(0.8)
    }

    @Test
    fun `live read keeps the planned end of the running temp basal`() = runBlocking {
        val now = day0 + 10 * hour
        val running = TB(timestamp = now - 10 * 60_000L, type = TB.Type.NORMAL, isAbsolute = true, rate = 0.0, duration = 30 * 60_000L)
        val reader = HistoryReader(persistence(switches = listOf(eps(day0, 50.0)), tempBasals = listOf(running)))
        val input = reader.readLive(now)
        assertThat(input.tempBasals.single().end).isEqualTo(now + 20 * 60_000L)
    }

    @Test
    fun `live read has 7 days of BG and reads the older part once an hour`() = runBlocking<Unit> {
        val now = day0 + 8 * 24 * hour
        val readings = (0..8 * 24 * 12).map { k ->
            GV(timestamp = now - k * 5 * 60_000L, raw = null, value = 100.0, trendArrow = TrendArrow.FLAT, noise = null, sourceSensor = SourceSensor.UNKNOWN)
        }
        val persistence = persistence(switches = listOf(eps(day0, 50.0)), bg = readings)
        val reader = HistoryReader(persistence)
        val first = reader.readLive(now)
        assertThat(first.bg.minOf { it.timestamp }).isAtMost(now - 7 * 24 * hour - hour)
        assertThat(first.bg.map { it.timestamp }).containsNoDuplicates()

        // 10 minutes later only the live part is read again
        clearInvocations(persistence)
        val later = reader.readLive(now + 10 * 60_000L)
        verify(persistence, times(1)).getBgReadingsDataFromTimeToTime(any(), any(), any())
        // No reading twice and none missing where the older part meets the live part
        val times = later.bg.map { it.timestamp }.sorted()
        assertThat(times.zipWithNext { a, b -> b - a }.distinct()).containsExactly(5 * 60_000L)
        assertThat(times.last()).isEqualTo(now)

        // After an hour the older part is read again
        clearInvocations(persistence)
        reader.readLive(now + hour)
        verify(persistence, atLeast(2)).getBgReadingsDataFromTimeToTime(any(), any(), any())
    }

    @Test
    fun `stored run main curve prefers COB when carbs are on board`() = runBlocking {
        val rt = RT(runningDynamicIsf = false, COB = 20.0, predBGs = Predictions(IOB = listOf(100, 101, 102), COB = listOf(100, 110, 120)))
        val withCarbs: APSResult = mock {
            on { date } doReturn day0
            on { algorithmResult } doReturn rt
        }
        val noCarbs: APSResult = mock {
            on { date } doReturn day0 + hour
            on { algorithmResult } doReturn rt.copy(COB = 0.0)
        }
        val reader = HistoryReader(persistence(runs = listOf(withCarbs, noCarbs)))
        val runs = reader.storedRuns(day0, day0 + 2 * hour).distinctBy { it.timestamp }
        assertThat(runs.first { it.timestamp == day0 }.mainCurve).containsExactly(110.0, 120.0).inOrder()
        assertThat(runs.first { it.timestamp == day0 + hour }.mainCurve).containsExactly(101.0, 102.0).inOrder()
    }

    @Test
    fun `priming the cannula is not insulin in the body`() = runBlocking<Unit> {
        val boluses = listOf(
            BS(timestamp = day0 + hour, amount = 0.5, type = BS.Type.PRIMING, iCfg = iCfg),
            BS(timestamp = day0 + 2 * hour, amount = 1.0, type = BS.Type.NORMAL, iCfg = iCfg),
            BS(timestamp = day0 + 3 * hour, amount = 0.1, type = BS.Type.SMB, iCfg = iCfg)
        )
        val input = HistoryReader(persistence(switches = listOf(eps(day0, 50.0)), boluses = boluses)).read(day0, day0 + 6 * hour)
        assertThat(input.doses.map { it.units }).containsExactly(1.0, 0.1)
    }
}
