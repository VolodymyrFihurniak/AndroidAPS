package app.aaps.ui.compose.overview

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture
import app.aaps.ui.compose.testing.setAapsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The window the two wide layouts are actually used in. `OverviewScreenSplit` is phone landscape and
 * `OverviewScreenTablet` is a tablet; on the default (portrait phone) Robolectric window their side
 * by side columns collapse and widgets report as "not displayed", which says nothing about the
 * layout.
 */
private const val WIDE = "w1280dp-h800dp"

/**
 * Robolectric composable tests for the three overview layouts.
 *
 * All three take the same view model set, so they are fed one [OverviewViewModelFixture] and exactly
 * the same data - which is what lets a difference these tests find be attributed to the layout
 * rather than to the fixture. What is pinned is what a user would notice if the three drifted apart:
 *  - autoISF build: all three show the same cards (`OverviewMainCards`): the BG card with its "time ago"
 *    line, and the status card that starts **collapsed**, with the Pump button that opens the pump
 *    screen. Collapsed shows an "expand" affordance and no heading. Before, a turned phone showed the
 *    old screen.
 *  - only the tablet layout carries a [LargeClock], with the time only (the BG card has the age).
 *  - with no BG reading, the BG card shows the "---" placeholder rather than nothing.
 *
 * Every label is read from the real resources, so a translation edit cannot make the test lie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverviewScreenVariantsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: OverviewViewModelFixture
    private lateinit var context: Context

    private lateinit var statusHeading: String
    private lateinit var expandLabel: String
    private lateinit var collapseLabel: String
    private lateinit var pumpLabel: String

    private val clockText = OverviewViewModelFixture.CLOCK_TIME + OverviewViewModelFixture.CLOCK_AGO

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        viewModels = OverviewViewModelFixture(screen)
        context = RuntimeEnvironment.getApplication()
        statusHeading = context.getString(app.aaps.core.ui.R.string.status)
        expandLabel = context.getString(app.aaps.core.ui.R.string.expand)
        collapseLabel = context.getString(app.aaps.core.ui.R.string.collapse)
        pumpLabel = context.getString(app.aaps.core.ui.R.string.pump_management)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val statusLightsDef = PreferenceSubScreenDef(
        key = "statuslights",
        titleResId = app.aaps.core.ui.R.string.statuslights
    )

    private val navigated = mutableListOf<NavigationRequest>()

    private enum class Variant { STACKED, SPLIT, TABLET }

    private fun setScreen(variant: Variant) {
        val graph = viewModels.graphViewModel
        val chips = viewModels.chipsViewModel
        val manage = viewModels.manageViewModel
        val status = viewModels.awaitStatusItems()
        compose.setAapsContent(screen) {
            when (variant) {
                Variant.STACKED -> OverviewScreenStacked(
                    profileName = PROFILE_NAME, isProfileModified = false, profileProgress = 0f,
                    tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                    tempTargetProgress = 0f, tempTargetReason = null,
                    runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                    runningModeRemaining = "", runningModeProgress = 0f,
                    smbEnabled = false, isSimpleMode = true,
                    graphViewModel = graph, chipsViewModel = chips, manageViewModel = manage,
                    statusViewModel = status, statusLightsDef = statusLightsDef,
                    onNavigate = { navigated += it }, onIobChipClick = {},
                    paddingValues = PaddingValues(0.dp)
                )

                Variant.SPLIT   -> OverviewScreenSplit(
                    profileName = PROFILE_NAME, isProfileModified = false, profileProgress = 0f,
                    tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                    tempTargetProgress = 0f, tempTargetReason = null,
                    runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                    runningModeRemaining = "", runningModeProgress = 0f,
                    smbEnabled = false, isSimpleMode = true,
                    graphViewModel = graph, chipsViewModel = chips, manageViewModel = manage,
                    statusViewModel = status, statusLightsDef = statusLightsDef,
                    onNavigate = { navigated += it }, onIobChipClick = {},
                    paddingValues = PaddingValues(0.dp)
                )

                Variant.TABLET  -> OverviewScreenTablet(
                    profileName = PROFILE_NAME, isProfileModified = false, profileProgress = 0f,
                    tempTargetText = TT_TEXT, tempTargetState = TempTargetChipState.None,
                    tempTargetProgress = 0f, tempTargetReason = null,
                    runningMode = RM.Mode.OPEN_LOOP, runningModeText = RM_TEXT,
                    runningModeRemaining = "", runningModeProgress = 0f,
                    smbEnabled = false, isSimpleMode = true,
                    graphViewModel = graph, chipsViewModel = chips, manageViewModel = manage,
                    statusViewModel = status, statusLightsDef = statusLightsDef,
                    onNavigate = { navigated += it }, onIobChipClick = {},
                    paddingValues = PaddingValues(0.dp)
                )
            }
        }
    }

    // ---------------------------------------------------------------- BG circle

    @Test
    fun `stacked layout shows the placeholder when there is no BG reading`() {
        setScreen(Variant.STACKED)

        compose.onNodeWithText(NO_BG_PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithText(OverviewViewModelFixture.BG_TEXT).assertDoesNotExist()
    }

    @Test
    fun `stacked layout shows BG value, delta and time ago once a reading arrives`() {
        viewModels.withBg()
        setScreen(Variant.STACKED)

        compose.onNodeWithText(OverviewViewModelFixture.BG_TEXT).assertIsDisplayed()
        compose.onNodeWithText(OverviewViewModelFixture.DELTA_TEXT).assertIsDisplayed()
        compose.onNodeWithText(OverviewViewModelFixture.TIME_AGO).assertIsDisplayed()
        compose.onNodeWithText(NO_BG_PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `split layout keeps the BG time ago line`() {
        viewModels.withBg()
        setScreen(Variant.SPLIT)

        compose.onNodeWithText(OverviewViewModelFixture.TIME_AGO).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `tablet layout keeps the BG time ago line`() {
        viewModels.withBg()
        setScreen(Variant.TABLET)

        compose.onNodeWithText(OverviewViewModelFixture.BG_TEXT).assertIsDisplayed()
        compose.onNodeWithText(OverviewViewModelFixture.TIME_AGO).assertIsDisplayed()
    }

    // ---------------------------------------------------------------- clock

    @Test
    fun `stacked layout has no large clock`() {
        viewModels.withBg()
        setScreen(Variant.STACKED)

        compose.onNodeWithText(clockText).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `split layout has no large clock`() {
        viewModels.withBg()
        setScreen(Variant.SPLIT)

        compose.onNodeWithText(OverviewViewModelFixture.CLOCK_TIME).assertDoesNotExist()
        compose.onNodeWithText(clockText).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `tablet layout shows the clock without the age of the BG reading`() {
        viewModels.withBg()
        setScreen(Variant.TABLET)

        // The BG card under it already shows the age, see `tablet layout keeps the BG time ago line`.
        compose.onNodeWithText(OverviewViewModelFixture.CLOCK_TIME).assertIsDisplayed()
        compose.onNodeWithText(clockText).assertDoesNotExist()
    }

    // ---------------------------------------------------------------- status card

    @Test
    fun `stacked layout starts with the status card collapsed`() = assertStatusCollapsed(Variant.STACKED)

    @Test
    @Config(qualifiers = WIDE)
    fun `split layout starts with the status card collapsed`() = assertStatusCollapsed(Variant.SPLIT)

    @Test
    @Config(qualifiers = WIDE)
    fun `tablet layout starts with the status card collapsed`() = assertStatusCollapsed(Variant.TABLET)

    private fun assertStatusCollapsed(variant: Variant) {
        setScreen(variant)

        compose.onNodeWithContentDescription(expandLabel).assertIsDisplayed()
        compose.onNodeWithText(statusHeading).assertDoesNotExist()
        compose.onNodeWithContentDescription(collapseLabel).assertDoesNotExist()
    }

    // ---------------------------------------------------------------- pump button

    @Test
    fun `stacked layout pump button opens the pump screen`() = assertPumpButton(Variant.STACKED)

    @Test
    @Config(qualifiers = WIDE)
    fun `split layout pump button opens the pump screen`() = assertPumpButton(Variant.SPLIT)

    @Test
    @Config(qualifiers = WIDE)
    fun `tablet layout pump button opens the pump screen`() = assertPumpButton(Variant.TABLET)

    private fun assertPumpButton(variant: Variant) {
        setScreen(variant)

        compose.onNodeWithText(pumpLabel).assertIsDisplayed().performClick()
        assertEquals(listOf<NavigationRequest>(NavigationRequest.Element(ElementType.PUMP)), navigated)
    }

    companion object {

        private const val PROFILE_NAME = "Profile A"
        private const val TT_TEXT = "100"
        private const val RM_TEXT = "Open Loop"
        private const val NO_BG_PLACEHOLDER = "---"
    }
}
