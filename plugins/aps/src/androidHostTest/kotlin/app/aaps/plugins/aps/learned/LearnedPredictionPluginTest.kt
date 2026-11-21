package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.keys.LearnedIntKey
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import kotlin.reflect.KClass

class LearnedPredictionPluginTest {

    private val now = 1_767_225_600_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val oldModel = LearnedModel(
        trainedAt = now, windowDays = 180, samples = 5000,
        linear = LinearModel(LinearFeatures.PRIOR.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 1f },
        model = Metrics(1.0, 2.0, 3.0), autoIsf = null, naive = Metrics(1.0, 2.0, 3.0), autoIsfCoverage = 0.0,
        typicalErrorMgdl = List(24) { 1.0 }, ready = true, clampHits = 0
    )

    private val preferences: Preferences = mock {
        on { get(LearnedStringKey.ModelJson) } doReturn oldModel.toJson()
        on { get(LearnedIntKey.TrainingWindowDays) } doReturn 180
    }
    private val bgChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // What the live read does. A test sets it before the start: stubbing the call again would run the old answer,
    // which waits for ever.
    private var liveRead: suspend () -> HistoryInput = { awaitCancellation() }
    private val historyReader: HistoryReader = mock {
        on { read(any(), any()) } doSuspendableAnswer { awaitCancellation() }
        on { readLive(any()) } doSuspendableAnswer { liveRead() }
        on { bgChanges() } doReturn bgChanges
    }
    private val rxBus: RxBus = mock {
        on { toFlow(any<KClass<EventBucketedDataCreated>>()) } doReturn emptyFlow()
    }
    private val dateUtil: DateUtil = mock { on { now() } doReturn now }

    private val sut = LearnedPredictionPlugin(
        aapsLogger = mock<AAPSLogger>(), rh = mock<TextResolver>(), preferences = preferences,
        notificationManager = mock<NotificationManager>(), historyReader = historyReader, runArchive = mock<AutoIsfRunArchive>(), rxBus = rxBus,
        dateUtil = dateUtil, profileUtil = mock<ProfileUtil>(), appScope = scope
    )

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `stop keeps the old model`() = runBlocking {
        sut.onStart()
        assertThat(sut.status.value.model).isEqualTo(oldModel)

        sut.trainNow()
        verifyBlocking(historyReader, timeout(2000)) { read(any(), any()) }
        assertThat(sut.status.value.state).isEqualTo(LearnedState.TRAINING)
        sut.stopTraining()

        val status = withTimeout(5000) { sut.status.first { it.state != LearnedState.TRAINING } }
        assertThat(status.model).isEqualTo(oldModel)
        verify(preferences, never()).put(eq(LearnedStringKey.ModelJson), any<String>())
    }

    @Test
    fun `a new BG reading makes a new forecast`() = runBlocking<Unit> {
        // The live read fails quickly here: the forecast is null, the plugin goes on listening
        liveRead = { throw IllegalStateException("no data in this test") }
        sut.onStart()
        verifyBlocking(historyReader, timeout(2000).times(1)) { readLive(any()) }
        // The plugin listens once the first refresh is done; until then an emitted change is lost
        withTimeout(2000) { bgChanges.subscriptionCount.first { it > 0 } }
        bgChanges.emit(Unit)
        verifyBlocking(historyReader, timeout(10_000).times(2)) { readLive(any()) }
    }
}
