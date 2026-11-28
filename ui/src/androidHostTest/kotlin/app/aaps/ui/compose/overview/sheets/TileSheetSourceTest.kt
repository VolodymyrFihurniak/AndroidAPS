package app.aaps.ui.compose.overview.sheets

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.RM
import app.aaps.core.keys.IntKey
import app.aaps.ui.compose.testing.TileSheetSourceFixture
import app.aaps.ui.compose.testing.setAapsContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * autoISF build: what the basal sheet works out from stored data, and that an open sheet reads it again. The data is
 * the made up day of [TileSheetSourceFixture].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TileSheetSourceTest {

    @get:Rule
    val compose = createComposeRule()

    private val fixture = TileSheetSourceFixture()
    private val minute = 60_000L

    private fun basal(): BasalSheetState = runBlocking { fixture.source().basalSheet() }

    @Test
    fun runningTempBasalIsTheRateNow() {
        val s = basal()
        assertEquals("0.94", s.rateText)
        assertTrue(s.percentText, s.percentText.contains("110"))
        assertNotNull(s.runningProgress)
        // 10 of 30 minutes are done
        assertEquals(1f / 3f, s.runningProgress!!, 0.01f)
        assertEquals(0.85, s.profileRate, 0.0001)
    }

    @Test
    fun chartHoldsSixHoursBackAndThreeAhead() {
        val c = basal().chart
        // Every 5 minutes, both ends included
        assertEquals(109, c.profile.size)
        assertEquals(109, c.given.size)
        assertEquals(72, c.nowIndex)
        // Now: the running temp basal, 110 % of 0.85
        assertEquals(0.935, c.given[c.nowIndex]!!, 0.001)
        // The temp basal runs 20 more minutes: points now+5 .. now+15 are given, the one after is not known
        assertNotNull(c.given[c.nowIndex + 3])
        assertNull(c.given[c.nowIndex + 4])
        // The loop gave 0 % from 17:20 to 17:50 (240 to 210 minutes ago)
        for (i in 24 until 30) assertEquals("point $i", 0.0, c.given[i]!!, 0.0001)
        // The profile line is the profile: 0.85 U/h all through the afternoon
        assertEquals(0.85, c.profile[10], 0.0001)
        // The hour marks of the axis
        assertTrue(c.ticks.isNotEmpty())
    }

    @Test
    fun todayIsTheGivenBasalAgainstTheProfile() {
        val s = basal()
        // The profile: 0.80 U/h for 12 h and 0.85 U/h for 8 h 40 min
        assertEquals(0.80 * 12 + 0.85 * (8 + 40 / 60.0), number(s.todayProfileText), 0.02)
        // The loop's temp basals: the sum of (percent - 100) of every 10 minutes, times 0.85 U/h, and the running 110 %
        val above = listOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            40, 40, 0, -100, -100, -100, -20, 0, 0, 0,
            0, 0, 60, 120, 160, 100, 40, 0, 0, -40,
            -100, -100, -100, 0, 20, 0
        ).sum() / 100.0 * 0.85 * 10 / 60.0 + 0.10 * 0.85 * 10 / 60.0
        assertEquals(above, number(s.todayDifferenceText), 0.03)
    }

    @Test
    fun noTempBasalRunsTheProfile() {
        fixture.noTbrNow()
        val s = basal()
        assertEquals("0.85", s.rateText)
        assertEquals("", s.percentText)
        assertNull(s.runningProgress)
        assertEquals("", s.warningText)
        // The profile changes at 22:00
        assertTrue(s.nextChangeText, s.nextChangeText.contains("22:00"))
    }

    @Test
    fun aQuietPumpIsNoWarning() {
        // The loop did not call the pump for 20 minutes, which is normal
        fixture.pumpAnswered(minutesAgo = 20)
        assertEquals("", basal().warningText)
    }

    @Test
    fun oldPumpDataIsSaid() {
        fixture.pumpAnswered(minutesAgo = 45)
        val s = basal()
        assertTrue(s.warningText, s.warningText.contains("45"))
    }

    @Test
    fun theUnreachableTimeOfTheSettingsIsKept() {
        whenever(fixture.screen.preferences.get(IntKey.AlertsPumpUnreachableThreshold)).thenReturn(60)
        fixture.pumpAnswered(minutesAgo = 45)
        assertEquals("", basal().warningText)
        fixture.pumpAnswered(minutesAgo = 65)
        assertTrue(basal().warningText.isNotEmpty())
    }

    @Test
    fun aPumpThatNeverAnsweredIsSaid() {
        whenever(fixture.overview.activePump.lastDataTime).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0L))
        assertTrue(basal().warningText.isNotEmpty())
    }

    @Test
    fun aSuspendedPumpGivesNothing() {
        fixture.noTbrNow()
        runBlocking {
            whenever(fixture.overview.persistenceLayer.getRunningModeActiveAtOrNull(org.mockito.kotlin.any()))
                .thenReturn(RM(timestamp = fixture.now - 5 * minute, mode = RM.Mode.SUSPENDED_BY_PUMP, duration = 0))
        }
        val s = basal()
        assertEquals(0.0, number(s.rateText), 0.0)
        assertTrue(s.warningText.isNotEmpty())
    }

    @Test
    fun anOpenSheetReadsItsStateAgain() {
        var reads = 0
        compose.mainClock.autoAdvance = false
        compose.setAapsContent(fixture.screen) {
            LoadingSheet(load = { reads++; "read $reads" }, onDismiss = {}) { text -> Text(text) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // The sheet is still sliding in: it exists, it may not be fully on the screen yet
        compose.onNodeWithText("read 1").assertExists()
        compose.mainClock.advanceTimeBy(SHEET_REFRESH_MS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("read 2").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(SHEET_REFRESH_MS)
        compose.waitForIdle()
        compose.onNodeWithText("read 3").assertIsDisplayed()
    }

    @Test
    fun aFailedReadKeepsTheLastState() {
        var reads = 0
        compose.mainClock.autoAdvance = false
        compose.setAapsContent(fixture.screen) {
            LoadingSheet(
                load = { reads++; if (reads == 2) error("read failed") else "read $reads" },
                onDismiss = {}
            ) { text -> Text(text) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SHEET_REFRESH_MS + 1_000)
        compose.waitForIdle()
        // The second read failed: the first state stays
        compose.onNodeWithText("read 1").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(SHEET_REFRESH_MS)
        compose.waitForIdle()
        compose.onNodeWithText("read 3").assertIsDisplayed()
    }

    /** The number in a text like "-0.70 U" */
    private fun number(text: String): Double = Regex("-?\\d+[.,]\\d+").find(text)!!.value.replace(',', '.').toDouble()
}
