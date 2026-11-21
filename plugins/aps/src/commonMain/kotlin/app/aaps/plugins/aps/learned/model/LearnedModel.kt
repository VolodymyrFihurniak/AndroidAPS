package app.aaps.plugins.aps.learned.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** autoISF build, personal prediction: mean absolute error at 30, 60 and 120 minutes, mg/dl */
@Serializable
data class Metrics(val mae30: Double, val mae60: Double, val mae120: Double)

/**
 * autoISF build, personal prediction: everything a trained model needs to predict, and how good it was on the test
 * days. Saved as JSON in a preference.
 */
@Serializable
data class LearnedModel(
    val version: Int = VERSION,
    val trainedAt: Long,
    /** Days of history it learned from */
    val windowDays: Int,
    /** Samples it learned from */
    val samples: Int,
    val linear: LinearModel,
    val network: NetworkState,
    /** Scaling of the network inputs: (x - mean) / std */
    val inputMean: List<Float>,
    val inputStd: List<Float>,
    /** The network outputs are the residuals divided by this */
    val outputScale: Float = OUTPUT_SCALE,
    /** Errors on the test days: the model, AutoISF (null without stored runs) and "BG stays the same" */
    val model: Metrics,
    val autoIsf: Metrics?,
    val naive: Metrics,
    /** Part of the test samples that had a stored AutoISF run */
    val autoIsfCoverage: Double,
    /** Model error on the test days for each of the 24 points */
    val typicalErrorMgdl: List<Double>,
    /** True when the model beat AutoISF (or "BG stays the same" without enough stored runs) at 60 minutes */
    val ready: Boolean,
    /** How often the network residual hit the limit on the test days */
    val clampHits: Int
) {

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {

        const val VERSION = 1
        const val OUTPUT_SCALE = 50f

        private val json = Json { ignoreUnknownKeys = true }

        /** Null for an empty, broken or older model */
        fun fromJson(text: String): LearnedModel? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.version == VERSION }
    }
}
