package app.aaps.plugins.aps.learned.model

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GruNetworkTest {

    private fun randomSamples(count: Int, steps: Int, inputs: Int, context: Int, outputs: Int, seed: Int): List<GruSample> {
        val random = Random(seed)
        return List(count) {
            GruSample(
                sequence = Array(steps) { FloatArray(inputs) { random.nextDouble(-1.0, 1.0).toFloat() } },
                context = FloatArray(context) { random.nextDouble(-1.0, 1.0).toFloat() },
                target = FloatArray(outputs) { random.nextDouble(-1.0, 1.0).toFloat() },
                weight = random.nextDouble(0.5, 2.0).toFloat()
            )
        }
    }

    @Test
    fun `gru gradient check`() {
        val net = GruNetwork(steps = 4, inputs = 3, context = 2, units = 3, dense = 3, outputs = 2, seed = 3)
        val samples = randomSamples(count = 3, steps = 4, inputs = 3, context = 2, outputs = 2, seed = 4)
        val analytic = net.gradient(samples.size) { samples[it] }
        val params = net.parameters()
        val eps = 1e-5
        for (p in params.indices) {
            net.setParameters(params.copyOf().also { it[p] += eps })
            val plus = net.loss(samples.size) { samples[it] }
            net.setParameters(params.copyOf().also { it[p] -= eps })
            val minus = net.loss(samples.size) { samples[it] }
            val numeric = (plus - minus) / (2 * eps)
            val error = abs(numeric - analytic[p]) / max(1e-7, abs(numeric) + abs(analytic[p]))
            assertTrue(error < 1e-3, "parameter $p: numeric $numeric, analytic ${analytic[p]}")
        }
        net.setParameters(params)
    }

    @Test
    fun `gru learns the mean of a sequence`() = runTest {
        val random = Random(7)
        val samples = List(400) {
            val values = FloatArray(6) { random.nextDouble(-1.0, 1.0).toFloat() }
            GruSample(Array(6) { floatArrayOf(values[it]) }, FloatArray(0), floatArrayOf(values.sum() / 6), 1f)
        }
        val net = GruNetwork(steps = 6, inputs = 1, context = 0, units = 8, dense = 8, outputs = 1, seed = 1)
        val before = net.loss(samples.size) { samples[it] }
        repeat(40) { net.trainEpoch(samples.size, { samples[it] }, batch = 32, lr = 0.01, epoch = it, threads = 1) }
        val after = net.loss(samples.size) { samples[it] }
        assertTrue(after < 0.2 * before, "loss before $before, after $after")
    }

    @Test
    fun `state round trip gives the same outputs`() {
        val net = GruNetwork(steps = 5, inputs = 2, context = 3, units = 4, dense = 4, outputs = 3, seed = 9)
        val copy = GruNetwork.fromState(net.toState())
        val sample = randomSamples(count = 1, steps = 5, inputs = 2, context = 3, outputs = 3, seed = 2).single()
        assertEquals(net.predict(sample.sequence, sample.context).toList(), copy.predict(sample.sequence, sample.context).toList())
    }

    @Test
    fun `parallel gradient equals single`() = runTest {
        val samples = randomSamples(count = 100, steps = 5, inputs = 3, context = 2, outputs = 2, seed = 5)
        val single = GruNetwork(steps = 5, inputs = 3, context = 2, units = 4, dense = 4, outputs = 2, seed = 1)
        val parallel = GruNetwork(steps = 5, inputs = 3, context = 2, units = 4, dense = 4, outputs = 2, seed = 1)
        single.trainEpoch(samples.size, { samples[it] }, batch = 32, epoch = 0, threads = 1)
        parallel.trainEpoch(samples.size, { samples[it] }, batch = 32, epoch = 0, threads = 3)
        val a = single.parameters()
        val b = parallel.parameters()
        for (k in a.indices) assertEquals(a[k], b[k], 1e-9, "parameter $k")
    }
}
