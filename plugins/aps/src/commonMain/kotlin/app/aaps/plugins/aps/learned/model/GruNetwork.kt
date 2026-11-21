package app.aaps.plugins.aps.learned.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/** autoISF build, personal prediction v2: one training example of the GRU */
class GruSample(
    /** [GruNetwork.steps] rows of [GruNetwork.inputs] values, oldest first */
    val sequence: Array<FloatArray>,
    /** [GruNetwork.context] values that describe the moment as a whole */
    val context: FloatArray,
    /** [GruNetwork.outputs] values to learn */
    val target: FloatArray,
    val weight: Float
)

/** autoISF build, personal prediction v2: the sizes and parameters of a [GruNetwork], for saving */
@Serializable
data class GruState(
    val steps: Int,
    val inputs: Int,
    val context: Int,
    val units: Int,
    val dense: Int,
    val outputs: Int,
    val parameters: List<Double>
)

/**
 * autoISF build, personal prediction v2: a small recurrent network (one GRU layer) for the expected curve. It reads
 * a sequence of [steps] rows, joins its last state with [context] values, and gives [outputs] values through one
 * dense tanh layer. Trained with Adam on a weighted mean squared error, with backpropagation through all steps.
 * Plain Kotlin, so it runs in shared code on every platform.
 *
 * Cell, with `s` the sigmoid: `z = s(Wz x + Uz h + bz)`, `r = s(Wr x + Ur h + br)`, `n = tanh(Wn x + Un (r*h) + bn)`,
 * `h' = (1 - z) * n + z * h`, starting from h = 0.
 *
 * Samples come from a provider and are built when their batch is, so a training set of 100 000 sequences never sits
 * in memory at once. A batch is split over [trainEpoch]'s threads; the gradients are added and one Adam step follows.
 */
