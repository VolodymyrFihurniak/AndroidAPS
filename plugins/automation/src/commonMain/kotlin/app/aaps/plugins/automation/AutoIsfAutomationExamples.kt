package app.aaps.plugins.automation

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDelta
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputWeight
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerBgAcceWeight
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDelta
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerRecurringTime
import app.aaps.plugins.automation.triggers.TriggerStepsCount

/**
 * autoISF build: ready made automations, added once to the list of automations.
 *
 * - AutoISF examples for the night, sport and meals. They are added **switched off**: their values
 *   are only a starting point, and the user checks them before switching an example on.
 * - A daily settings export. It is switched on: it only writes a file, and it tells the user when the
 *   export password must be entered again.
 */
class AutoIsfAutomationExamples(
    private val eventFactory: AutomationEventFactory,
    private val actionFactory: ActionFactory,
    private val triggerDeps: TriggerDeps,
    private val rh: TextResolver
) {

    fun build(): List<AutomationEventObject> = listOf(
        nightIobTh(), dayIobTh(), sport(), meal(), afterMeal(), dailySettingsExport()
    )

    /** At 22:00 a lower IOB threshold, so fewer SMBs pile up in the night */
    private fun nightIobTh() = event(
        title = rh.gs(AutomationStrings.aisf_example_night_iobth),
        enabled = false,
        triggers = listOf(daily(NIGHT_START_MINUTES)),
        action = actionFactory.actionSetIobTH().apply { newIobTH.value = NIGHT_IOB_TH_PERCENT }
    )

    /** At 06:00 the full IOB threshold again */
    private fun dayIobTh() = event(
        title = rh.gs(AutomationStrings.aisf_example_day_iobth),
        enabled = false,
        triggers = listOf(daily(DAY_START_MINUTES)),
        action = actionFactory.actionSetIobTH().apply { newIobTH.value = DAY_IOB_TH_PERCENT }
    )

    /**
     * Many steps in 30 minutes: a high temp target. With exercise mode on, a high temp target raises
     * the sensitivity, see the exercise mode button in the overview.
     */
    private fun sport() = event(
        title = rh.gs(AutomationStrings.aisf_example_sport),
        enabled = false,
        triggers = listOf(
            TriggerStepsCount(triggerDeps).apply {
                measurementDuration.setValue("30")
                stepsCount.value = SPORT_STEPS_IN_30_MIN
                comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
            }
        ),
        action = actionFactory.actionStartTempTarget().apply {
            value.units = GlucoseUnit.MGDL
            value.value = SPORT_TEMP_TARGET_MGDL
            duration = InputDuration(SPORT_DURATION_MINUTES, InputDuration.TimeUnit.MINUTES)
        }
    )

    /** BG high and rising: a higher bgAccel weight, so AutoISF reacts more to the rise */
    private fun meal() = event(
        title = rh.gs(AutomationStrings.aisf_example_meal),
        enabled = false,
        triggers = listOf(
            TriggerBg(triggerDeps, MEAL_BG_MGDL, GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_GREATER),
            delta(MEAL_DELTA_MGDL, Comparator.Compare.IS_EQUAL_OR_GREATER),
            acceWeight(MEAL_ACCE_WEIGHT, Comparator.Compare.IS_LESSER)
        ),
        action = actionFactory.actionSetAcceWeight().apply { newWeight.value = MEAL_ACCE_WEIGHT }
    )

    /** BG no longer rising: the bgAccel weight back to the normal value */
    private fun afterMeal() = event(
        title = rh.gs(AutomationStrings.aisf_example_after_meal),
        enabled = false,
        triggers = listOf(
            delta(0.0, Comparator.Compare.IS_EQUAL_OR_LESSER),
            acceWeight(NORMAL_ACCE_WEIGHT, Comparator.Compare.IS_GREATER)
        ),
        action = actionFactory.actionSetAcceWeight().apply { newWeight.value = NORMAL_ACCE_WEIGHT }
    )

    /** Every night at 03:00 the settings are exported, see "Unattended settings export" in the maintenance settings */
    private fun dailySettingsExport() = event(
        title = rh.gs(AutomationStrings.aisf_daily_settings_export),
        enabled = true,
        triggers = listOf(daily(EXPORT_MINUTES)),
        action = actionFactory.actionSettingsExport().apply { text.value = rh.gs(AutomationStrings.aisf_daily_settings_export) }
    )

    private fun event(title: String, enabled: Boolean, triggers: List<Trigger>, action: Action): AutomationEventObject =
        eventFactory.newEvent().apply {
            this.title = title
            isEnabled = enabled
            trigger = TriggerConnector(triggerDeps, TriggerConnector.Type.AND).apply { list.addAll(triggers) }
            addAction(action)
        }

    private fun daily(minutesSinceMidnight: Int) =
        TriggerRecurringTime(triggerDeps).time(minutesSinceMidnight).apply { days.setAll(true) }

    private fun delta(mgdl: Double, compare: Comparator.Compare) =
        TriggerDelta(
            triggerDeps,
            InputDelta(rh, mgdl, -360.0, 360.0, 1.0, NumberFormat.INTEGER, InputDelta.DeltaType.DELTA),
            GlucoseUnit.MGDL,
            compare
        )

    private fun acceWeight(weight: Double, compare: Comparator.Compare) =
        TriggerBgAcceWeight(triggerDeps).apply {
            acceWeight = InputWeight(weight)
            comparator.value = compare
        }

    companion object {

        const val NIGHT_START_MINUTES = 22 * 60
        const val DAY_START_MINUTES = 6 * 60
        const val EXPORT_MINUTES = 3 * 60
        const val NIGHT_IOB_TH_PERCENT = 80
        const val DAY_IOB_TH_PERCENT = 100
        const val SPORT_STEPS_IN_30_MIN = 1500.0
        const val SPORT_TEMP_TARGET_MGDL = 140.0
        const val SPORT_DURATION_MINUTES = 60
        const val MEAL_BG_MGDL = 120.0
        const val MEAL_DELTA_MGDL = 6.0
        const val MEAL_ACCE_WEIGHT = 0.2
        const val NORMAL_ACCE_WEIGHT = 0.1
    }
}
