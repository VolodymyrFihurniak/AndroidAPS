package app.aaps.plugins.aps.learned.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResidualNetworkTest {

    @Test
    fun `network gradient check`() {
        val net = ResidualNetwork(inputs = 3, hidden = 4, outputs = 2, seed = 3)
        val random = Random(4)
        val x = Array(3) { FloatArray(3) { random.nextDouble(-1.0, 1.0).toFloat() } }
        val y = Array(3) { FloatArray(2) { random.nextDouble(-1.0, 1.0).toFloat() } }
        val w = floatArrayOf(1f, 0.5f, 2f)
        val analytic = net.gradient(x, y, w)
        val params = net.parameters()
        val eps = 1e-4
        for (p in params.indices) {
            val plus = params.copyOf().also { it[p] += eps }
            val minus = params.copyOf().also { it[p] -= eps }
            net.setParameters(plus)
            val lossPlus = net.loss(x, y, w)
            net.setParameters(minus)
            val lossMinus = net.loss(x, y, w)
            val numeric = (lossPlus - lossMinus) / (2 * eps)
            val error = abs(numeric - analytic[p]) / max(1e-6, abs(numeric) + abs(analytic[p]))
            assertTrue(error < 1e-3, "parameter $p: numeric $numeric, analytic ${analytic[p]}")
        }
        net.setParameters(params)
    }

    @Test
    fun `network training lowers the loss`() {
        val random = Random(5)
        val x = Array(500) { FloatArray(2) { random.nextDouble(-2.0, 2.0).toFloat() } }
        val y = Array(500) { floatArrayOf(sin(x[it][0].toDouble()).toFloat()) }
        val w = FloatArray(500) { 1f }
        val net = ResidualNetwork(inputs = 2, hidden = 16, outputs = 1, seed = 1)
        val before = net.loss(x, y, w)
        repeat(30) { net.trainEpoch(x, y, w, batch = 32, lr = 0.01f, epoch = it) }
        assertTrue(net.loss(x, y, w) < 0.3 * before, "loss before $before, after ${net.loss(x, y, w)}")
    }

    @Test
    fun `state round trip gives the same outputs`() {
        val net = ResidualNetwork(inputs = 3, hidden = 5, outputs = 2, seed = 9)
        val copy = ResidualNetwork.fromState(net.toState())
        val x = floatArrayOf(0.3f, -1.2f, 0.7f)
        assertEquals(net.predict(x).toList(), copy.predict(x).toList())
    }
}
