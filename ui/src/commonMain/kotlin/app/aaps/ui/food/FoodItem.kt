package app.aaps.ui.food

import app.aaps.core.objects.food.Nutrients

/**
 * autoISF build: one food the meal calculator can add, from any source. Values per 100 g.
 *
 * @param defaultGrams the usual portion, offered first
 * @param source where it came from, shown next to the name
 * @param unit the unit of the amount: "g" for most foods. A food of My foods can count in other units
 *   ("ml", "piece"), then the "per 100" values are per 100 of that unit.
 */
data class FoodItem(
    val name: String,
    val carbsPer100: Double,
    val proteinPer100: Double?,
    val fatPer100: Double?,
    val defaultGrams: Double,
    val source: Source,
    val unit: String = "g"
) {

    enum class Source { MY_FOODS, COMMON, OPEN_FOOD_FACTS, AI }

    fun nutrients(grams: Double): Nutrients = Nutrients.of(grams, carbsPer100, proteinPer100, fatPer100)
}

/** A food in the meal, with its weight */
data class MealPart(val food: FoodItem, val grams: Double) {

    val nutrients: Nutrients get() = food.nutrients(grams)
}
