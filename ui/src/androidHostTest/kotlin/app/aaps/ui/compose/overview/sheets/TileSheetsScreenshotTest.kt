package app.aaps.ui.compose.overview.sheets

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.ui.compose.overview.chips.BolusRecommendation
import app.aaps.ui.compose.overview.chips.BolusRecommendationKind
import app.aaps.ui.compose.overview.statusLights.StatusItem
import app.aaps.ui.compose.overview.statusLights.StatusUiState
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.TileSheetSourceFixture
import app.aaps.ui.compose.testing.setAapsContent
import java.io.File
import kotlin.math.exp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * autoISF build. Design review only, not a regression test: draws the body of every tile sheet with made up data, in the
 * dark and the light theme, and writes PNGs to `ui/build/screenshots` (sheet_*). Run with
 * `./gradlew :ui:testAndroidHostTest --tests '*TileSheetsScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TileSheetsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture

    @Before
    fun setUp() {
        screen = AapsScreenFixture()
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        if (dark) whenever(screen.preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("dark"))
        compose.setAapsContent(screen) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.padding(top = 24.dp)) { content() }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "sheet_${name}_${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val bgInfo = BgInfoData(
        bgValue = 132.0, bgText = "132", bgRange = BgRange.IN_RANGE, isOutdated = false, timestamp = 0L,
        trendArrow = TrendArrow.FORTY_FIVE_UP, trendDescription = "Rising",
        delta = 4.0, deltaText = "+4", shortAvgDelta = 3.5, shortAvgDeltaText = "+3.5", longAvgDelta = 2.0, longAvgDeltaText = "+2.0"
    )

    private val status = StatusUiState(
        sensorStatus = StatusItem(label = "Dexcom G7", age = "2d4h", agePercent = 0.22f, icon = IcCgmInsert),
        insulinStatus = StatusItem(label = "Insulin", age = "1d6h", agePercent = 0.18f, level = "142 U", levelPercent = 0.53f, icon = IcPluginInsulin),
        cannulaStatus = StatusItem(label = "Cannula", age = "2d20h", ageStatus = StatusLevel.WARNING, agePercent = 0.95f, icon = IcCannulaChange),
        batteryStatus = StatusItem(label = "Pump battery", age = "12d", level = "78 %", icon = IcPumpBattery),
        showFill = true,
        showPumpBatteryChange = true
    )

    private fun bg(dark: Boolean) = shot("01_bg", dark) {
        BgSheetBody(
            bgInfo, "3 min ago", "Dexcom G7",
            BgSheetState(GlucoseUnit.MGDL, listOf(98.0, 96.0, 99.0, 104.0, 110.0, 118.0, 124.0, 128.0, 132.0, 132.0, 131.0, 132.0), 92, 128.0, 1.0, true),
            true, {}, {}, {}
        )
    }

    private fun target(dark: Boolean) = shot("02_target", dark) {
        TargetSheetBody(
            TargetSheetState(
                presets = listOf(
                    TargetPresetItem("e", "Eating soon", TT.Reason.EATING_SOON, "80 mg/dL · 45 min", false),
                    TargetPresetItem("a", "Activity", TT.Reason.ACTIVITY, "140 mg/dL · 60 min", true),
                    TargetPresetItem("h", "Hypo", TT.Reason.HYPOGLYCEMIA, "120 mg/dL · 60 min", false)
                ),
                runningText = "140 mg/dL · 45 min left",
                profileTargetText = "100 mg/dL", nextChangeTime = "22:00", nextChangeText = "110 mg/dL"
            ),
            true, {}, {}, {}, {}
        )
    }

    private fun bolus(dark: Boolean) = shot("03_bolus", dark) {
        BolusSheetBody(
            BolusRecommendation(kind = BolusRecommendationKind.SUGGEST, dose = 0.8, insulinNeeded = 0.8),
            BolusSheetState("3.0 U · 2 h ago", "BG 132 mg/dL, IOB 1.20 U, COB 22 g", 0.1, 10.0),
            true, { _, _ -> }, {}
        )
    }

    private fun carbs(dark: Boolean) = shot("04_carbs", dark) {
        CarbsSheetBody(CarbsSheetState("22 g", 22.0, 45, "45 g · 1 h 10 min ago", "120 mg/dL · 60 min", 120), true, { _, _, _ -> }, {})
    }

    private fun iob(dark: Boolean) = shot("05_iob", dark) {
        IobSheetBody(
            IobSheetState(
                "1.20 U", "1.45 U", "-0.25 U", "0.021 U/min",
                SheetTimeline(
                    values = (0..24).map { 0.3 + 3.0 * exp(-it / 30.0) * (1 - exp(-it / 4.0)) } + (1..38).map { 1.2 * exp(-it / 12.0) },
                    nowIndex = 25,
                    ticks = listOf(SheetTimeTick(4, "19:00"), SheetTimeTick(16, "20:00"), SheetTimeTick(28, "21:00"), SheetTimeTick(40, "22:00"), SheetTimeTick(52, "23:00")),
                    startText = "18:42", endText = "23:40"
                ),
                listOf("SMB 0.30 U" to "12 min ago · 0.27 U left", "TBR 110 %" to "12 min ago · 30 min", "SMB 0.20 U" to "27 min ago · 0.16 U left", "TBR 0 %" to "52 min ago · 30 min", "Bolus 3.00 U" to "2 h ago · 0.98 U left"),
                "23:40 · 3 h 10 min left"
            ),
            {}
        )
    }

    /** The basal sheet from what the app works out of stored data (see TileSheetSourceFixture), not from typed numbers */
    private fun basalReal(name: String, dark: Boolean, prepare: TileSheetSourceFixture.() -> Unit = {}) {
        val fixture = TileSheetSourceFixture(screen).apply(prepare)
        val state = runBlocking { fixture.source().basalSheet() }
        shot(name, dark) { BasalSheetBody(IcTbrHigh, state, true, { _, _ -> }, {}, {}) }
    }

    private fun basal(dark: Boolean) = basalReal("06_basal", dark)

    private fun basalNone(dark: Boolean) = basalReal("06b_basal_none", dark) { noTbrNow() }

    private fun basalOld(dark: Boolean) = basalReal("06c_basal_old_data", dark) { pumpAnswered(minutesAgo = 45) }

    private fun basalBig(dark: Boolean) = basalReal("06d_basal_font15", dark)

    private fun supplies(dark: Boolean) = shot("08_supplies", dark) {
        SuppliesSheetBody(status, {}, {}, {}, {}, {})
    }

    private fun profile(dark: Boolean) = shot("09_profile", dark) {
        ProfileSheetBody(ProfileSheetState(listOf("Profile A", "Weekend", "Sick day"), "Profile A", 100, "100 %"), true, { _, _, _ -> }, {})
    }

    private fun pump(dark: Boolean) = shot("10_pump", dark) {
        PumpSheetBody("Dana-i", null, status, PumpSheetState("connected 1 min ago", "0.94 U/h (temp basal)", "3.00 U · 2 h ago", true, true), true, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun bgDark() = bg(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun bgLight() = bg(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun targetDark() = target(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun targetLight() = target(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun bolusDark() = bolus(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun bolusLight() = bolus(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun carbsDark() = carbs(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun carbsLight() = carbs(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun iobDark() = iob(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun iobLight() = iob(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun basalDark() = basal(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun basalLight() = basal(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun basalNoneDark() = basalNone(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun basalNoneLight() = basalNone(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun basalOldDark() = basalOld(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun basalOldLight() = basalOld(false)
    @Test @Config(qualifiers = "w393dp-h1300dp-port-night-xxhdpi", fontScale = 1.5f) fun basalBigDark() = basalBig(true)
    @Test @Config(qualifiers = "w393dp-h1300dp-port-xxhdpi", fontScale = 1.5f) fun basalBigLight() = basalBig(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun suppliesDark() = supplies(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun suppliesLight() = supplies(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun profileDark() = profile(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun profileLight() = profile(false)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun pumpDark() = pump(true)
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun pumpLight() = pump(false)
}
