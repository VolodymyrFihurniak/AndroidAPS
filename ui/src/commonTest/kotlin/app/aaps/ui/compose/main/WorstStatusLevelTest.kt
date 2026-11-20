package app.aaps.ui.compose.main

import app.aaps.core.ui.compose.StatusLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class WorstStatusLevelTest {

    @Test
    fun criticalWinsOverEverything() {
        assertEquals(StatusLevel.CRITICAL, worstStatusLevel(StatusLevel.NORMAL, StatusLevel.WARNING, StatusLevel.CRITICAL, null))
    }

    @Test
    fun warningWinsOverNormal() {
        assertEquals(StatusLevel.WARNING, worstStatusLevel(StatusLevel.NORMAL, null, StatusLevel.WARNING, StatusLevel.UNSPECIFIED))
    }

    @Test
    fun normalWhenNothingIsWrong() {
        assertEquals(StatusLevel.NORMAL, worstStatusLevel(StatusLevel.UNSPECIFIED, StatusLevel.NORMAL, null))
    }

    @Test
    fun unknownWhenNothingIsKnown() {
        assertEquals(StatusLevel.UNSPECIFIED, worstStatusLevel(null, StatusLevel.UNSPECIFIED))
        assertEquals(StatusLevel.UNSPECIFIED, worstStatusLevel())
    }
}
