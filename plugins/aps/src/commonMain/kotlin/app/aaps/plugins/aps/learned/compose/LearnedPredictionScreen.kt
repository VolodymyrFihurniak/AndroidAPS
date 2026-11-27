package app.aaps.plugins.aps.learned.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.LearnedParts
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
import app.aaps.core.ui.compose.log.syncLogItems
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.learned.LearnedPredictionPlugin
import app.aaps.plugins.aps.learned.LearnedState
import app.aaps.plugins.aps.learned.LearnedStatus
import app.aaps.plugins.aps.learned.LiveScore
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LowCheck
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.train.Samples
import app.aaps.plugins.aps.learned.train.Trainer
import kotlinx.coroutines.launch
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
        val autoIsfCopyDays by plugin.autoIsfCopyDays.collectAsStateWithLifecycle()
        val liveScore by plugin.liveScore.collectAsStateWithLifecycle()
        val forecast by plugin.forecast.collectAsStateWithLifecycle()
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        LearnedPredictionContent(
            status = status,
            log = log,
            autoIsfCopyDays = autoIsfCopyDays,
            liveScore = liveScore,
            parts60 = forecast?.parts60,
            onTrain = plugin::trainNow,
            onStop = plugin::stopTraining,
            onReset = plugin::resetModel,
            onCopyReport = { scope.launch { clipboard.setClipEntry(plainTextClip(plugin.report())) } }
        )
    }
}

@Composable
internal fun LearnedPredictionContent(
    status: LearnedStatus,
    log: List<SyncLogEntry>,
    autoIsfCopyDays: Int,
    liveScore: LiveScore?,
    parts60: LearnedParts?,
    onTrain: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val training = status.state == LearnedState.TRAINING
    val dateUtil = LocalDateUtil.current
    // One list for the whole screen: the cards are taller than a phone screen, and the log goes on below them
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        item(key = TOP_KEY) {
            Column(
                modifier = Modifier.padding(bottom = AapsSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                StatusCard(status, autoIsfCopyDays, liveScore)
                status.model?.let { model ->
                    parts60?.let { PartsCard(it) }
                    liveScore?.let { LiveCard(it, hasGru = model.expected != null) }
                    ErrorsCard(model)
                    LearnedCard(model)
                }
                ActionButtons(
                    listOf(
                        ScreenAction(stringResource(ApsStrings.learned_train_now), Icons.Default.PlayArrow, enabled = !training, onClick = onTrain),
                        ScreenAction(stringResource(ApsStrings.learned_stop), Icons.Default.Stop, visible = training, onClick = onStop),
                        ScreenAction(stringResource(ApsStrings.learned_reset), Icons.Default.DeleteOutline, visible = status.model != null && !training, onClick = onReset),
                        ScreenAction(stringResource(ApsStrings.learned_copy_report), Icons.Default.ContentCopy, onClick = onCopyReport)
                    )
                )
                HorizontalDivider()
            }
        }
        syncLogItems(log, dateUtil)
    }
}

/** The key of the cards and buttons above the log; the log lines use their own number as the key */
private const val TOP_KEY = "top"

