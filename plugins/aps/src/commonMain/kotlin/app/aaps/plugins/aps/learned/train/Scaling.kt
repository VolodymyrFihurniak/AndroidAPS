package app.aaps.plugins.aps.learned.train

import kotlin.math.sqrt

/** autoISF build, personal prediction: the network inputs are scaled to `(x - mean) / std`, from the learning set */
class Scaling(val mean: FloatArray, val std: FloatArray) {

    fun apply(x: FloatArray) {
        for (k in x.indices) x[k] = (x[k] - mean[k]) / std[k]
    }

    fun apply(rows: Array<FloatArray>) = rows.forEach { apply(it) }

    companion object {

        /** Mean and standard deviation per input; a constant input gets std 1 */
        fun of(rows: Array<FloatArray>): Scaling {
            val n = rows.firstOrNull()?.size ?: 0
            val mean = FloatArray(n)
            val std = FloatArray(n)
            for (k in 0 until n) {
                var sum = 0.0
                for (row in rows) sum += row[k]
                val m = if (rows.isEmpty()) 0.0 else sum / rows.size
                var squares = 0.0
                for (row in rows) squares += (row[k] - m) * (row[k] - m)
                val s = if (rows.isEmpty()) 0.0 else sqrt(squares / rows.size)
                mean[k] = m.toFloat()
                std[k] = if (s > 1e-6) s.toFloat() else 1f
            }
            return Scaling(mean, std)
        }
    }
}
