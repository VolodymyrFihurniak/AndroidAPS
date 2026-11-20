package app.aaps.ui.compose.overview

import app.aaps.ui.compose.overview.chips.BolusAdviceKind
import app.aaps.ui.compose.overview.chips.bolusAdviceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** autoISF build: the advice of the Bolus card from the result of the bolus wizard */
class BolusAdviceTest {

    @Test
    fun aDoseThatCanBeGivenIsAnInsulinAdvice() {
        val advice = bolusAdviceOf(insulin = 0.6, carbsEquivalent = 0.0, loopMayCover = false)

        assertEquals(BolusAdviceKind.INSULIN, advice.kind)
        assertEquals(0.6, advice.insulin)
        assertFalse(advice.loopMayCover)
    }

    @Test
    fun inClosedLoopTheInsulinAdviceSaysTheLoopMayGiveIt() {
        assertTrue(bolusAdviceOf(insulin = 0.6, carbsEquivalent = 0.0, loopMayCover = true).loopMayCover)
    }

    @Test
    fun belowTheTargetItAsksForCarbsInWholeGrams() {
        val advice = bolusAdviceOf(insulin = 0.0, carbsEquivalent = 12.2, loopMayCover = true)

        assertEquals(BolusAdviceKind.CARBS, advice.kind)
        assertEquals(13, advice.carbs)
        // Carbs are never "covered by the loop"
        assertFalse(advice.loopMayCover)
    }

    @Test
    fun lessThanOneGramAndNoInsulinIsNoBolus() {
        assertEquals(BolusAdviceKind.NONE, bolusAdviceOf(insulin = 0.0, carbsEquivalent = 0.4, loopMayCover = false).kind)
        assertEquals(BolusAdviceKind.NONE, bolusAdviceOf(insulin = 0.0, carbsEquivalent = 0.0, loopMayCover = false).kind)
    }
}
