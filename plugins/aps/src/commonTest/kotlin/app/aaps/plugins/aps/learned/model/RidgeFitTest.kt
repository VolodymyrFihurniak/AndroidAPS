package app.aaps.plugins.aps.learned.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RidgeFitTest {

    @Test
    fun `ridge finds known parameters`() {
        val random = Random(1)
        val acc = RidgeAccumulator(3)
        repeat(5000) {
            val x = DoubleArray(3) { random.nextDouble(-1.0, 1.0) }
            acc.add(x, 2 * x[0] - x[1] + 0.5 * x[2] + random.nextDouble(-0.01, 0.01), weight = 1.0)
        }
        val theta = RidgeFit.solve(acc, prior = DoubleArray(3), nonNegative = emptySet())
        assertEquals(2.0, theta[0], 0.02)
        assertEquals(-1.0, theta[1], 0.02)
        assertEquals(0.5, theta[2], 0.02)
        assertEquals(5000L, acc.count)
    }

    @Test
    fun `ridge stays at the prior without data`() {
        val random = Random(2)
        val acc = RidgeAccumulator(3)
        repeat(3) {
            val x = DoubleArray(3) { random.nextDouble(-1.0, 1.0) }
            acc.add(x, 5 * x[1], weight = 1.0)
        }
        val theta = RidgeFit.solve(acc, prior = doubleArrayOf(1.0, 0.0, 0.0), nonNegative = emptySet(), lambda = 1e3)
        assertEquals(1.0, theta[0], 0.05)
        assertEquals(0.0, theta[1], 0.05)
        assertEquals(0.0, theta[2], 0.05)
    }

    @Test
    fun `feature never seen keeps its prior`() {
        val acc = RidgeAccumulator(2)
        repeat(100) { acc.add(doubleArrayOf(it.toDouble(), 0.0), 3.0 * it, weight = 1.0) }
        val theta = RidgeFit.solve(acc, prior = doubleArrayOf(0.0, 0.7), nonNegative = emptySet())
        assertEquals(3.0, theta[0], 0.01)
        assertEquals(0.7, theta[1], 1e-6)
    }

    @Test
    fun `negative weight of a non-negative feature is set to zero`() {
        val random = Random(3)
        val acc = RidgeAccumulator(2)
        repeat(2000) {
            val x = DoubleArray(2) { random.nextDouble(-1.0, 1.0) }
            acc.add(x, x[0] - 0.5 * x[1], weight = 1.0)
        }
        val theta = RidgeFit.solve(acc, prior = DoubleArray(2), nonNegative = setOf(1))
        assertEquals(0.0, theta[1])
        assertEquals(1.0, theta[0], 0.05)
    }
}
