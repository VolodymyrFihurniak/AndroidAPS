package app.aaps.core.objects.food

import kotlin.math.round

/**
 * autoISF build: one common food (see [COMMON_FOODS]). Values per 100 g.
 *
 * @param portions household measures as `name:grams` pairs, for example `piece:182,cup:109`
 */
data class CommonFood(
    val key: String,
    val carbs: Double,
    val protein: Double?,
    val fat: Double?,
    val fiber: Double?,
    val kcal: Double?,
    val portions: String,
    val nameEn: String,
    val nameUk: String,
    val nameRu: String
) {

    /** The name in [language] (an ISO 639 code like "uk"), English when there is none */
    fun name(language: String): String = when (language) {
        "uk" -> nameUk
        "ru" -> nameRu
        else -> nameEn
    }.ifBlank { nameEn }

    /** Household measures, name to grams */
    val portionGrams: List<Pair<String, Double>>
        get() = portions.split(',').mapNotNull { part ->
            val name = part.substringBefore(':').trim()
            val grams = part.substringAfter(':', "").trim().toDoubleOrNull()
            if (name.isEmpty() || grams == null || grams <= 0.0) null else name to grams
        }

    /** The first household measure in grams, or 100 g when there is none */
    val defaultPortionGrams: Double get() = portionGrams.firstOrNull()?.second ?: 100.0
}

object CommonFoods {

    val all: List<CommonFood> get() = COMMON_FOODS

    /** Foods whose name (in [language] or English) or key holds [query], best matches first */
    fun search(query: String, language: String): List<CommonFood> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all.sortedBy { it.name(language).lowercase() }
        return all.mapNotNull { food ->
            val names = listOf(food.name(language), food.nameEn, food.key).map { it.lowercase() }
            val rank = when {
                names.any { it.startsWith(q) } -> 0
                names.any { it.contains(q) }   -> 1
                else                           -> return@mapNotNull null
            }
            rank to food
        }.sortedWith(compareBy({ it.first }, { it.second.name(language).lowercase() })).map { it.second }
    }
}

/** Carbs, protein and fat in grams for a weight of a food */
data class Nutrients(val carbs: Double, val protein: Double, val fat: Double) {

    operator fun plus(other: Nutrients) = Nutrients(carbs + other.carbs, protein + other.protein, fat + other.fat)

    companion object {

        val ZERO = Nutrients(0.0, 0.0, 0.0)

        /** Nutrients of [grams] of a food with [per100Carbs], [per100Protein], [per100Fat] per 100 g */
        fun of(grams: Double, per100Carbs: Double, per100Protein: Double?, per100Fat: Double?): Nutrients =
            Nutrients(
                carbs = round1(grams * per100Carbs / 100.0),
                protein = round1(grams * (per100Protein ?: 0.0) / 100.0),
                fat = round1(grams * (per100Fat ?: 0.0) / 100.0)
            )

        private fun round1(v: Double) = round(v * 10.0) / 10.0
    }
}
