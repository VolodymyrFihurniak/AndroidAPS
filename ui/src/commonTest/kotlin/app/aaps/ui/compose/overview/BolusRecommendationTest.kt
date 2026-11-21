package app.aaps.ui.compose.overview

import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.ui.compose.overview.chips.BolusRecommendationInput
import app.aaps.ui.compose.overview.chips.BolusRecommendationKind
import app.aaps.ui.compose.overview.chips.bolusRecommendationOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** autoISF build: the advice of the Bolus card and the Insulin dialog, from the last algorithm run */
class BolusRecommendationTest {

    private val flat = List(48) { 120 }

    private fun rt(insulinReq: Double = 0.0, smb: Double? = null, carbsReq: Int? = null, iob: List<Int> = flat, zt: List<Int>? = null, eventual: Double = 120.0) =
        RT(runningDynamicIsf = false, insulinReq = insulinReq, units = smb, carbsReq = carbsReq, carbsReqWithin = carbsReq?.let { 20 },
           eventualBG = eventual, predBGs = Predictions(IOB = iob, ZT = zt), variable_sens = 54.0)

    private fun input(rt: RT?, bg: Double? = 120.0, smbLoop: Boolean = false, age: Long = 60_000L) =
        BolusRecommendationInput(bgMgdl = bg, lowMgdl = 70.0, result = rt, resultAgeMs = age, loopGivesSmb = smbLoop, maxBolus = 3.0, bolusStep = 0.05)

    @Test
    fun aLowBgNeverGetsADoseEvenWhenTheAlgorithmAsksForInsulin() {
        // The case that started this: 3.9 mmol/l (70 mg/dl is the mark), the wizard said 0.4 U
        assertEquals(BolusRecommendationKind.LOW_NOW, bolusRecommendationOf(input(rt(insulinReq = 0.4), bg = 69.0)).kind)
    }

    @Test
    fun noRunOrAnOldRunIsNoData() {
        assertEquals(BolusRecommendationKind.NO_DATA, bolusRecommendationOf(input(null)).kind)
        assertEquals(BolusRecommendationKind.NO_DATA, bolusRecommendationOf(input(rt(insulinReq = 1.0), age = 20 * 60_000L)).kind)
        assertEquals(BolusRecommendationKind.NO_DATA, bolusRecommendationOf(input(rt(insulinReq = 1.0), bg = null)).kind)
    }

    @Test
    fun carbsAskedByTheAlgorithmComeBeforeAnyInsulin() {
        val advice = bolusRecommendationOf(input(rt(insulinReq = 1.0, carbsReq = 15)))

        assertEquals(BolusRecommendationKind.CARBS, advice.kind)
        assertEquals(15, advice.carbs)
        assertEquals(20, advice.carbsWithinMinutes)
    }

    @Test
    fun anyCurveBelowTheLowMarkInTwoHoursStopsTheDose() {
        // Zero-temp dips to 65 after an hour, IOB stays high: still no dose
        val zt = List(48) { if (it in 12..20) 65 else 120 }
        val advice = bolusRecommendationOf(input(rt(insulinReq = 1.0, zt = zt)))

        assertEquals(BolusRecommendationKind.LOW_PREDICTED, advice.kind)
        assertEquals(65.0, advice.minPredictedMgdl)
    }

    @Test
    fun aDipAfterTwoHoursDoesNotCount() {
        val zt = List(48) { if (it >= 30) 60 else 120 }

        assertEquals(BolusRecommendationKind.SUGGEST, bolusRecommendationOf(input(rt(insulinReq = 1.0, zt = zt))).kind)
    }

    @Test
    fun inClosedLoopWithSmbTheLoopGivesItAndNoDoseIsOffered() {
        val advice = bolusRecommendationOf(input(rt(insulinReq = 1.0, smb = 0.4), smbLoop = true))

        assertEquals(BolusRecommendationKind.LOOP_GIVES, advice.kind)
        assertEquals(0.6, advice.insulinNeeded, 1e-9)
        assertEquals(0.0, advice.dose)
    }

    @Test
    fun inOpenLoopTheDoseIsTheNeedMinusTheSmbFlooredToTheStep() {
        val advice = bolusRecommendationOf(input(rt(insulinReq = 1.0, smb = 0.33)))

        assertEquals(BolusRecommendationKind.SUGGEST, advice.kind)
        assertEquals(0.65, advice.dose, 1e-9)
    }

    @Test
    fun theDoseNeverPassesTheMaxBolus() {
        assertEquals(3.0, bolusRecommendationOf(input(rt(insulinReq = 5.0))).dose, 1e-9)
    }

    @Test
    fun lessThanOneStepIsNothingNeeded() {
        assertEquals(BolusRecommendationKind.NONE, bolusRecommendationOf(input(rt(insulinReq = 0.03))).kind)
        assertEquals(BolusRecommendationKind.NONE, bolusRecommendationOf(input(rt(insulinReq = -0.5))).kind)
    }

    @Test
    fun theDetailsComeFromTheRun() {
        val iob = List(48) { 120 + it }
        val advice = bolusRecommendationOf(input(rt(insulinReq = 1.0, iob = iob, eventual = 150.0)))

        assertEquals(132.0, advice.inOneHourMgdl)
        assertEquals(150.0, advice.eventualMgdl)
        assertEquals(54.0, advice.isfMgdl)
    }
}
