package app.aaps.ui.food

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * autoISF build: carbs from a photo of a meal, the idea of the AI food search of iAPS
 * (github.com/Artificial-Pancreas/iAPS, MIT licence, `Services/FoodSearch/AI`). The user brings their own API key
 * of OpenAI, Anthropic or Google. The photo goes to that provider and to nobody else.
 *
 * The answer is only a guess for the user to check: it fills the meal list, it never doses by itself.
 */
class AiFoodClient(private val http: HttpClient) {

    enum class Provider(val defaultModel: String) {
        OPENAI("gpt-4o-mini"),
        ANTHROPIC("claude-sonnet-5-5"),
        GEMINI("gemini-2.5-flash")
    }

    sealed class Result {
        data class Foods(val items: List<FoodItem>) : Result()
        data class Failed(val message: String) : Result()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun analyse(jpeg: ByteArray, provider: Provider, apiKey: String, model: String): Result {
        if (apiKey.isBlank()) return Result.Failed("no API key")
        val image = Base64.encode(jpeg)
        val useModel = model.ifBlank { provider.defaultModel }
        return try {
            val text = when (provider) {
                Provider.OPENAI    -> openAi(image, apiKey, useModel)
                Provider.ANTHROPIC -> anthropic(image, apiKey, useModel)
                Provider.GEMINI    -> gemini(image, apiKey, useModel)
            }
            val items = parseItems(text)
            Result.Foods(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(e.message ?: e::class.simpleName ?: "error")
        }
    }

    private suspend fun post(url: String, body: JsonObject, headers: Map<String, String>): JsonObject {
        val response = http.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: ${text.take(200)}")
        return json.parseToJsonElement(text) as? JsonObject ?: error("bad answer")
    }

    private suspend fun openAi(image: String, key: String, model: String): String {
        val body = buildJsonObject {
            put("model", model)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", PROMPT) }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$image") }
                        }
                    }
                }
            }
        }
        val root = post("https://api.openai.com/v1/chat/completions", body, mapOf("Authorization" to "Bearer $key"))
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        return ((choice?.get("message") as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull ?: error("empty answer")
    }

    private suspend fun anthropic(image: String, key: String, model: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64"); put("media_type", "image/jpeg"); put("data", image)
                            }
                        }
                        addJsonObject { put("type", "text"); put("text", PROMPT) }
                    }
                }
            }
        }
        val root = post(
            "https://api.anthropic.com/v1/messages", body,
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
        )
        val parts = (root["content"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        return parts.firstNotNullOfOrNull { (it["text"] as? JsonPrimitive)?.contentOrNull } ?: error("empty answer")
    }

    private suspend fun gemini(image: String, key: String, model: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", PROMPT) }
                        addJsonObject {
                            putJsonObject("inline_data") { put("mime_type", "image/jpeg"); put("data", image) }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val root = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body,
            mapOf("x-goog-api-key" to key)
        )
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        val parts = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        return parts.firstNotNullOfOrNull { (it["text"] as? JsonPrimitive)?.contentOrNull } ?: error("empty answer")
    }

    /** Reads `{"items":[{"name","grams","carbs","protein","fat"}]}`, also when the model wraps it in text */
    internal fun parseItems(text: String): List<FoodItem> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val root = try {
            json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = (item["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val grams = item.num("grams")?.takeIf { it > 0.0 && it < 3000.0 } ?: return@mapNotNull null
            val carbs = item.num("carbs")?.takeIf { it >= 0.0 } ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            FoodItem(
                name = name,
                carbsPer100 = carbs / grams * 100.0,
                proteinPer100 = item.num("protein")?.let { it / grams * 100.0 },
                fatPer100 = item.num("fat")?.let { it / grams * 100.0 },
                defaultGrams = grams,
                source = FoodItem.Source.AI
            )
        }
    }

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    companion object {

        internal const val PROMPT =
            "You see a photo of a meal. List each food on the plate with an estimated weight in grams and, for that " +
                "weight, the grams of carbohydrate (without fibre), protein and fat. Answer only with JSON in this form: " +
                "{\"items\":[{\"name\":\"rice\",\"grams\":150,\"carbs\":42,\"protein\":4,\"fat\":1}]}. " +
                "Use short food names in the language of any text on the photo, else English. If you see no food, answer {\"items\":[]}."
    }
}
