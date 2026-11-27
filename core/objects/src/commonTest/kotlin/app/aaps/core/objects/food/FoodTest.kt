package app.aaps.core.objects.food

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodTest {

    @Test fun fpuFollowsWarsawSteps() {
        // 20 g fat + 30 g protein = 300 kcal, * 0.5 / 10 = 15 g, 1.5 FPU -> 3 h
        val small = FatProteinUnits.compute(20.0, 30.0, 0.5, 60, 8)
        assertNotNull(small)
        assertEquals(15, small.carbs)
        assertEquals(3, small.durationHours)
        assertEquals(60, small.delayMinutes)
        // 50 g fat + 50 g protein = 650 kcal -> 32.5 g, 3.25 FPU -> 5 h
        assertEquals(5, FatProteinUnits.compute(50.0, 50.0, 0.5, 60, 8)?.durationHours)
        // a lot -> the time cap
        assertEquals(8, FatProteinUnits.compute(100.0, 60.0, 0.5, 60, 8)?.durationHours)
    }

    @Test fun fpuNothingForTinyAmounts() {
        assertNull(FatProteinUnits.compute(0.0, 0.0, 0.5, 60, 8))
        assertNull(FatProteinUnits.compute(1.0, 0.0, 0.5, 60, 8))
        assertNull(FatProteinUnits.compute(-5.0, 10.0, 0.5, 60, 8))
    }

    @Test fun commonFoodsAreReadable() {
        assertTrue(CommonFoods.all.size > 200)
        assertTrue(CommonFoods.all.all { it.carbs >= 0.0 && it.nameEn.isNotBlank() })
        val apple = CommonFoods.all.first { it.key == "apple" }
        assertEquals("Яблуко", apple.name("uk"))
        assertEquals("Apple", apple.name("de"))
        assertEquals(182.0, apple.defaultPortionGrams)
    }

    @Test fun searchFindsByLocalName() {
        assertEquals("apple", CommonFoods.search("ябл", "uk").first().key)
        assertEquals("apple", CommonFoods.search("appl", "en").first().key)
    }

    @Test fun nutrientsScaleWithWeight() {
        assertEquals(Nutrients(22.8, 0.6, 0.4), Nutrients.of(200.0, 11.4, 0.3, 0.2))
    }
}
