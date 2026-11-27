package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.model.Range
import kotlin.random.Random

/**
 * autoISF build, personal prediction: how sure a difference of errors is. Moments of one day are not independent (a
 * meal makes a whole afternoon hard), so whole test days are drawn again with replacement, [ROUNDS] times, and the
 * middle 95 % of the differences is the range. A range that holds 0 means the test days cannot tell the two apart.
 */
object DayResampling {

    const val ROUNDS = 1000
    private const val SEED = 1

    /**
     * [differences] per day: the sum over its moments of (error of the curve - error of the other); [moments] per day:
     * how many moments. Null with fewer than 2 days that have moments.
     */
    fun range(differences: DoubleArray, moments: IntArray): Range? {
        val days = moments.indices.filter { moments[it] > 0 }
        if (days.size < 2) return null
        val total = days.sumOf { differences[it] } / days.sumOf { moments[it] }
        val random = Random(SEED)
        val drawn = DoubleArray(ROUNDS) {
            var sum = 0.0
            var count = 0
            repeat(days.size) {
                val day = days[random.nextInt(days.size)]
                sum += differences[day]
                count += moments[day]
            }
            sum / count
        }
        drawn.sort()
        return Range(mean = total, low = drawn[(ROUNDS * 0.025).toInt()], high = drawn[(ROUNDS * 0.975).toInt() - 1])
    }
}
