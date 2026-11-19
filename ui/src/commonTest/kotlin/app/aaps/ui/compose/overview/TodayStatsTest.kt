package app.aaps.ui.compose.overview

import app.aaps.ui.compose.overview.chips.todayStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** autoISF build: the statistics of today on the overview */
class TodayStatsTest {

    @Test
    fun noReadingNoStats() {
        assertNull(todayStats(emptyList(), 70.0, 180.0))
        // Sensor errors only
        assertNull(todayStats(listOf(10.0, 38.0), 70.0, 180.0))
    }

    @Test
    fun timeInRangeAverageAndCv() {
        // 2 of 4 in range; mean 125; sd = sqrt((75² + 25² + 25² + 75²) / 4) = 55.9; cv 44.7 %
        val stats = todayStats(listOf(50.0, 100.0, 150.0, 200.0), 70.0, 180.0)!!
        assertEquals(50, stats.timeInRangePercent)
        assertEquals(125.0, stats.averageMgdl, 1e-9)
        assertEquals(44.72, stats.cvPercent, 0.01)
        assertEquals(4, stats.count)
    }

    @Test
    fun theLimitsAreInRangeAndErrorsDoNotCount() {
        val stats = todayStats(listOf(70.0, 180.0, 20.0), 70.0, 180.0)!!
        assertEquals(100, stats.timeInRangePercent)
        assertEquals(2, stats.count)
    }
}
