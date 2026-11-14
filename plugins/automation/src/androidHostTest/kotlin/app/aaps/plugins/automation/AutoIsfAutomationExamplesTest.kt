package app.aaps.plugins.automation

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.actions.ActionSetAcceWeight
import app.aaps.plugins.automation.actions.ActionSetIobTH
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutoIsfAutomationExamplesTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    private val rh = generatedTextResolver("automation" to AutomationStringsValues::textOf)
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var pumpEnactResultProvider: () -> PumpEnactResult

    private val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(
            aapsLogger, mock(), rh, profileFunction, mock(), preferences, mock(), mock(), mock(), mock(), mock(), dateUtil
        )
    }

    private val triggerFactory: TriggerFactory by lazy {
        TriggerFactory(triggerDeps, mock(), mock(), mock(), mock())
    }

    private val actionFactory: ActionFactory by lazy {
        ActionFactory(
            triggerDeps, aapsLogger, rh, pumpEnactResultProvider, mock(), dateUtil, mock(), mock(), mock(),
            profileFunction, mock(), mock(), mock(), mock(), mock(), preferences, mock(), mock(), mock(), mock(),
            mock(), mock(), mock(), mock(), mock()
        )
    }

    private val eventFactory: AutomationEventFactory by lazy {
        AutomationEventFactory(aapsLogger, dateUtil, actionFactory, triggerFactory, triggerDeps)
    }

    private val examples by lazy { AutoIsfAutomationExamples(eventFactory, actionFactory, triggerDeps, rh).build() }

    @BeforeEach fun prepare() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test fun theAutoIsfExamplesAreOffAndTheExportIsOn() {
        assertThat(examples).hasSize(6)
        val export = examples.single { it.actions.single() is ActionSettingsExport }
        assertThat(export.isEnabled).isTrue()
        assertThat(examples.filter { it !== export }.none { it.isEnabled }).isTrue()
    }

    @Test fun everyExampleHasItsOwnTitleAndOneAction() {
        assertThat(examples.map { it.title }.toSet()).hasSize(examples.size)
        assertThat(examples.all { it.title.isNotBlank() && it.actions.size == 1 && it.trigger.list.isNotEmpty() }).isTrue()
    }

    @Test fun theValuesAreTheDocumentedOnes() {
        val iobTh = examples.mapNotNull { it.actions.single() as? ActionSetIobTH }.map { it.newIobTH.value }
        assertThat(iobTh).containsExactly(AutoIsfAutomationExamples.NIGHT_IOB_TH_PERCENT, AutoIsfAutomationExamples.DAY_IOB_TH_PERCENT)
        val weights = examples.mapNotNull { it.actions.single() as? ActionSetAcceWeight }.map { it.newWeight.value }
        assertThat(weights).containsExactly(AutoIsfAutomationExamples.MEAL_ACCE_WEIGHT, AutoIsfAutomationExamples.NORMAL_ACCE_WEIGHT)
        val tempTarget = examples.mapNotNull { it.actions.single() as? ActionStartTempTarget }.single()
        assertThat(tempTarget.value.value).isEqualTo(AutoIsfAutomationExamples.SPORT_TEMP_TARGET_MGDL)
        assertThat(tempTarget.value.units).isEqualTo(GlucoseUnit.MGDL)
    }

    @Test fun everyExampleSurvivesTheStoredForm() {
        // Stored as JSON and read back at every start, and sent to the clients: it must not change on the way
        for (example in examples) {
            val first = example.toJSON()
            val second = eventFactory.fromJSON(first).toJSON()
            assertThat(second).isEqualTo(first)
        }
    }
}
