package app.aaps.plugins.aps.learned.model

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** autoISF build, personal prediction: mean absolute error at 30, 60 and 120 minutes, mg/dl */
@Serializable
data class Metrics(val mae30: Double, val mae60: Double, val mae120: Double)

/**
 * autoISF build, personal prediction: how a curve sees lows on the test days. A low is BG below
 * [LOW_MGDL] at 30 or 60 minutes after a moment (the points AutoISF is kept at, so every curve is judged the same way).
 * [lows]: moments followed by a low; [caught]: of those, the ones the curve also put below the mark; [warnings]:
 * moments where the curve put BG below the mark; [falseWarnings]: of those, the ones without a low.
 */
@Serializable
data class LowCheck(val lows: Int, val caught: Int, val warnings: Int, val falseWarnings: Int) {

    /** The part of the lows the curve saw coming, null without lows */
    val caughtShare: Double? get() = if (lows > 0) caught.toDouble() / lows else null

    companion object {

        const val LOW_MGDL = 70.0
    }
}

/** autoISF build, personal prediction: a difference of errors, mg/dl, with its 95 % range over the test days */
@Serializable
data class Range(val mean: Double, val low: Double, val high: Double) {

    /** True when the whole range is below 0: the curve is surely better than the other */
    val surelyBelowZero: Boolean get() = high < 0.0
}

/**
 * autoISF build, personal prediction: everything a trained model needs to predict, and how good it was on the test
 * days. Saved as JSON in a preference.
 */
@Serializable
data class LearnedModel(
    // Required: a model saved without it (version 1 never wrote it) is not read, so it is learned again
    @Required val version: Int = VERSION,
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
    /** The model and "BG stays the same" on the moments with a stored AutoISF run, to compare fairly with [autoIsf] */
    val modelOnAutoIsfMoments: Metrics? = null,
    val naiveOnAutoIsfMoments: Metrics? = null,
    /** Model error on the test days for each of the 24 points */
    val typicalErrorMgdl: List<Double>,
    /** True when the model beat AutoISF at 60 minutes on the same moments, which cover at least half of the test days */
    val ready: Boolean,
    /** How often the network residual hit the limit on the test days */
    val clampHits: Int,
    /** v2: the GRU of the expected curve (where BG really goes, the loop included), null when it was not learned */
    val expected: GruState? = null,
    /** Scaling of the GRU step rows and context values: (x - mean) / std */
    val expectedStepMean: List<Float> = emptyList(),
    val expectedStepStd: List<Float> = emptyList(),
    val expectedContextMean: List<Float> = emptyList(),
    val expectedContextStd: List<Float> = emptyList(),
    /** Errors of the expected curve on the test days: all moments, and the moments with a stored AutoISF run */
    val expectedMetrics: Metrics? = null,
    val expectedOnAutoIsfMoments: Metrics? = null,
    /** Expected curve error on the test days for each of the 24 points */
    val expectedTypicalErrorMgdl: List<Double> = emptyList(),
    /** True when the expected curve beat AutoISF at 60 minutes on the same moments, which cover at least half of the test days */
    val expectedReady: Boolean = false,
    /** How many 1-minute phases of the history it learned from: 5 with a 1-minute sensor, else 1 */
    val phases: Int = 1,
    /** Seconds of each part of the training: "linear", "network", "gru", "measure" */
    val trainingSeconds: Map<String, Int> = emptyMap(),
    /**
     * The typical errors per point of each curve for each group of `ErrorBands` (steady, moderate, fast BG change); an
     * empty list for a group with too few test moments
     */
    val typicalErrorByChange: List<List<Double>> = emptyList(),
    val expectedTypicalErrorByChange: List<List<Double>> = emptyList(),
    /**
     * v6: a yardstick that reads only BG (`CgmOnlyBaseline`), on all test moments and on the moments with a stored
     * AutoISF run. Never drawn.
     */
    val cgmOnly: Metrics? = null,
    val cgmOnlyOnAutoIsfMoments: Metrics? = null,
    /** How each curve saw lows, on the moments with a stored AutoISF run; keys in [CURVES] */
    val lowsOnAutoIsfMoments: Map<String, LowCheck> = emptyMap(),
    /** How each curve saw lows on all test moments; keys in [CURVES] except AutoISF */
    val lows: Map<String, LowCheck> = emptyMap(),
    /** The 60-minute error of each curve minus that of AutoISF on the same moments, with its range by test days */
    val versusAutoIsf60: Map<String, Range> = emptyMap()
) {

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {

        /**
         * 2: the ready rule needs AutoISF on the same moments; a model of version 1 may say ready wrongly.
         * 3: the expected curve; a model of version 2 has none, so it is learned again.
         * 4: the GRU has one more input per step (a meal going on), so a model of version 3 does not fit.
         * 5: and two more (steps and heart rate).
         * 6: the networks stop on validation days, not on the test days, and the GRU is measured without starting from
         *    a model that saw the test days; the ready flags of version 5 could be too kind, so it is learned again.
         */
        const val VERSION = 6
        const val OUTPUT_SCALE = 50f

        /** The names of the curves in [lows], [lowsOnAutoIsfMoments] and [versusAutoIsf60] */
        const val EXPECTED = "expected"
        const val LINEAR = "linear"
        const val CGM_ONLY = "cgm"
        const val AUTOISF = "autoisf"
        const val NAIVE = "naive"
        val CURVES = listOf(EXPECTED, LINEAR, CGM_ONLY, AUTOISF, NAIVE)

        private val json = Json { ignoreUnknownKeys = true }

        /** Null for an empty, broken or older model */
        fun fromJson(text: String): LearnedModel? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.version == VERSION }
    }
}
