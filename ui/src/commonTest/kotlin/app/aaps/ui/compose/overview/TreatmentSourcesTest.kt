package app.aaps.ui.compose.overview

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.ui.compose.treatments.TreatmentSource
import app.aaps.ui.compose.treatments.TreatmentSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** autoISF build: who gave a bolus or set a temp basal, from the user entry log */
class TreatmentSourcesTest {

    private val t = 1_700_000_000_000L
    private val minute = 60_000L

    private fun bolus(at: Long, amount: Double, type: BS.Type = BS.Type.NORMAL) =
        BS(timestamp = at, amount = amount, type = type, iCfg = ICfg("Test", peak = 75, dia = 5.0, concentration = 1.0))
    private fun entry(at: Long, action: Action, source: Sources, vararg values: ValueWithUnit) =
        UE(timestamp = at, action = action, source = source, note = "", values = values.toList())

    @Test
    fun anSmbIsTheAlgorithmWithoutAnyEntry() {
        assertEquals(TreatmentSource.Algorithm, TreatmentSources(emptyList()).ofBolus(bolus(t, 0.3, BS.Type.SMB)))
    }

    @Test
    fun aBolusFromTheWizardIsTheUserThroughTheWizard() {
        val sources = TreatmentSources(listOf(entry(t - minute, Action.BOLUS, Sources.WizardDialog, ValueWithUnit.Insulin(2.0))))

        assertEquals(TreatmentSource.User(Sources.WizardDialog), sources.ofBolus(bolus(t, 2.0)))
    }

    @Test
    fun aClientBolusCarriesTheDeviceName() {
        val sources = TreatmentSources(
            listOf(entry(t - minute, Action.BOLUS, Sources.NSClient, ValueWithUnit.Insulin(1.5), ValueWithUnit.SimpleString("Pixel 7")))
        )

        assertEquals(TreatmentSource.Client("Pixel 7"), sources.ofBolus(bolus(t, 1.5)))
    }

    @Test
    fun aClientBolusFromBeforeTheNameHasAnEmptyName() {
        val sources = TreatmentSources(listOf(entry(t, Action.BOLUS, Sources.NSClient, ValueWithUnit.Insulin(1.5))))

        assertEquals(TreatmentSource.Client(""), sources.ofBolus(bolus(t, 1.5)))
    }

    @Test
    fun theEntryWithTheSameAmountWinsOverANearerOne() {
        val sources = TreatmentSources(
            listOf(
                entry(t - minute, Action.BOLUS, Sources.Automation, ValueWithUnit.Insulin(0.5)),
                entry(t - 3 * minute, Action.BOLUS, Sources.InsulinDialog, ValueWithUnit.Insulin(2.0))
            )
        )

        assertEquals(TreatmentSource.User(Sources.InsulinDialog), sources.ofBolus(bolus(t, 2.0)))
    }

    @Test
    fun anEntryTooFarAwayDoesNotCount() {
        val sources = TreatmentSources(listOf(entry(t - 30 * minute, Action.BOLUS, Sources.WizardDialog, ValueWithUnit.Insulin(2.0))))

        assertNull(sources.ofBolus(bolus(t, 2.0)))
    }

    @Test
    fun aTempBasalOfTheLoopIsTheAlgorithm() {
        val sources = TreatmentSources(
            listOf(
                entry(t, Action.TEMP_BASAL, Sources.Loop, ValueWithUnit.Percent(120), ValueWithUnit.Minute(30)),
                entry(t + 40 * minute, Action.TEMP_BASAL, Sources.TempBasalDialog, ValueWithUnit.Percent(50), ValueWithUnit.Minute(60))
            )
        )

        assertEquals(TreatmentSource.Algorithm, sources.ofTempBasal(t + 10_000L))
        assertEquals(TreatmentSource.User(Sources.TempBasalDialog), sources.ofTempBasal(t + 40 * minute))
    }
}
