package app.aaps.ui.compose.overview.chips

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * autoISF build: gathers what [bolusRecommendationOf] needs, for the Bolus card and the Insulin dialog, so both show
 * the same advice.
 *
 * On the master the last run of its own loop is read. On a client it is the run the master sent in its device
 * status. A client does not know whether the master has SMB on, so in closed loop it assumes the loop gives the
 * insulin: it then offers no dose, which is the careful side.
 */
@SingleIn(AppScope::class)
@Inject
class BolusRecommendationSource(
    private val loop: Loop,
    private val config: Config,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val iobCobCalculator: IobCobCalculator,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val dateUtil: DateUtil
) {

    suspend fun current(): BolusRecommendation = runCatching { bolusRecommendationOf(input()) }.getOrElse { BolusRecommendation() }

    private suspend fun input(): BolusRecommendationInput {
        val now = dateUtil.now()
        val (result, runAt) =
            if (config.AAPSCLIENT) processedDeviceStatusData.openAPSData.suggested to processedDeviceStatusData.openApsTimestamp
            else loop.lastRun?.let { it.request?.algorithmResult to it.lastAPSRun } ?: (null to 0L)
        val closedLoop = loop.runningMode() == RM.Mode.CLOSED_LOOP
        return BolusRecommendationInput(
            bgMgdl = iobCobCalculator.ads.actualBg()?.value,
            lowMgdl = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), profileUtil.units),
            result = result,
            resultAgeMs = if (runAt > 0L) now - runAt else Long.MAX_VALUE,
            loopGivesSmb = closedLoop && (config.AAPSCLIENT || constraintChecker.isSMBModeEnabled().value()),
            maxBolus = constraintChecker.getMaxBolusAllowed().value(),
            bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        )
    }
}
