package app.aaps.plugins.aps.learned.model

import kotlin.math.abs

/**
 * autoISF build, personal prediction: collects `X^T W X` and `X^T W y` row by row, so a fit over millions of rows
 * needs only an n x n matrix.
 */
class RidgeAccumulator(val n: Int) {

    internal val xtx = Array(n) { DoubleArray(n) }
    internal val xty = DoubleArray(n)

    /** Rows added so far */
    var count: Long = 0L
        private set

    fun add(x: DoubleArray, y: Double, weight: Double) {
        for (a in 0 until n) {
            val wa = weight * x[a]
            if (wa == 0.0) continue
            xty[a] += wa * y
            val row = xtx[a]
            for (b in 0 until n) row[b] += wa * x[b]
        }
        count++
    }
}

/**
 * Ridge regression that pulls towards a prior instead of towards 0. Each parameter `k` is pulled with
 * `lambda * (X^T W X)[k][k] + EPSILON`, so the pull is the same for features of any size, and a feature that never
 * appears in the data keeps its prior.
 */
object RidgeFit {

    private const val EPSILON = 1e-6

    /** How often the fit is repeated after a non-negative parameter came out below 0 */
    private const val MAX_ROUNDS = 5

    /**
     * The parameters for [acc]. Parameters in [nonNegative] that come out below 0 are set to 0 and the others are
     * fitted again, at most [MAX_ROUNDS] times.
     */
    fun solve(acc: RidgeAccumulator, prior: DoubleArray, nonNegative: Set<Int>, lambda: Double = 1e-3): DoubleArray {
        val n = acc.n
        val pull = DoubleArray(n) { lambda * acc.xtx[it][it] + EPSILON }
        val zero = mutableSetOf<Int>()
        var theta = DoubleArray(n)
        repeat(MAX_ROUNDS) {
            val free = (0 until n).filter { it !in zero }
            val a = Array(free.size) { r -> DoubleArray(free.size) { c -> acc.xtx[free[r]][free[c]] + if (r == c) pull[free[r]] else 0.0 } }
            val b = DoubleArray(free.size) { r -> acc.xty[free[r]] + pull[free[r]] * prior[free[r]] }
            val solved = solveLinear(a, b)
            theta = DoubleArray(n)
            free.forEachIndexed { r, k -> theta[k] = solved[r] }
            val negative = free.filter { it in nonNegative && theta[it] < 0.0 }
            if (negative.isEmpty()) return theta
            zero += negative
        }
        zero.forEach { theta[it] = 0.0 }
        return theta
    }

    /** Gaussian elimination with partial pivoting. The matrix is positive definite here, so it does not fail. */
    internal fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { a[it].copyOf() }
        val v = b.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (abs(m[row][col]) > abs(m[pivot][col])) pivot = row
            if (pivot != col) {
                val tmp = m[pivot]; m[pivot] = m[col]; m[col] = tmp
                val t = v[pivot]; v[pivot] = v[col]; v[col] = t
            }
            val p = m[col][col]
            if (p == 0.0) continue
            for (row in col + 1 until n) {
                val f = m[row][col] / p
                if (f == 0.0) continue
                for (c in col until n) m[row][c] -= f * m[col][c]
                v[row] -= f * v[col]
            }
        }
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var s = v[row]
            for (c in row + 1 until n) s -= m[row][c] * x[c]
            x[row] = if (m[row][row] == 0.0) 0.0 else s / m[row][row]
        }
        return x
    }
}
