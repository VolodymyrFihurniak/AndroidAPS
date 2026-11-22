package app.aaps.plugins.aps.learned.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.LearnedParts
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.plugins.aps.learned.LearnedState
import app.aaps.plugins.aps.learned.LearnedStatus
import app.aaps.plugins.aps.learned.LiveScore
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The plugin screen with a model has more cards than a phone screen holds. The whole screen has to scroll, so that
 * the buttons and the log below the cards can be reached.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LearnedPredictionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Text calls answer "", so the test does not depend on how numbers are formatted
    private val profileUtil: ProfileUtil = mock(defaultAnswer = RETURNS_SMART_NULLS) {
        on { units } doReturn GlucoseUnit.MMOL
    }
    private val dateUtil: DateUtil = mock(defaultAnswer = RETURNS_SMART_NULLS)

    private val model = LearnedModel(
        trainedAt = 0L, windowDays = 180, samples = 5000,
        linear = LinearModel(LinearFeatures.PRIOR.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 1f },
        model = Metrics(10.0, 20.0, 30.0), autoIsf = null, naive = Metrics(11.0, 22.0, 33.0), autoIsfCoverage = 0.0,
        typicalErrorMgdl = List(24) { 5.0 }, ready = false, clampHits = 0, phases = 5
    )
    private val live = LiveScore(
        moments = 0, gru = null, linear = null, naive = null,
        pairedMoments = 0, pairedGru = null, pairedLinear = null, pairedAutoIsf = null, pairedNaive = null
    )

    @Test
    fun `the buttons and the last log line can be reached by scrolling`() {
        val log = (1..40).map { SyncLogEntry("Log line $it") }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalProfileUtil provides profileUtil, LocalDateUtil provides dateUtil) {
                    LearnedPredictionContent(
                        status = LearnedStatus(LearnedState.NOT_READY, model = model),
                        log = log,
                        autoIsfCopyDays = 0,
                        liveScore = live,
                        parts60 = LearnedParts(insulin = 20.0, carbs = 0.0, meal = 0.0, drift = 0.0, trend = -2.0, network = 20.0),
                        onTrain = {}, onStop = {}, onReset = {}, onCopyReport = {}
                    )
                }
            }
        }
        // Without a string table here, a text of this module shows its key name
        val copyReport = "learned_copy_report"
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText(copyReport))
        compose.onNodeWithText(copyReport).assertIsDisplayed()
        list.performScrollToNode(hasText("Log line 40", substring = true))
        compose.onNodeWithText("Log line 40", substring = true).assertIsDisplayed()
    }
}
