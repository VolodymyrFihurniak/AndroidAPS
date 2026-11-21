package app.aaps.plugins.aps.learned.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.ActionButtons
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.log.SyncLogList
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.learned.LearnedPredictionPlugin
import app.aaps.plugins.aps.learned.LearnedState
import app.aaps.plugins.aps.learned.LearnedStatus
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.train.Trainer
import kotlin.math.roundToInt

/** autoISF build: the screen of the personal prediction: its state, its errors, what it learned and its log */
internal class LearnedPredictionScreen(private val plugin: LearnedPredictionPlugin) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val title = stringResource(ApsStrings.learned_prediction)
        val back = stringResource(CoreUiStrings.back)
        val settings = stringResource(CoreUiStrings.nav_plugin_preferences)
        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = back) }
                    },
                    actions = {
                        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = settings) }
                    }
                )
            )
        }
        val status by plugin.status.collectAsStateWithLifecycle()
        val log by plugin.log.entries.collectAsStateWithLifecycle()
        LearnedPredictionContent(
            status = status,
            log = log,
            onTrain = plugin::trainNow,
            onStop = plugin::stopTraining,
            onReset = plugin::resetModel
        )
    }
}

@Composable
internal fun LearnedPredictionContent(
    status: LearnedStatus,
    log: List<SyncLogEntry>,
    onTrain: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val training = status.state == LearnedState.TRAINING
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        StatusCard(status)
        status.model?.let { model ->
            ErrorsCard(model)
            LearnedCard(model)
        }
        ActionButtons(
            listOf(
                ScreenAction(stringResource(ApsStrings.learned_train_now), Icons.Default.PlayArrow, enabled = !training, onClick = onTrain),
                ScreenAction(stringResource(ApsStrings.learned_stop), Icons.Default.Stop, visible = training, onClick = onStop),
                ScreenAction(stringResource(ApsStrings.learned_reset), Icons.Default.DeleteOutline, visible = status.model != null && !training, onClick = onReset)
            )
        )
        HorizontalDivider()
        SyncLogList(
            entries = log,
            dateUtil = LocalDateUtil.current,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun StatusCard(status: LearnedStatus) {
    val dateUtil = LocalDateUtil.current
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            val model = status.model
            // One line per curve, the expected one first. Too few stored AutoISF runs is a different reason than
            // losing to AutoISF, and the same for both curves.
            val lines: List<Pair<String, Boolean>> = when {
                status.state == LearnedState.NO_MODEL -> listOf(stringResource(ApsStrings.learned_state_no_model) to false)
                status.state == LearnedState.TRAINING -> listOf(stringResource(ApsStrings.learned_state_training) to false)
                model == null                         -> emptyList()
                model.autoIsfCoverage < Trainer.MIN_COVERAGE ->
                    listOf(stringResource(ApsStrings.learned_state_no_comparison, (model.autoIsfCoverage * 100).roundToInt()) to true)

                else                                  -> listOfNotNull(
                    if (model.expected == null) null
                    else if (model.expectedReady) stringResource(ApsStrings.learned_state_ready_expected) to false
                    else stringResource(ApsStrings.learned_state_not_ready_expected) to true,
                    if (model.ready) stringResource(ApsStrings.learned_state_ready) to false
                    else stringResource(ApsStrings.learned_state_not_ready) to true
                )
            }
            lines.forEach { (text, warning) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (warning) AapsTheme.generalColors.statusWarning else MaterialTheme.colorScheme.onSurface
                )
            }
            if (status.state == LearnedState.TRAINING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                status.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            status.model?.let { model ->
                Text(
                    text = stringResource(ApsStrings.learned_last_training, dateUtil.dateAndTimeString(model.trainedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(ApsStrings.learned_trained_on, model.windowDays, model.samples),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(ApsStrings.learned_phases, model.phases),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (model.trainingSeconds.isNotEmpty()) {
                    val seconds = model.trainingSeconds
                    Text(
                        text = stringResource(
                            ApsStrings.learned_training_time, duration(seconds.values.sum()), duration(seconds["linear"] ?: 0),
                            duration(seconds["network"] ?: 0), duration(seconds["gru"] ?: 0), duration(seconds["measure"] ?: 0)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A time in whole seconds below a minute, else in whole minutes */
@Composable
private fun duration(seconds: Int): String =
    if (seconds < 60) stringResource(CoreUiStrings.secs, seconds) else stringResource(CoreUiStrings.format_mins, (seconds + 30) / 60)

/** The mean error at 30, 60 and 120 minutes of both curves, AutoISF and "BG stays the same" on the test days */
@Composable
private fun ErrorsCard(model: LearnedModel) {
    val profileUtil = LocalProfileUtil.current
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(ApsStrings.learned_errors_title), style = MaterialTheme.typography.titleSmall)
            ErrorRow("", listOf(30, 60, 120).map { stringResource(CoreUiStrings.format_mins, it) }, bold = true)
            fun values(metrics: Metrics?) = listOf(metrics?.mae30, metrics?.mae60, metrics?.mae120)
                .map { value -> value?.let { profileUtil.fromMgdlToStringWithUnits(it) } ?: "–" }
            // All rows on the moments with a stored AutoISF run, so they compare fairly
            val paired = model.modelOnAutoIsfMoments != null
            if (model.expected != null) {
                ErrorRow(stringResource(ApsStrings.learned_row_expected), values(model.expectedOnAutoIsfMoments ?: model.expectedMetrics), bold = true)
            }
            ErrorRow(stringResource(ApsStrings.learned_row_no_insulin), values(model.modelOnAutoIsfMoments ?: model.model), bold = true)
            ErrorRow(stringResource(ApsStrings.openaps_auto_isf), values(model.autoIsf))
            ErrorRow(stringResource(ApsStrings.learned_row_naive), values(model.naiveOnAutoIsfMoments ?: model.naive))
            Text(
                text = if (paired) stringResource(ApsStrings.learned_compared_on, (model.autoIsfCoverage * 100).roundToInt())
                else stringResource(ApsStrings.learned_no_autoisf_runs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorRow(label: String, values: List<String>, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else null
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = weight, modifier = Modifier.weight(1.4f))
        values.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** The personal ISF and the drift by time of day, the carb factor and how fast carbs act */
@Composable
private fun LearnedCard(model: LearnedModel) {
    val profileUtil = LocalProfileUtil.current
    val parts = listOf(
        stringResource(ApsStrings.learned_part_night),
        stringResource(ApsStrings.learned_part_morning),
        stringResource(ApsStrings.learned_part_day),
        stringResource(ApsStrings.learned_part_evening)
    )
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
            Text(stringResource(ApsStrings.learned_learned_title), style = MaterialTheme.typography.titleSmall)
            // One line per time of day: the personal ISF and what BG does on its own
            Trainer.DAY_PARTS.forEachIndexed { k, hour ->
                val angle = Trainer.angleOfHour(hour)
                val percent = (model.linear.isfMultiplier(angle) * 100).roundToInt()
                val drift = profileUtil.fromMgdlToStringWithUnits(model.linear.driftPerHour(angle))
                Text(stringResource(ApsStrings.learned_part_line, parts[k], percent, drift), style = MaterialTheme.typography.bodySmall)
            }
            val weights = model.linear.carbWeights()
            val mainHours = CARB_HOURS[weights.indices.maxByOrNull { weights[it] } ?: 0]
            Text(
                stringResource(ApsStrings.learned_carbs_line, (model.linear.carbFactor() * 100).roundToInt(), mainHours),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
