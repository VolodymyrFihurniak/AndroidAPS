package app.aaps.ui.food

import androidx.compose.ui.text.intl.Locale
import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.food.CommonFood
import app.aaps.core.objects.food.CommonFoods
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.withContext

/**
 * autoISF build: all food sources of the meal calculator in the carbs dialog.
 *
 * - My foods (the food table of AAPS) and the common foods: on the phone, no network.
 * - Open Food Facts: by name or barcode, needs network.
 * - A meal photo: sent to the AI service the user chose, with the user's own key.
 */
@SingleIn(AppScope::class)
@Inject
class MealRepository(
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val openFoodFacts by lazy { OpenFoodFactsClient(httpClient(OFF_TIMEOUT_MS)) }
    private val ai by lazy { AiFoodClient(httpClient(AI_TIMEOUT_MS)) }

    val isConnected: Boolean get() = receiverStatusStore.isConnected

    /** My foods first, then the common foods. Empty [query] lists everything. */
    suspend fun localSearch(query: String): List<FoodItem> {
        val q = query.trim().lowercase()
        val mine = withContext(aapsIoDispatcher) { runCatching { persistenceLayer.getFoods() }.getOrDefault(emptyList()) }
            .filter { it.isValid && (q.isEmpty() || it.name.lowercase().contains(q)) }
            .mapNotNull { it.toFoodItem() }
            .sortedBy { it.name.lowercase() }
        val common = CommonFoods.search(query, language()).map { it.toFoodItem(language()) }
        return mine + common
    }

    suspend fun onlineSearch(query: String): List<FoodItem> =
        if (!isConnected) emptyList() else withContext(aapsIoDispatcher) { openFoodFacts.search(query) }

    suspend fun byBarcode(barcode: String): FoodItem? =
        if (!isConnected) null else withContext(aapsIoDispatcher) { openFoodFacts.byBarcode(barcode) }

    /** The chosen photo service, or null when it is off or has no key */
    val aiProvider: AiFoodClient.Provider?
        get() {
            if (preferences.get(StringKey.FoodAiApiKey).isBlank()) return null
            return when (preferences.get(StringKey.FoodAiProvider)) {
                "openai"    -> AiFoodClient.Provider.OPENAI
                "anthropic" -> AiFoodClient.Provider.ANTHROPIC
                "gemini"    -> AiFoodClient.Provider.GEMINI
                else        -> null
            }
        }

    suspend fun analysePhoto(jpeg: ByteArray): AiFoodClient.Result {
        val provider = aiProvider ?: return AiFoodClient.Result.Failed("off")
        if (!isConnected) return AiFoodClient.Result.Failed("offline")
        val model = preferences.get(StringKey.FoodAiModel).trim().ifEmpty { provider.defaultModel }
        return withContext(aapsIoDispatcher) { ai.analyse(jpeg, provider, preferences.get(StringKey.FoodAiApiKey).trim(), model) }
    }

    private fun language(): String = (TextRefValueRegistry.locale ?: Locale.current.toLanguageTag()).substringBefore('-').substringBefore('_')

    private fun httpClient(timeoutMs: Long) = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = OFF_TIMEOUT_MS
            requestTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
        }
    }

    companion object {

        private const val OFF_TIMEOUT_MS = 10_000L
        private const val AI_TIMEOUT_MS = 60_000L

        /** A food of My foods counts per portion. Turn it into per 100 of its unit. */
        internal fun FD.toFoodItem(): FoodItem? {
            if (portion <= 0.0) return null
            val factor = 100.0 / portion
            return FoodItem(
                name = name,
                carbsPer100 = carbs * factor,
                proteinPer100 = protein?.let { it * factor },
                fatPer100 = fat?.let { it * factor },
                defaultGrams = portion,
                source = FoodItem.Source.MY_FOODS,
                unit = unit.ifBlank { "g" }
            )
        }

        internal fun CommonFood.toFoodItem(language: String) = FoodItem(
            name = name(language),
            carbsPer100 = carbs,
            proteinPer100 = protein,
            fatPer100 = fat,
            defaultGrams = defaultPortionGrams,
            source = FoodItem.Source.COMMON
        )
    }
}
