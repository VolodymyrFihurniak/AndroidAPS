package app.aaps.ui.food

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * autoISF build: packaged foods from Open Food Facts (world.openfoodfacts.org, data under the Open Database License,
 * (c) Open Food Facts contributors), by name or by barcode. The idea and the fields come from JugglucoNG
 * (`OpenFoodFacts.kt`, GPL-3.0). Only the typed words or the barcode are sent, nothing about the user.
 *
 * Any failure is "nothing found": the user can always type the carbs.
 */
internal class OpenFoodFactsClient(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun byBarcode(barcode: String): FoodItem? {
        val code = barcode.filter { it.isDigit() }
        if (code.length < 6) return null
        return request {
            val response = http.get("$BASE/api/v2/product/$code.json") {
                header("User-Agent", USER_AGENT)
                parameter("fields", FIELDS)
            }
            if (!response.status.isSuccess()) return@request null
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return@request null
            (root["product"] as? JsonObject)?.let { parseProduct(it) }
        }
    }

    suspend fun search(query: String): List<FoodItem> {
        if (query.trim().length < 3) return emptyList()
        return request {
            val response = http.get("$BASE/cgi/search.pl") {
                header("User-Agent", USER_AGENT)
                parameter("search_terms", query.trim())
                parameter("search_simple", 1)
                parameter("action", "process")
                parameter("json", 1)
                parameter("page_size", PAGE_SIZE)
                parameter("fields", FIELDS)
            }
            if (!response.status.isSuccess()) return@request null
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return@request null
            (root["products"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { p -> parseProduct(p) } }
        }.orEmpty()
    }

    internal fun parseProduct(product: JsonObject): FoodItem? {
        val nutriments = product["nutriments"] as? JsonObject ?: return null
        val carbs = nutriments.number("carbohydrates_100g") ?: return null
        val name = product.text("product_name").ifBlank { product.text("generic_name") }.ifBlank { return null }
        val brand = product.text("brands").substringBefore(',').trim()
        val serving = product.number("serving_quantity")?.takeIf { it > 0.0 && it < 2000.0 }
        return FoodItem(
            name = if (brand.isNotEmpty()) "$name ($brand)" else name,
            carbsPer100 = carbs.coerceAtLeast(0.0),
            proteinPer100 = nutriments.number("proteins_100g"),
            fatPer100 = nutriments.number("fat_100g"),
            defaultGrams = serving ?: 100.0,
            source = FoodItem.Source.OPEN_FOOD_FACTS
        )
    }

    private fun JsonObject.text(key: String): String = ((this[key] as? JsonPrimitive)?.contentOrNull ?: "").trim()

    private fun JsonObject.number(key: String): Double? {
        val value: JsonElement = this[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
    }

    private suspend fun <T> request(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    companion object {

        private const val BASE = "https://world.openfoodfacts.org"
        private const val FIELDS = "product_name,generic_name,brands,nutriments,serving_quantity"
        private const val PAGE_SIZE = 15

        /** Open Food Facts asks apps to name themselves. No contact details, they would identify the user. */
        private const val USER_AGENT = "AAPS-autoISF/1.0"
    }
}
