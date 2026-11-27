package app.aaps.ui.compose.overview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.AutoIsfGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgParabolaData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.DeviationDataPoint
import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.HeartRateGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.RunningModeGraphData
import app.aaps.core.interfaces.overview.graph.RunningModeSegment
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.StepsGraphData
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture
import app.aaps.ui.compose.testing.setAapsContent
import java.io.File
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design review only, not a regression test: renders the overview with every graph layer switched on and a busy
 * but realistic day, and writes PNGs to `ui/build/screenshots`. Run with
 * `./gradlew :ui:testAndroidHostTest --tests '*OverviewScreenshotTest*'`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverviewScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var vm: OverviewViewModelFixture

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        vm = OverviewViewModelFixture(screen)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val now = OverviewViewModelFixture.NOW
    private fun t(min: Int) = now + min * 60_000L

    /** BG of the made up day, mg/dl: a meal 3 h ago, a snack 25 min ago, a calm night before */
    private fun bgAt(min: Int): Double {
        val x = min.toDouble()
        fun g(center: Double, width: Double, h: Double) = h * exp(-((x - center) * (x - center)) / (2 * width * width))
        return 115 + g(-120.0, 35.0, 115.0) - g(-50.0, 25.0, 35.0) + g(-600.0, 60.0, 50.0) - g(-900.0, 50.0, 55.0) +
            g(5.0, 25.0, 20.0) + 4 * sin(x / 17.0)
    }

    private fun range(v: Double) = when {
        v > 180 -> BgRange.HIGH
        v < 72  -> BgRange.LOW
        else    -> BgRange.IN_RANGE
    }

    private fun fill(allSeries: Boolean) {
        val minutes = (-24 * 60..0 step 5).toList()
        val readings = minutes.map { BgDataPoint(t(it), bgAt(it), range(bgAt(it)), BgType.REGULAR) }
        val bucketed = minutes.map { BgDataPoint(t(it), bgAt(it), range(bgAt(it)), BgType.BUCKETED) }
        val bgNow = bgAt(0)
        fun pred(type: BgType, f: (Int) -> Double) = (5..180 step 5).map { BgDataPoint(t(it), f(it), range(f(it)), type) }
        val predictions = pred(BgType.IOB_PREDICTION) { bgNow - 0.25 * it } +
            pred(BgType.COB_PREDICTION) { bgNow + 40 * (1 - exp(-it / 40.0)) - 0.1 * it } +
            pred(BgType.UAM_PREDICTION) { bgNow + 20 * (1 - exp(-it / 30.0)) - 0.2 * it } +
            pred(BgType.ZT_PREDICTION) { bgNow - 0.35 * it }
        val c = vm.cache
        c.timeRangeFlow.value = TimeRange(t(-24 * 60), now, t(180))
        c.bgReadingsFlow.value = readings
        c.bucketedDataFlow.value = bucketed
        c.predictionsFlow.value = predictions

        fun series(from: Int = -24 * 60, to: Int = 0, step: Int = 5, f: (Int) -> Double) = (from..to step step).map { GraphDataPoint(t(it), f(it)) }
        fun iobAt(m: Int): Double = max(0.0, 0.4 + (if (m >= -180) 6.0 * exp(-(m + 180) / 90.0) else 0.0) + (if (m >= -25) 1.5 * exp(-(m + 25) / 90.0) else 0.0))
        fun cobAt(m: Int): Double = (if (m >= -180) max(0.0, 60 - (m + 180) * 0.5) else 0.0) + (if (m >= -25) max(0.0, 20 - (m + 25) * 0.25) else 0.0)
        c.iobGraphFlow.value = IobGraphData(series { iobAt(it) }, series(0, 180) { iobAt(0) * exp(-it / 90.0) })
        c.cobGraphFlow.value = CobGraphData(series { cobAt(it) }, emptyList())
        c.activityGraphFlow.value = ActivityGraphData(series { iobAt(it) * 0.012 }, series(0, 180) { iobAt(0) * 0.012 * exp(-it / 90.0) })
        c.bgiGraphFlow.value = BgiGraphData(series { -iobAt(it) * 0.6 }, series(0, 180) { -iobAt(0) * 0.6 * exp(-it / 90.0) })
        c.deviationsGraphFlow.value = DeviationsGraphData((-24 * 60..0 step 5).map {
            val d = (bgAt(it) - bgAt(it - 5)) + iobAt(it) * 0.6
            DeviationDataPoint(t(it), d, when {
                cobAt(it) > 0 -> DeviationType.CSF
                d > 2 -> DeviationType.POSITIVE
                d < -2 -> DeviationType.NEGATIVE
                else -> DeviationType.EQUAL
            })
        })
        c.ratioGraphFlow.value = RatioGraphData(series(step = 30) { 1.0 + 0.15 * sin(it / 200.0) })
        c.devSlopeGraphFlow.value = DevSlopeGraphData(series(step = 15) { 2 + sin(it / 60.0) * 2 }, series(step = 15) { -2 + sin(it / 50.0) })
        c.varSensGraphFlow.value = VarSensGraphData(series(step = 15) { 45 + 10 * sin(it / 120.0) })
        c.autoIsfGraphFlow.value = AutoIsfGraphData(
            acceIsf = series(step = 5) { 1.0 + 0.3 * sin(it / 40.0) },
            bgIsf = series(step = 5) { 1.0 + max(0.0, (bgAt(it) - 110) / 150) },
            ppIsf = series(step = 5) { if (it in -170..-60) 1.2 else 1.0 },
            duraIsf = series(step = 5) { if (it in -140..-80) 1.4 else 1.0 },
            finalIsf = series(step = 5) { 1.0 + max(0.0, (bgAt(it) - 110) / 120) },
            iobTh = series(step = 5) { 3.5 }
        )
        c.bgParabolaFlow.value = BgParabolaData(
            fitted = (-25..0 step 5).map { GraphDataPoint(t(it), bgAt(it) + 1) },
            extrapolation = (0..30 step 5).map { GraphDataPoint(t(it), bgNow + 1.1 * it - 0.02 * it * it) }
        )
        c.heartRateGraphFlow.value = HeartRateGraphData(listOf(series(step = 5) { 70 + 25 * max(0.0, sin(it / 90.0)) }))
        c.stepsGraphFlow.value = StepsGraphData(series(step = 5) { max(0.0, 300 * sin(it / 70.0)) })
        c.basalGraphFlow.value = BasalGraphData(
            profileBasal = (-24 * 60..0 step 60).map { GraphDataPoint(t(it), if ((it / 60) % 6 == 0) 0.9 else 0.7) },
            actualBasal = (-24 * 60..0 step 30).map { GraphDataPoint(t(it), listOf(0.0, 0.7, 1.4, 2.1, 0.35)[(it / 30 + 100) % 5]) },
            maxBasal = 3.0
        )
        c.targetLineFlow.value = TargetLineData(
            listOf(GraphDataPoint(t(-24 * 60), 100.0), GraphDataPoint(t(-420), 140.0), GraphDataPoint(t(-360), 100.0), GraphDataPoint(t(180), 100.0))
        )
        val smbs = (-170..0 step 15).map { BolusGraphPoint(t(it), 0.3, BolusType.SMB, true, "0.30 U") }
        c.treatmentGraphFlow.value = TreatmentGraphData(
            boluses = listOf(BolusGraphPoint(t(-180), 6.0, BolusType.NORMAL, true, "6.00 U"), BolusGraphPoint(t(-25), 1.5, BolusType.NORMAL, true, "1.50 U")) + smbs,
            carbs = listOf(CarbsGraphPoint(t(-180), 60.0, true, "60 g"), CarbsGraphPoint(t(-25), 20.0, true, "20 g")),
            extendedBoluses = emptyList(),
            therapyEvents = listOf(
                TherapyEventGraphPoint(t(-300), TherapyEventType.FINGER_STICK, "BG check", 0, 140.0),
                TherapyEventGraphPoint(t(-480), TherapyEventType.EXERCISE, "Exercise", 45 * 60_000L),
                TherapyEventGraphPoint(t(-700), TherapyEventType.ANNOUNCEMENT, "Site change", 0)
            ),
            calibrations = emptyList()
        )
        c.epsGraphFlow.value = listOf(EpsGraphPoint(t(-600), 110, 0, "Profile A", "110%"))
        c.runningModeGraphFlow.value = RunningModeGraphData(listOf(RunningModeSegment(RM.Mode.CLOSED_LOOP, t(-24 * 60), t(180))))
        c.bgInfoFlow.value = BgInfoData(
            bgValue = bgNow, bgText = bgNow.toInt().toString(), bgRange = range(bgNow), isOutdated = false,
            timestamp = now - 60_000, trendArrow = TrendArrow.FORTY_FIVE_UP, trendDescription = "Rising",
            delta = 4.0, deltaText = "+4", shortAvgDelta = 3.5, shortAvgDeltaText = "+3.5", longAvgDelta = 2.0, longAvgDeltaText = "+2.0"
        )

        // Personal prediction, both curves with the error band
        val learnedPts = (5..120 step 5).map { LearnedPoint(t(it), bgNow + 25 * (1 - exp(-it / 35.0)) - 0.15 * it) }
        val expectedPts = (5..120 step 5).map { LearnedPoint(t(it), bgNow + 15 * (1 - exp(-it / 30.0)) - 0.25 * it) }
        whenever(screen.profileUtil.fromMgdlToUnits(any(), any())).thenAnswer { it.arguments[0] as Double }
        vm.learnedPrediction.enabled.value = true
        vm.learnedPrediction.forecast.value = LearnedForecast(
            timestamp = now, bgMgdl = bgNow, points = learnedPts, typicalErrorMgdl = (1..24).map { it * 1.5 },
            eventualMgdl = 110.0, isfMgdl = 45.0, ic = 10.0, targetMgdl = 100.0, ready = true, modelTrainedAt = now - 86_400_000,
            expectedPoints = expectedPts, expectedTypicalErrorMgdl = (1..24).map { it * 1.2 }, expectedReady = true
        )

        val config = if (allSeries) GraphConfig(
            bgOverlays = GraphConfig.DEFAULT_BG_OVERLAYS,
            iobOverlays = listOf(SeriesType.ACTIVITY, SeriesType.BASAL),
            secondaryGraphs = listOf(
                SecondaryGraph(listOf(SeriesType.AUTOISF, SeriesType.IOB_TH)),
                SecondaryGraph(listOf(SeriesType.DEVIATIONS, SeriesType.BGI)),
                SecondaryGraph(listOf(SeriesType.SENSITIVITY, SeriesType.VAR_SENSITIVITY)),
                SecondaryGraph(listOf(SeriesType.HEART_RATE, SeriesType.STEPS))
            )
        ) else GraphConfig()
        whenever(vm.graphConfigRepository.graphConfigFlow).thenReturn(MutableStateFlow(config))

        // autoISF build: the activity tile. The exercise mode is on; with all series an activity target runs too.
        whenever(screen.config.APS).thenReturn(true)
        val aps = mock<APS>()
        whenever(aps.algorithm).thenReturn(APSResult.Algorithm.AUTO_ISF)
        whenever(vm.activePlugin.activeAPS).thenReturn(aps)
        whenever(screen.preferences.observe(BooleanKey.ApsAutoIsfHighTtRaisesSens)).thenReturn(MutableStateFlow(true))
        whenever(screen.preferences.get(StringNonKey.TempTargetPresets))
            .thenReturn("""[{"id":"a","reason":"Activity","targetValue":140,"duration":3600000,"isDeletable":false}]""")
        whenever(screen.preferences.get(UnitDoubleKey.ApsAutoIsfHalfBasalExerciseTarget)).thenReturn(160.0)
        whenever(screen.dateUtil.untilString(any(), any(), any())).thenReturn("45 min")
        whenever(screen.profileUtil.convertToMgdlDetect(any())).thenAnswer { it.arguments[0] as Double }
        whenever(screen.profileUtil.fromMgdlToStringWithUnits(any())).thenAnswer { "${(it.arguments[0] as Double).toInt()} mg/dL" }

        runBlocking {
            whenever(vm.persistenceLayer.getTemporaryTargetActiveAt(any())).thenReturn(
                if (allSeries) TT(timestamp = now - 15 * 60_000L, duration = 60 * 60_000L, reason = TT.Reason.ACTIVITY, lowTarget = 140.0, highTarget = 140.0) else null
            )
            whenever(vm.persistenceLayer.getStepsCountFromTime(any())).thenReturn(
                listOf(SC(duration = 300_000L, timestamp = now - 60_000L, steps5min = 420, steps10min = 800, steps15min = 1200, steps30min = 2300, steps60min = 4180, steps180min = 6000, device = "watch"))
            )
            whenever(vm.persistenceLayer.getHeartRatesFromTime(any())).thenReturn(
                listOf(98.0, 112.0, 124.0).mapIndexed { i, bpm -> HR(duration = 300_000L, timestamp = now - (3 - i) * 300_000L, beatsPerMinute = bpm, device = "watch") }
            )
            whenever(vm.iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(now, iob = 2.1, activity = 0.02))
            whenever(vm.iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(now, basaliob = 0.4))
            whenever(vm.iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(now, 22.0, 0.0))
        }
    }

    private fun dark() {
        whenever(screen.preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("dark"))
    }

    private val statusLightsDef = PreferenceSubScreenDef(key = "statuslights", titleResId = app.aaps.core.ui.R.string.statuslights)

    private fun render(name: String, split: Boolean, empty: Boolean = false) {
        val profileName = if (empty) "" else "Profile A"
        val runningMode = if (empty) RM.Mode.DISABLED_LOOP else RM.Mode.CLOSED_LOOP
        val graph = vm.graphViewModel
        val chips = vm.chipsViewModel
        val manage = vm.manageViewModel
        val status = vm.awaitStatusItems()
        compose.setAapsContent(screen) {
            if (split) OverviewScreenSplit(
                profileName = profileName, isProfileModified = !empty, profileProgress = if (empty) 0f else 0.4f,
                tempTargetText = if (empty) "" else "100", tempTargetState = TempTargetChipState.None, tempTargetProgress = 0f, tempTargetReason = null,
                runningMode = runningMode, runningModeText = if (empty) "Loop disabled" else "Closed Loop",
                isSimpleMode = false,
                graphViewModel = graph, chipsViewModel = chips, manageViewModel = manage, statusViewModel = status,
                statusLightsDef = statusLightsDef, onNavigate = {}, onIobChipClick = {}, paddingValues = PaddingValues(0.dp)
            ) else OverviewScreenStacked(
                profileName = profileName, isProfileModified = !empty, profileProgress = if (empty) 0f else 0.4f,
                tempTargetText = if (empty) "" else "100", tempTargetState = TempTargetChipState.None, tempTargetProgress = 0f, tempTargetReason = null,
                runningMode = runningMode, runningModeText = if (empty) "Loop disabled" else "Closed Loop",
                isSimpleMode = false,
                graphViewModel = graph, chipsViewModel = chips, manageViewModel = manage, statusViewModel = status,
                statusLightsDef = statusLightsDef, onNavigate = {}, onIobChipClick = {}, paddingValues = PaddingValues(0.dp)
            )
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w393dp-h852dp-port-xxhdpi")
    fun portraitDefaultLight() { fill(false); render("01_portrait_default_light", false) }

    @Test @Config(qualifiers = "w393dp-h852dp-port-night-xxhdpi")
    fun portraitDefaultDark() { fill(false); dark(); render("02_portrait_default_dark", false) }

    @Test @Config(qualifiers = "w393dp-h2000dp-port-xxhdpi")
    fun portraitAllLightTall() { fill(true); render("03_portrait_all_light_tall", false) }

    @Test @Config(qualifiers = "w393dp-h2000dp-port-night-xxhdpi")
    fun portraitAllDarkTall() { fill(true); dark(); render("04_portrait_all_dark_tall", false) }

    @Test @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun landscapeDefaultLight() { fill(false); render("05_landscape_default_light", true) }

    @Test @Config(qualifiers = "w852dp-h393dp-land-night-xxhdpi")
    fun landscapeAllDark() { fill(true); dark(); render("06_landscape_all_dark", true) }

    // Samsung phones: a narrow window (display size "large") together with a big font
    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi", fontScale = 1.3f)
    fun samsungNarrowFont13() { fill(false); render("07_w360_font13", false) }

    @Test @Config(qualifiers = "w320dp-h700dp-port-xxhdpi", fontScale = 1.5f)
    fun samsungZoomFont15() { fill(true); render("08_w320_font15", false) }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi", fontScale = 2.0f)
    fun samsungNarrowFont20() { fill(false); render("09_w360_font20", false) }

    // autoISF build: a new install as on the owner's phone: no BG, no profile, loop disabled
    @Test @Config(qualifiers = "w360dp-h800dp-port-night-xxhdpi", fontScale = 1.15f)
    fun newInstallDark() { dark(); render("11_new_install_dark", false, empty = true) }

    /** autoISF build: the activity sheet, with the state the ViewModel works out from the running activity target */
    private fun renderActivitySheet(name: String, running: Boolean) {
        val chips = vm.chipsViewModel
        compose.setAapsContent(screen) {
            val state by chips.exerciseModeUiState.collectAsState()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    ActivitySheetContent(state = state, commandsAllowed = true, onToggleMode = {}, onStart = {}, onCancelTarget = {}, onOpenTargets = {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        check(running == chips.exerciseModeUiState.value.targetRunning)
    }

    @Test @Config(qualifiers = "w393dp-h520dp-port-night-xxhdpi")
    fun activitySheetRunningDark() { fill(true); dark(); renderActivitySheet("12_activity_sheet_running_dark", running = true) }

    @Test @Config(qualifiers = "w393dp-h520dp-port-xxhdpi")
    fun activitySheetReadyLight() { fill(false); renderActivitySheet("13_activity_sheet_ready_light", running = false) }

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.5f)
    fun samsungLandscapeFont15() { fill(false); render("10_land_w780_font15", true) }
}
