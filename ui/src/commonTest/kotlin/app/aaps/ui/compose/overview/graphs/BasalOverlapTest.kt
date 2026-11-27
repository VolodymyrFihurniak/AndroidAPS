package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * autoISF build: the part of the basal band the profile and the given basal share. The band is drawn downwards, so
 * the values are negative and the shared part is the one closer to 0.
 */
class BasalOverlapTest {

    @Test
    fun higherTbrSharesTheProfile() {
        // Profile 0.5 the whole time, a TBR of 0.8 from minute 10 to 40
        val profile = listOf(0.0 to 0.5, 60.0 to 0.5)
        val actual = listOf(0.0 to 0.5, 10.0 to 0.8, 40.0 to 0.5, 60.0 to 0.5)

        assertEquals(listOf(0.0 to 0.5, 10.0 to 0.5, 40.0 to 0.5, 60.0 to 0.5), basalOverlap(profile, actual))
    }

    @Test
    fun lowerTbrSharesTheTbr() {
        val profile = listOf(0.0 to 0.5, 60.0 to 0.5)
        val actual = listOf(0.0 to 0.5, 10.0 to 0.2, 40.0 to 0.5, 60.0 to 0.5)

        assertEquals(listOf(0.0 to 0.5, 10.0 to 0.2, 40.0 to 0.5, 60.0 to 0.5), basalOverlap(profile, actual))
    }

    @Test
    fun aProfileStepInsideATbrIsFollowed() {
        // The profile goes from 0.5 to 1.0 at minute 30, during a 0.8 TBR
        val profile = listOf(0.0 to 0.5, 30.0 to 1.0, 60.0 to 1.0)
        val actual = listOf(0.0 to 0.8, 60.0 to 0.8)

        assertEquals(listOf(0.0 to 0.5, 30.0 to 0.8, 60.0 to 0.8), basalOverlap(profile, actual))
    }

    @Test
    fun noOverlapWithoutBothSeries() {
        assertTrue(basalOverlap(emptyList(), listOf(0.0 to 0.5)).isEmpty())
        assertTrue(basalOverlap(listOf(0.0 to 0.5), emptyList()).isEmpty())
    }
}
