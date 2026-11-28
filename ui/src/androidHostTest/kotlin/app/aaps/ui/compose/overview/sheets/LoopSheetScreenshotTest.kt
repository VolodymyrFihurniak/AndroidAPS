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
import app.aaps.core.data.model.RM
import app.aaps.core.keys.StringKey
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.setAapsContent
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * autoISF build. Design review only, not a regression test: draws the body of the Loop sheet in every state it has (a
 * closed, open and LGS loop, a pause, a pump that is off, a loop that is off with no end, a client, no run yet, a run with
 * no change, no right to act), in the dark and the light theme, and at the font scales 1.5 and 2.0. The PNGs go to
 * `ui/build/screenshots` (loop_*). Run with `./gradlew :ui:testAndroidHostTest --tests '*LoopSheetScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoopSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture

    @Before
    fun setUp() {
        screen = AapsScreenFixture()
    }

    private fun shot(name: String, dark: Boolean, suffix: String = "", content: @Composable () -> Unit) {
        if (dark) whenever(screen.preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("dark"))
        compose.setAapsContent(screen) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.padding(top = 24.dp)) { content() }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "loop_${name}_${if (dark) "dark" else "light"}$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val loopModes = listOf(RM.Mode.CLOSED_LOOP, RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP_LGS)

    /** The last hour: the share of the profile asked for, and two SMBs */
    private val strip = LoopRunStrip(
        percent = listOf(100, 100, 80, 60, 60, 100, 120, 140, 130, 120, 110, 110),
        smb = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3, 0.0, 0.0, 0.0, 0.3),
        startText = "13:35", endText = "14:35", hours = listOf(SheetTimeTick(25, "14:00"))
    )

    private val lastRun = LoopLastRun("14:32", "110 % · 30 min", true, "0.30 U")

    private fun running(mode: RM.Mode) = LoopSheetState(
        mode = mode, lastRunText = "2 min ago", loopModes = loopModes, canSuspend = true, canDisconnect = true, canStop = true,
        lastRun = lastRun, runStrip = strip, canRunNow = true, apsClassName = "OpenAPSAutoISFPlugin"
    )

    private val paused = LoopSheetState(
        mode = RM.Mode.SUSPENDED_BY_USER, lastRunText = "43 min ago", canSuspend = true, canDisconnect = true, canResume = true,
        inEffect = LoopInEffect("42 min left · until 15:14", 0.7f), lastRun = lastRun.copy(timeText = "14:31"), runStrip = strip, apsClassName = "OpenAPSAutoISFPlugin"
    )

    private val pumpOff = LoopSheetState(
        mode = RM.Mode.DISCONNECTED_PUMP, lastRunText = "21 min ago", canDisconnect = true, canResume = true,
        inEffect = LoopInEffect("12 min left · until 14:46", 0.4f), lastRun = lastRun.copy(timeText = "14:14"), runStrip = strip, apsClassName = "OpenAPSAutoISFPlugin"
    )

    private val stopped = LoopSheetState(
        mode = RM.Mode.DISABLED_LOOP, lastRunText = "3 h ago", loopModes = loopModes, canDisconnect = true,
        inEffect = LoopInEffect("No end: it will not turn on by itself"), lastRun = lastRun.copy(timeText = "11:20"), runStrip = strip, apsClassName = "OpenAPSAutoISFPlugin"
    )

    private val client = running(RM.Mode.CLOSED_LOOP).copy(lastRunText = "", lastRun = null, runStrip = LoopRunStrip(), canRunNow = false, apsClassName = null)

    private val noRun = running(RM.Mode.CLOSED_LOOP).copy(lastRunText = "", lastRun = null, runStrip = LoopRunStrip())

    private val noChange = running(RM.Mode.CLOSED_LOOP).copy(lastRun = LoopLastRun("14:32"), runStrip = LoopRunStrip())

    private fun body(state: LoopSheetState, allowed: Boolean = true, autosens: String = "95 %") = @Composable {
        LoopSheetBody(state, autosens, allowed, { _, _ -> }, {}, {})
    }

    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun closedDark() = shot("closed", true, content = body(running(RM.Mode.CLOSED_LOOP)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun closedLight() = shot("closed", false, content = body(running(RM.Mode.CLOSED_LOOP)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun openDark() = shot("open", true, content = body(running(RM.Mode.OPEN_LOOP)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun openLight() = shot("open", false, content = body(running(RM.Mode.OPEN_LOOP)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun lgsDark() = shot("lgs", true, content = body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun lgsLight() = shot("lgs", false, content = body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun pausedDark() = shot("paused", true, content = body(paused))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun pausedLight() = shot("paused", false, content = body(paused))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun pumpDark() = shot("pump", true, content = body(pumpOff))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun pumpLight() = shot("pump", false, content = body(pumpOff))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun stoppedDark() = shot("stopped", true, content = body(stopped))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun stoppedLight() = shot("stopped", false, content = body(stopped))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun clientDark() = shot("client", true, content = body(client, allowed = false, autosens = ""))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun clientLight() = shot("client", false, content = body(client, allowed = false, autosens = ""))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun noRunDark() = shot("norun", true, content = body(noRun))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun noRunLight() = shot("norun", false, content = body(noRun))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun noChangeDark() = shot("nochange", true, content = body(noChange))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun noChangeLight() = shot("nochange", false, content = body(noChange))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-night-xxhdpi") fun noRightDark() = shot("noright", true, content = body(running(RM.Mode.CLOSED_LOOP), allowed = false))
    @Test @Config(qualifiers = "w393dp-h1000dp-port-xxhdpi") fun noRightLight() = shot("noright", false, content = body(running(RM.Mode.CLOSED_LOOP), allowed = false))

    @Test @Config(qualifiers = "w393dp-h1400dp-port-night-xxhdpi", fontScale = 1.5f) fun closedDark15() = shot("closed", true, "_fs15", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1400dp-port-xxhdpi", fontScale = 1.5f) fun closedLight15() = shot("closed", false, "_fs15", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1800dp-port-night-xxhdpi", fontScale = 2.0f) fun closedDark20() = shot("closed", true, "_fs20", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1800dp-port-xxhdpi", fontScale = 2.0f) fun closedLight20() = shot("closed", false, "_fs20", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "w393dp-h1400dp-port-night-xxhdpi", fontScale = 1.5f) fun stoppedDark15() = shot("stopped", true, "_fs15", body(stopped))
    @Test @Config(qualifiers = "w393dp-h1400dp-port-xxhdpi", fontScale = 1.5f) fun stoppedLight15() = shot("stopped", false, "_fs15", body(stopped))
    @Test @Config(qualifiers = "w393dp-h1800dp-port-night-xxhdpi", fontScale = 2.0f) fun stoppedDark20() = shot("stopped", true, "_fs20", body(stopped))
    @Test @Config(qualifiers = "w393dp-h1800dp-port-xxhdpi", fontScale = 2.0f) fun stoppedLight20() = shot("stopped", false, "_fs20", body(stopped))
    @Test @Config(qualifiers = "w393dp-h1400dp-port-night-xxhdpi", fontScale = 1.5f) fun pausedDark15() = shot("paused", true, "_fs15", body(paused))
    @Test @Config(qualifiers = "w393dp-h1800dp-port-night-xxhdpi", fontScale = 2.0f) fun pausedDark20() = shot("paused", true, "_fs20", body(paused))

    // Ukrainian: the long name of the LGS mode ("Зупинка базалу при низькому ГК") must wrap inside its tile
    @Test @Config(qualifiers = "uk-w393dp-h1000dp-port-night-xxhdpi") fun closedUkDark() = shot("closed", true, "_uk", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "uk-w393dp-h1400dp-port-xxhdpi", fontScale = 1.5f) fun closedUkLight15() = shot("closed", false, "_uk_fs15", body(running(RM.Mode.CLOSED_LOOP_LGS)))
    @Test @Config(qualifiers = "uk-w393dp-h1000dp-port-night-xxhdpi") fun pausedUkDark() = shot("paused", true, "_uk", body(paused))
    @Test @Config(qualifiers = "uk-w393dp-h1000dp-port-xxhdpi") fun stoppedUkLight() = shot("stopped", false, "_uk", body(stopped))
    @Test @Config(qualifiers = "uk-w393dp-h1000dp-port-night-xxhdpi") fun pumpUkDark() = shot("pump", true, "_uk", body(pumpOff))
}