class GruNetwork private constructor(
    val steps: Int,
    val inputs: Int,
    val context: Int,
    val units: Int,
    val dense: Int,
    val outputs: Int,
    private val p: DoubleArray
) {

    constructor(steps: Int = 36, inputs: Int = 11, context: Int, units: Int = 32, dense: Int = 32, outputs: Int = 24, seed: Int = 1) :
        this(steps, inputs, context, units, dense, outputs, initialParameters(inputs, context, units, dense, outputs, seed))

    // Offsets of the parameter blocks in p
    private val gate = units * inputs + units * units + units
    private val wz = 0
    private val uz = wz + units * inputs
    private val bz = uz + units * units
    private val wr = gate
    private val ur = wr + units * inputs
    private val br = ur + units * units
    private val wn = 2 * gate
    private val un = wn + units * inputs
    private val bn = un + units * units
    private val wd = 3 * gate
    private val bd = wd + dense * (units + context)
    private val wo = bd + dense
    private val bo = wo + outputs * dense

    // Adam state
    private val m = DoubleArray(p.size)
    private val v = DoubleArray(p.size)
    private var t = 0

    /** The values of one step, kept for the backward pass */
    private inner class Trace {

        val hPrev = Array(steps) { DoubleArray(units) }
        val z = Array(steps) { DoubleArray(units) }
        val r = Array(steps) { DoubleArray(units) }
        val n = Array(steps) { DoubleArray(units) }
        val rh = Array(steps) { DoubleArray(units) }
        val h = DoubleArray(units)
        val joined = DoubleArray(units + context)
        val d = DoubleArray(dense)
        val y = DoubleArray(outputs)
    }

    fun predict(sequence: Array<FloatArray>, context: FloatArray): FloatArray {
        val trace = Trace()
        forward(sequence, context, trace)
        return FloatArray(outputs) { trace.y[it].toFloat() }
    }

    /** Weighted mean over the samples of the squared error summed over the outputs */
    fun loss(count: Int, sample: (Int) -> GruSample): Double {
        val trace = Trace()
        var sum = 0.0
        var weights = 0.0
        for (s in 0 until count) {
            val one = sample(s)
            forward(one.sequence, one.context, trace)
            for (o in 0 until outputs) {
                val e = trace.y[o] - one.target[o]
                sum += one.weight * e * e
            }
            weights += one.weight
        }
        return if (weights > 0.0) sum / weights else 0.0
    }

    /** The gradient of [loss], in the order of [parameters] */
    fun gradient(count: Int, sample: (Int) -> GruSample): DoubleArray {
        val g = DoubleArray(p.size)
        val all = (0 until count).map(sample)
        val weights = all.sumOf { it.weight.toDouble() }
        if (weights > 0.0) all.forEach { accumulate(it, g, 1.0 / weights, Trace()) }
        return g
    }

    /** One pass over the samples in shuffled batches. [epoch] makes the shuffle repeatable. */
    suspend fun trainEpoch(count: Int, sample: (Int) -> GruSample, batch: Int = 64, lr: Double = 1e-3, epoch: Int, threads: Int) {
        val order = (0 until count).shuffled(Random(epoch * 7919 + 17))
        val parts = threads.coerceAtLeast(1)
        var start = 0
        while (start < order.size) {
            val part = order.subList(start, minOf(order.size, start + batch)).map(sample)
            val weights = part.sumOf { it.weight.toDouble() }
            if (weights > 0.0) {
                val chunks = part.chunked((part.size + parts - 1) / parts)
                val g = coroutineScope {
                    chunks.map { chunk ->
                        async(Dispatchers.Default) {
                            val local = DoubleArray(p.size)
                            val trace = Trace()
                            chunk.forEach { accumulate(it, local, 1.0 / weights, trace) }
                            local
                        }
                    }.awaitAll()
                }.reduce { a, b -> a.also { for (k in it.indices) it[k] += b[k] } }
                adamStep(g, lr)
            }
            start += batch
        }
    }

    fun parameters(): DoubleArray = p.copyOf()

    fun setParameters(values: DoubleArray) {
        values.copyInto(p)
    }

    fun toState() = GruState(steps, inputs, context, units, dense, outputs, p.toList())

    private fun forward(sequence: Array<FloatArray>, context: FloatArray, trace: Trace) {
        val h = trace.h
        h.fill(0.0)
        for (s in 0 until steps) {
            val x = sequence[s]
            h.copyInto(trace.hPrev[s])
            val z = trace.z[s]
            val r = trace.r[s]
            for (j in 0 until units) {
                z[j] = sigmoid(affine(wz, uz, bz, j, x, h))
                r[j] = sigmoid(affine(wr, ur, br, j, x, h))
            }
            val rh = trace.rh[s]
            for (j in 0 until units) rh[j] = r[j] * h[j]
            val n = trace.n[s]
            for (j in 0 until units) n[j] = tanh(affine(wn, un, bn, j, x, rh))
            for (j in 0 until units) h[j] = (1 - z[j]) * n[j] + z[j] * h[j]
        }
        h.copyInto(trace.joined)
        for (c in 0 until context.size) trace.joined[units + c] = context[c].toDouble()
        val width = units + this.context
        for (k in 0 until dense) {
            var a = p[bd + k]
            val row = wd + k * width
            for (c in 0 until width) a += p[row + c] * trace.joined[c]
            trace.d[k] = tanh(a)
        }
        for (o in 0 until outputs) {
            var a = p[bo + o]
            val row = wo + o * dense
            for (k in 0 until dense) a += p[row + k] * trace.d[k]
            trace.y[o] = a
        }
    }

    /** `W x + U h + b` for unit [j] of the gate whose blocks start at [w], [u], [b] */
    private fun affine(w: Int, u: Int, b: Int, j: Int, x: FloatArray, h: DoubleArray): Double {
        var a = p[b + j]
        val wRow = w + j * inputs
        for (i in 0 until inputs) a += p[wRow + i] * x[i]
        val uRow = u + j * units
        for (i in 0 until units) a += p[uRow + i] * h[i]
        return a
    }

    /** Adds the gradient of one sample, scaled by `weight * scale`, to [g] */
    private fun accumulate(one: GruSample, g: DoubleArray, scale: Double, trace: Trace) {
        forward(one.sequence, one.context, trace)
        val width = units + context
        val dd = DoubleArray(dense)
        for (o in 0 until outputs) {
            val dy = 2.0 * (trace.y[o] - one.target[o]) * one.weight * scale
            if (dy == 0.0) continue
            g[bo + o] += dy
            val row = wo + o * dense
            for (k in 0 until dense) {
                g[row + k] += dy * trace.d[k]
                dd[k] += dy * p[row + k]
            }
        }
        var dh = DoubleArray(units)
        for (k in 0 until dense) {
            val da = dd[k] * (1 - trace.d[k] * trace.d[k])
            if (da == 0.0) continue
            g[bd + k] += da
            val row = wd + k * width
            for (c in 0 until width) g[row + c] += da * trace.joined[c]
            for (j in 0 until units) dh[j] += da * p[row + j]
        }
        val dz = DoubleArray(units)
        val dan = DoubleArray(units)
        val drh = DoubleArray(units)
        val dar = DoubleArray(units)
        val daz = DoubleArray(units)
        for (s in steps - 1 downTo 0) {
            val x = one.sequence[s]
            val hPrev = trace.hPrev[s]
            val z = trace.z[s]
            val r = trace.r[s]
            val n = trace.n[s]
            val rh = trace.rh[s]
            val dhPrev = DoubleArray(units)
            for (j in 0 until units) {
                dz[j] = dh[j] * (hPrev[j] - n[j])
                dan[j] = dh[j] * (1 - z[j]) * (1 - n[j] * n[j])
                dhPrev[j] = dh[j] * z[j]
            }
            // Candidate n: Wn x + Un (r*h) + bn
            drh.fill(0.0)
            for (j in 0 until units) {
                val a = dan[j]
                if (a == 0.0) continue
                g[bn + j] += a
                val wRow = wn + j * inputs
                for (i in 0 until inputs) g[wRow + i] += a * x[i]
                val uRow = un + j * units
                for (i in 0 until units) {
                    g[uRow + i] += a * rh[i]
                    drh[i] += a * p[uRow + i]
                }
            }
            for (i in 0 until units) {
                dar[i] = drh[i] * hPrev[i] * r[i] * (1 - r[i])
                dhPrev[i] += drh[i] * r[i]
                daz[i] = dz[i] * z[i] * (1 - z[i])
            }
            gateBackward(wr, ur, br, dar, x, hPrev, g, dhPrev)
            gateBackward(wz, uz, bz, daz, x, hPrev, g, dhPrev)
            dh = dhPrev
        }
    }

    /** The gradient of a gate `W x + U h + b` with pre-activation gradient [da]; adds `U^T da` to [dhPrev] */
    private fun gateBackward(w: Int, u: Int, b: Int, da: DoubleArray, x: FloatArray, hPrev: DoubleArray, g: DoubleArray, dhPrev: DoubleArray) {
        for (j in 0 until units) {
            val a = da[j]
            if (a == 0.0) continue
            g[b + j] += a
            val wRow = w + j * inputs
            for (i in 0 until inputs) g[wRow + i] += a * x[i]
            val uRow = u + j * units
            for (i in 0 until units) {
                g[uRow + i] += a * hPrev[i]
                dhPrev[i] += a * p[uRow + i]
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

        fun fromState(state: GruState): GruNetwork =
            GruNetwork(state.steps, state.inputs, state.context, state.units, state.dense, state.outputs, state.parameters.toDoubleArray())

        private fun sigmoid(a: Double) = 1.0 / (1.0 + exp(-a))

        /** Xavier start for the weights, 0 for the biases; the output layer starts small so a new network adds little */
        private fun initialParameters(inputs: Int, context: Int, units: Int, dense: Int, outputs: Int, seed: Int): DoubleArray {
            val random = Random(seed)
            val gate = units * inputs + units * units + units
            val size = 3 * gate + dense * (units + context) + dense + outputs * dense + outputs
            val p = DoubleArray(size)
            fun fill(from: Int, count: Int, fanIn: Int, fanOut: Int, factor: Double = 1.0) {
                val a = factor * sqrt(6.0 / (fanIn + fanOut))
                for (k in from until from + count) p[k] = random.nextDouble(-a, a)
            }
            for (gateIndex in 0 until 3) {
                val base = gateIndex * gate
                fill(base, units * inputs, inputs, units)
                fill(base + units * inputs, units * units, units, units)
            }
            val wd = 3 * gate
            fill(wd, dense * (units + context), units + context, dense)
            val wo = wd + dense * (units + context) + dense
            fill(wo, outputs * dense, dense, outputs, factor = 0.1)
            return p
        }
    }
}
