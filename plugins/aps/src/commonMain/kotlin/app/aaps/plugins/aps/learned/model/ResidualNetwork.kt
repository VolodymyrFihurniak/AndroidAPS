package app.aaps.plugins.aps.learned.model

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/** autoISF build, personal prediction: the weights of a [ResidualNetwork], for saving */
@Serializable
data class NetworkState(
    val inputs: Int,
    val hidden: Int,
    val outputs: Int,
    val w1: List<Double>,
    val b1: List<Double>,
    val w2: List<Double>,
    val b2: List<Double>
)

/**
 * autoISF build, personal prediction: a small neural network with one hidden layer (tanh), trained with Adam on a
 * weighted mean squared error. It learns what the linear part of the model leaves over. Plain Kotlin, so it runs in
 * shared code on every platform.
 *
 * The parameters are kept in one array: `w1` (hidden x inputs), `b1`, `w2` (outputs x hidden), `b2`.
 */
class ResidualNetwork private constructor(
    val inputs: Int,
    val hidden: Int,
    val outputs: Int,
    private val p: DoubleArray
) {

    constructor(inputs: Int = 16, hidden: Int = 32, outputs: Int = 4, seed: Int = 1) :
        this(inputs, hidden, outputs, initialParameters(inputs, hidden, outputs, seed))

    private val w1 = 0
    private val b1 = w1 + hidden * inputs
    private val w2 = b1 + hidden
    private val b2 = w2 + outputs * hidden

    // Adam state
    private val m = DoubleArray(p.size)
    private val v = DoubleArray(p.size)
    private var t = 0

    fun predict(x: FloatArray): FloatArray {
        val h = DoubleArray(hidden)
        forwardHidden(x, h)
        return FloatArray(outputs) { o -> output(h, o).toFloat() }
    }

    /** Weighted mean squared error over all outputs */
    fun loss(x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray): Double {
        val h = DoubleArray(hidden)
        var sum = 0.0
        var weights = 0.0
        for (s in x.indices) {
            forwardHidden(x[s], h)
            for (o in 0 until outputs) {
                val e = output(h, o) - y[s][o]
                sum += w[s] * e * e
            }
            weights += w[s]
        }
        return if (weights > 0.0) sum / weights else 0.0
    }

    /** The gradient of [loss] over all parameters, in the order of [parameters] */
    fun gradient(x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray): DoubleArray {
        val g = DoubleArray(p.size)
        val weights = w.sum().toDouble()
        if (weights > 0.0) accumulate(x.indices, x, y, w, g, 1.0 / weights)
        return g
    }

    /** One pass over the data in shuffled mini-batches. [epoch] makes the shuffle repeatable. */
    fun trainEpoch(x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray, batch: Int = 256, lr: Float = 1e-3f, epoch: Int = 0) {
        val order = x.indices.shuffled(Random(epoch * 7919 + 17))
        val g = DoubleArray(p.size)
        var start = 0
        while (start < order.size) {
            val part = order.subList(start, minOf(order.size, start + batch))
            val weights = part.sumOf { w[it].toDouble() }
            if (weights > 0.0) {
                g.fill(0.0)
                accumulate(part, x, y, w, g, 1.0 / weights)
                adamStep(g, lr.toDouble())
            }
            start += batch
        }
    }

    fun parameters(): DoubleArray = p.copyOf()

    fun setParameters(values: DoubleArray) {
        values.copyInto(p)
    }

    fun toState() = NetworkState(
        inputs, hidden, outputs,
        p.slice(w1 until b1), p.slice(b1 until w2), p.slice(w2 until b2), p.slice(b2 until p.size)
    )

    private fun forwardHidden(x: FloatArray, h: DoubleArray) {
        for (j in 0 until hidden) {
            var z = p[b1 + j]
            val row = w1 + j * inputs
            for (i in 0 until inputs) z += p[row + i] * x[i]
            h[j] = tanh(z)
        }
    }

    private fun output(h: DoubleArray, o: Int): Double {
        var z = p[b2 + o]
        val row = w2 + o * hidden
        for (j in 0 until hidden) z += p[row + j] * h[j]
        return z
    }

    /** Adds the gradient of the samples in [samples] to [g], each scaled by `w * scale` */
    private fun accumulate(samples: Iterable<Int>, x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray, g: DoubleArray, scale: Double) {
        val h = DoubleArray(hidden)
        val dh = DoubleArray(hidden)
        for (s in samples) {
            val xs = x[s]
            forwardHidden(xs, h)
            dh.fill(0.0)
            for (o in 0 until outputs) {
                val d = 2.0 * (output(h, o) - y[s][o]) * w[s] * scale
                if (d == 0.0) continue
                g[b2 + o] += d
                val row = w2 + o * hidden
                for (j in 0 until hidden) {
                    g[row + j] += d * h[j]
                    dh[j] += d * p[row + j]
                }
            }
            for (j in 0 until hidden) {
                val dz = dh[j] * (1.0 - h[j] * h[j])
                if (dz == 0.0) continue
                g[b1 + j] += dz
                val row = w1 + j * inputs
                for (i in 0 until inputs) g[row + i] += dz * xs[i]
            }
        }
    }

    private fun adamStep(g: DoubleArray, lr: Double) {
        t++
        val c1 = 1.0 - BETA1.pow(t)
        val c2 = 1.0 - BETA2.pow(t)
        for (k in p.indices) {
            m[k] = BETA1 * m[k] + (1 - BETA1) * g[k]
            v[k] = BETA2 * v[k] + (1 - BETA2) * g[k] * g[k]
            p[k] -= lr * (m[k] / c1) / (sqrt(v[k] / c2) + ADAM_EPSILON)
        }
    }

    companion object {

        private const val BETA1 = 0.9
        private const val BETA2 = 0.999
        private const val ADAM_EPSILON = 1e-8

        fun fromState(state: NetworkState): ResidualNetwork =
            ResidualNetwork(state.inputs, state.hidden, state.outputs, (state.w1 + state.b1 + state.w2 + state.b2).toDoubleArray())

        /** Xavier start for the weights, 0 for the biases. The output weights start small, so a new network adds little. */
        private fun initialParameters(inputs: Int, hidden: Int, outputs: Int, seed: Int): DoubleArray {
            val random = Random(seed)
            val p = DoubleArray(hidden * inputs + hidden + outputs * hidden + outputs)
            val a1 = sqrt(6.0 / (inputs + hidden))
            for (k in 0 until hidden * inputs) p[k] = random.nextDouble(-a1, a1)
            val start2 = hidden * inputs + hidden
            val a2 = 0.1 * sqrt(6.0 / (hidden + outputs))
            for (k in start2 until start2 + outputs * hidden) p[k] = random.nextDouble(-a2, a2)
            return p
        }
    }
}
