package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class GraphConfigRepositoryImpl(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : GraphConfigRepository {

    // Not the main dispatcher: all this scope does is parse a preference string into a StateFlow,
    // which is safe from any thread, and reaching for `Dispatchers.Main` in a constructor makes the
    // object graph unbuildable in a plain JVM unit test (no main looper).
    private val scope = CoroutineScope(SupervisorJob() + aapsIoDispatcher)
    private val _graphConfigFlow = MutableStateFlow(load())
    override val graphConfigFlow: StateFlow<GraphConfig> = _graphConfigFlow.asStateFlow()

    init {
        // Sync from preferences when changed externally (e.g. import/restore)
        scope.launch {
            preferences.observe(StringNonKey.ComposeGraphConfig)
                .drop(1) // Skip initial (already loaded)
                .collect { json -> _graphConfigFlow.value = parseJson(json) }
        }
    }

    override fun update(config: GraphConfig) {
        _graphConfigFlow.value = config
        preferences.put(StringNonKey.ComposeGraphConfig, toJson(config))
    }

    private fun load(): GraphConfig = parseJson(preferences.get(StringNonKey.ComposeGraphConfig))

    private fun parseJson(json: String): GraphConfig {
        if (json.isBlank()) return GraphConfig()
        return try {
            fromJson(json)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to parse graph config, using defaults", e)
            GraphConfig()
        }
    }

    companion object {

        private const val KEY_SECONDARY_GRAPHS = "secondaryGraphs"
        private const val KEY_BG_OVERLAYS = "bgOverlays"
        private const val KEY_IOB_OVERLAYS = "iobOverlays"

        // autoISF build: the IOB layers with the basal band. A new key, so a config saved before the basal could be
        // switched off still shows it: under the old key BASAL is missing, which would read as "switched off".
        private const val KEY_IOB_LAYERS = "iobLayers"

        // autoISF build: the BG layers with IOB, COB, basal and the line. A new key for the same reason: a config
        // saved before had none of them, and that has to read as "not chosen yet", not as "switched off".
        private const val KEY_BG_LAYERS = "bgLayers"

        /** autoISF build: BG layers an older version does not know, left out of the old key */
        private val NEW_BG_LAYERS = listOf(SeriesType.IOB, SeriesType.COB, SeriesType.BASAL, SeriesType.BG_LINE, SeriesType.LEARNED_PREDICTION)

        // autoISF build: whether the personal prediction layer is on. A key of its own, so a config saved before the
        // layer existed shows it (absent = on), and one where the user switched it off keeps it off.
        private const val KEY_MODEL_LAYER = "modelLayer"
        private const val KEY_BG_HEIGHT = "bgHeight"
        private const val KEY_IOB_HEIGHT = "iobHeight"
        private const val KEY_SERIES = "series"
        private const val KEY_HEIGHT = "height"

        private fun overlaysToJson(overlays: List<SeriesType>) = buildJsonArray {
            for (type in overlays) add(type.name)
        }

        /**
         * Absent means "never written", so the caller's default applies. An empty array means the
         * user turned them all off, which is a different thing and is kept.
         */
        private fun overlaysFromJson(arr: JsonArray?, default: List<SeriesType>): List<SeriesType> {
            if (arr == null) return default
            val out = mutableListOf<SeriesType>()
            for (element in arr) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown types (forward compat)
                if (type !in out) out.add(type)
            }
            return out
        }

        fun toJson(config: GraphConfig): String =
            buildJsonObject {
                // The old key without the new layers, for a version that does not know them
                put(KEY_BG_OVERLAYS, overlaysToJson(config.bgOverlays - NEW_BG_LAYERS.toSet()))
                put(KEY_BG_LAYERS, overlaysToJson(config.bgOverlays))
                put(KEY_MODEL_LAYER, SeriesType.LEARNED_PREDICTION in config.bgOverlays)
                // The old key without BASAL, for a version that does not know it
                put(KEY_IOB_OVERLAYS, overlaysToJson(config.iobOverlays - SeriesType.BASAL))
                put(KEY_IOB_LAYERS, overlaysToJson(config.iobOverlays))
                put(KEY_BG_HEIGHT, config.bgHeight)
                put(KEY_IOB_HEIGHT, config.iobHeight)
                put(
                    KEY_SECONDARY_GRAPHS,
                    buildJsonArray {
                        for (graph in config.secondaryGraphs) {
                            addJsonObject {
                                put(KEY_SERIES, buildJsonArray { for (series in graph.series) add(series.name) })
                                put(KEY_HEIGHT, graph.height)
                            }
                        }
                    }
                )
            }.toString()

        private fun parseSeriesArray(seriesArray: JsonArray): List<SeriesType> {
            val series = mutableListOf<SeriesType>()
            for (element in seriesArray) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown series (forward compat)
                // IOB is now a fixed graph — strip it from configurable graphs (legacy migration)
                if (type == SeriesType.IOB) continue
                if (type !in series) series.add(type)
            }
            return series.take(2)
        }

        fun fromJson(json: String): GraphConfig {
            val obj = Json.parseToJsonElement(json) as JsonObject
            val bgLayers = obj.array(KEY_BG_LAYERS)
            // autoISF build: a config from before the BG layers gets IOB, COB and the basal in the BG graph, and loses
            // its graph with COB alone below it (the user asked to have COB in the BG graph instead).
            val beforeBgLayers = bgLayers == null && obj.isNotEmpty()
            val savedBgOverlays = bgLayers?.let { overlaysFromJson(it, emptyList()) }
                ?: if (beforeBgLayers) (overlaysFromJson(obj.array(KEY_BG_OVERLAYS), OLD_DEFAULT_BG_OVERLAYS) + SeriesType.IOB + SeriesType.COB + SeriesType.BASAL).distinct()
                else GraphConfig.DEFAULT_BG_OVERLAYS
            val modelLayer = (obj[KEY_MODEL_LAYER] as? JsonPrimitive)?.booleanOrNull ?: true
            val bgOverlays = if (modelLayer) (savedBgOverlays + SeriesType.LEARNED_PREDICTION).distinct() else savedBgOverlays - SeriesType.LEARNED_PREDICTION
            val iobOverlays = obj.array(KEY_IOB_LAYERS)?.let { overlaysFromJson(it, emptyList()) }
                ?: (overlaysFromJson(obj.array(KEY_IOB_OVERLAYS), listOf(SeriesType.ACTIVITY)) + SeriesType.BASAL).distinct()
            val bgHeight = obj.height(KEY_BG_HEIGHT)
            val iobHeight = obj.height(KEY_IOB_HEIGHT)
            val graphs = mutableListOf<SecondaryGraph>()
            for (raw in obj.array(KEY_SECONDARY_GRAPHS).orEmpty()) {
                // Legacy format: element is an array of series names.
                // New format: element is an object { series: [...], height: Int }.
                val (series, height) = when (raw) {
                    is JsonArray  -> parseSeriesArray(raw) to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                    is JsonObject -> (raw.array(KEY_SERIES)?.let { parseSeriesArray(it) } ?: emptyList()) to raw.height(KEY_HEIGHT)
                    else          -> emptyList<SeriesType>() to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                }
                if (series.isEmpty() || beforeBgLayers && series == listOf(SeriesType.COB)) continue
                graphs.add(SecondaryGraph(series, height))
            }
            return GraphConfig(
                bgOverlays = bgOverlays,
                iobOverlays = iobOverlays,
                bgHeight = bgHeight,
                iobHeight = iobHeight,
                secondaryGraphs = graphs
            )
        }

        /** The BG overlays of a config written before the autoISF BG layers, when it had none at all */
        private val OLD_DEFAULT_BG_OVERLAYS = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS, SeriesType.BG_PARABOLA)

        /** The named array, or null when it is absent or is something other than an array. */
        private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

        /** A stored height, defaulted when absent and always brought inside the allowed range. */
        private fun JsonObject.height(key: String): Int =
            ((this[key] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() } ?: GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
                .coerceIn(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)

        /** The series this element names, or null if it is not a name this version knows. */
        private fun JsonElement.seriesTypeOrNull(): SeriesType? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { name -> SeriesType.entries.firstOrNull { it.name == name } }
    }
}