@Composable
private fun StatusCard(status: LearnedStatus, autoIsfCopyDays: Int, liveScore: LiveScore?) {
    val dateUtil = LocalDateUtil.current
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            val model = status.model
            // One line per model, from the live check of the last 7 days. Before it has enough moments with an AutoISF
            // prediction, one line says how far it is: that is not the same as losing to AutoISF.
            val lines: List<Pair<String, Boolean>> = when {
                status.state == LearnedState.NO_MODEL -> listOf(stringResource(ApsStrings.learned_state_no_model) to false)
                status.state == LearnedState.TRAINING -> listOf(stringResource(ApsStrings.learned_state_training) to false)
                model == null                         -> emptyList()
                liveScore == null || !liveScore.enough ->
                    listOf(stringResource(ApsStrings.learned_live_collecting, liveScore?.pairedMoments ?: 0, LiveScore.MIN_PAIRED_MOMENTS) to true)

                else                                  -> listOfNotNull(
                    if (model.expected == null) null
                    else if (liveScore.gruReady) stringResource(ApsStrings.learned_state_ready_expected) to false
                    else stringResource(ApsStrings.learned_state_not_ready_expected) to true,
                    if (liveScore.linearReady) stringResource(ApsStrings.learned_state_ready) to false
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
                // How far the plugin's own copy of the AutoISF predictions goes back, while the checks need more of it
                if (liveScore?.enough != true) {
                    Text(
                        text = stringResource(ApsStrings.learned_autoisf_copy, minOf(autoIsfCopyDays, Samples.TEST_DAYS), Samples.TEST_DAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The live check of the last 7 days: the models next to AutoISF and "BG stays the same" on the moments with an AutoISF
 * prediction, or, before there are enough of those, the models and "BG stays the same" on all moments.
 */
@Composable
private fun LiveCard(score: LiveScore, hasGru: Boolean) {
    val profileUtil = LocalProfileUtil.current
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(ApsStrings.learned_live_title), style = MaterialTheme.typography.titleSmall)
            ErrorRow("", listOf(30, 60, 120).map { stringResource(CoreUiStrings.format_mins, it) }, bold = true)
            fun values(metrics: Metrics?) = listOf(metrics?.mae30, metrics?.mae60, metrics?.mae120)
                .map { value -> value?.let { profileUtil.fromMgdlToStringWithUnits(it) } ?: "–" }
            val paired = score.enough
            if (hasGru) ErrorRow(stringResource(ApsStrings.learned_row_expected), values(if (paired) score.pairedGru else score.gru), bold = true)
            ErrorRow(stringResource(ApsStrings.learned_row_no_insulin), values(if (paired) score.pairedLinear else score.linear), bold = true)
            ErrorRow(stringResource(ApsStrings.openaps_auto_isf), values(if (paired) score.pairedAutoIsf else null))
            ErrorRow(stringResource(ApsStrings.learned_row_naive), values(if (paired) score.pairedNaive else score.naive))
            Text(
                text = stringResource(ApsStrings.learned_live_moments, score.moments, score.pairedMoments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What makes the linear curve change by +60 minutes now, one row per part */
@Composable
private fun PartsCard(parts: LearnedParts) {
    val profileUtil = LocalProfileUtil.current
    val unit = stringResource(if (profileUtil.units == GlucoseUnit.MGDL) CoreUiStrings.mgdl else CoreUiStrings.mmol)
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
            Text(stringResource(ApsStrings.learned_parts_title, unit), style = MaterialTheme.typography.titleSmall)
            listOf(
                ApsStrings.learned_parts_insulin to parts.insulin,
                ApsStrings.learned_parts_carbs to parts.carbs,
                ApsStrings.learned_parts_meal to parts.meal,
                ApsStrings.learned_parts_drift to parts.drift,
                ApsStrings.learned_parts_trend to parts.trend,
                ApsStrings.learned_parts_network to parts.network
            ).forEach { (label, mgdl) -> ErrorRow(stringResource(label), listOf(profileUtil.fromMgdlToSignedStringInUnits(mgdl))) }
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
            // With enough AutoISF runs all rows are on their moments, so they compare fairly. With too few the models
            // and "BG stays the same" are shown on all moments of the test days: a few hours say little.
            val percent = (model.autoIsfCoverage * 100).roundToInt()
            val paired = model.modelOnAutoIsfMoments != null && model.autoIsfCoverage >= Trainer.MIN_COVERAGE
            if (model.expected != null) {
                ErrorRow(stringResource(ApsStrings.learned_row_expected), values(if (paired) model.expectedOnAutoIsfMoments else model.expectedMetrics), bold = true)
            }
            ErrorRow(stringResource(ApsStrings.learned_row_no_insulin), values(if (paired) model.modelOnAutoIsfMoments else model.model), bold = true)
            if (model.cgmOnly != null) ErrorRow(stringResource(ApsStrings.learned_row_cgm_only), values(if (paired) model.cgmOnlyOnAutoIsfMoments else model.cgmOnly))
            ErrorRow(stringResource(ApsStrings.openaps_auto_isf), values(if (paired) model.autoIsf else null))
            ErrorRow(stringResource(ApsStrings.learned_row_naive), values(if (paired) model.naiveOnAutoIsfMoments else model.naive))
            Text(
                text = when {
                    paired                         -> stringResource(ApsStrings.learned_compared_on, percent)
                    model.autoIsf != null          -> stringResource(ApsStrings.learned_all_moments, percent)
                    else                           -> stringResource(ApsStrings.learned_no_autoisf_runs)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val names = mapOf(
                LearnedModel.EXPECTED to stringResource(ApsStrings.learned_row_expected),
                LearnedModel.LINEAR to stringResource(ApsStrings.learned_row_no_insulin),
                LearnedModel.CGM_ONLY to stringResource(ApsStrings.learned_row_cgm_only),
                LearnedModel.AUTOISF to stringResource(ApsStrings.openaps_auto_isf),
                LearnedModel.NAIVE to stringResource(ApsStrings.learned_row_naive)
            )
            // How sure the difference to AutoISF is: a range that holds 0 means the test days cannot tell them apart
            if (paired && model.versusAutoIsf60.isNotEmpty()) {
                val ranges = LearnedModel.CURVES.mapNotNull { curve ->
                    model.versusAutoIsf60[curve]?.let { range ->
                        "${names.getValue(curve)} ${profileUtil.fromMgdlToSignedStringInUnits(range.mean)} " +
                            "(${profileUtil.fromMgdlToSignedStringInUnits(range.low)} … ${profileUtil.fromMgdlToSignedStringInUnits(range.high)})"
                    }
                }.joinToString(", ")
                Text(stringResource(ApsStrings.learned_versus_autoisf, ranges), style = MaterialTheme.typography.bodySmall)
            }
            // Lows: an error that is small on average can still miss them
            val lows = if (paired) model.lowsOnAutoIsfMoments else model.lows
            val lowCount = lows.values.firstOrNull()?.lows
            if (lowCount != null) {
                val seen = LearnedModel.CURVES.mapNotNull { curve -> lows[curve]?.let { "${names.getValue(curve)} ${it.caught} (${it.falseWarnings})" } }.joinToString(", ")
                Text(
                    stringResource(ApsStrings.learned_lows_seen, profileUtil.fromMgdlToStringWithUnits(LowCheck.LOW_MGDL), lowCount, seen),
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
