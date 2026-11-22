package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.train.Trainer
import kotlin.math.roundToInt

/**
 * autoISF build, personal prediction: a plain text report of the model, the checks and the log, for the "Copy report"
 * button: everything needed to see what the model learned and how good it is, in one paste. BG values in mg/dl. It is
 * for finding problems, so it is in English like the log.
 */
object LearnedReport {

    /** The log lines in the report, newest first */
    const val MAX_LOG_LINES = 300

    fun build(model: LearnedModel?, live: LiveScore?, autoIsfCopyDays: Int, log: List<SyncLogEntry>, time: (Long) -> String): String = buildString {
        appendLine("Personal prediction report")
        if (model == null) appendLine("No model")
        else {
            appendLine("Model: version ${model.version}, trained ${time(model.trainedAt)}, ${model.windowDays} days, ${model.samples} samples, ${model.phases} phases")
            appendLine("Training seconds: " + model.trainingSeconds.entries.joinToString(", ") { "${it.key} ${it.value}" })
            appendLine("ISF multiplier before the limits: " + Trainer.DAY_PARTS.joinToString(", ") { "$it:00 ${number(model.linear.rawIsfMultiplier(Trainer.angleOfHour(it)))}" })
            appendLine("BG on its own per hour before the limit: " + Trainer.DAY_PARTS.joinToString(", ") { "$it:00 ${number(model.linear.rawDriftPerHour(Trainer.angleOfHour(it)))}" })
            appendLine("Carb factor ${number(model.linear.carbFactor())}, weights by absorption hours " + model.linear.carbWeights().joinToString(", ") { number(it) })
            appendLine("Test days, mean error 30/60/120 min: GRU ${metrics(model.expectedMetrics)}, linear ${metrics(model.model)}, BG stays ${metrics(model.naive)}")
            appendLine(
                "Test days with an AutoISF run (${(model.autoIsfCoverage * 100).roundToInt()} %): GRU ${metrics(model.expectedOnAutoIsfMoments)}, " +
                    "linear ${metrics(model.modelOnAutoIsfMoments)}, AutoISF ${metrics(model.autoIsf)}, BG stays ${metrics(model.naiveOnAutoIsfMoments)}"
            )
        }
        if (live == null) appendLine("Live check: not made yet")
        else {
            appendLine("Live check, last 7 days: ${live.moments} moments, ${live.pairedMoments} with AutoISF (${LiveScore.MIN_PAIRED_MOMENTS} needed)")
            appendLine("  all moments: GRU ${metrics(live.gru)}, linear ${metrics(live.linear)}, BG stays ${metrics(live.naive)}")
            appendLine(
                "  with AutoISF: GRU ${metrics(live.pairedGru)}, linear ${metrics(live.pairedLinear)}, AutoISF ${metrics(live.pairedAutoIsf)}, " +
                    "BG stays ${metrics(live.pairedNaive)}"
            )
            appendLine("  may give a dose: GRU ${live.gruReady}, linear ${live.linearReady}")
        }
        appendLine("AutoISF predictions kept by the plugin: $autoIsfCopyDays days")
        appendLine("Log:")
        for (entry in log.take(MAX_LOG_LINES)) appendLine("${time(entry.date)} ${entry.action}" + (entry.text?.let { ": $it" } ?: ""))
    }

    private fun metrics(metrics: Metrics?): String =
        metrics?.let { "${number(it.mae30)}/${number(it.mae60)}/${number(it.mae120)}" } ?: "-"

    private fun number(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
}
