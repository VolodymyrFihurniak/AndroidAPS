package app.aaps.plugins.aps.learned.train

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpectedCurveRolesTest {

    private val hour = 3_600_000L
    private val validationFrom = 100 * hour
    private val testFrom = 200 * hour

    @Test
    fun `a sample whose next 2 hours reach the next part is at the edge`() {
        assertEquals(ExpectedCurveTrainer.LEARN, ExpectedCurveTrainer.roleOf(validationFrom - 3 * hour, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.EDGE, ExpectedCurveTrainer.roleOf(validationFrom - 2 * hour, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.EDGE, ExpectedCurveTrainer.roleOf(validationFrom - 1, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.VALIDATE, ExpectedCurveTrainer.roleOf(validationFrom, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.VALIDATE, ExpectedCurveTrainer.roleOf(testFrom - 2 * hour - 1, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.EDGE, ExpectedCurveTrainer.roleOf(testFrom - 2 * hour, validationFrom, testFrom))
        assertEquals(ExpectedCurveTrainer.TEST, ExpectedCurveTrainer.roleOf(testFrom, validationFrom, testFrom))
    }
}
