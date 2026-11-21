package app.aaps.plugins.aps.learned.grid

/**
 * autoISF build, personal prediction: with a 1-minute sensor the 5-minute grid can start at any of 5 minutes. Training
 * uses all 5 ("phases"), so it sees 5 times more moments and matches a live grid that ends at the newest reading every
 * minute. With a 5-minute sensor the shifted grids would only repeat the same readings, so there is one phase.
 */
object Phases {

    private const val MINUTE_MS = 60_000L

    /** The most gap between readings, as a median, that still counts as a 1-minute sensor */
    private const val ONE_MINUTE_MEDIAN_GAP_MS = 2 * MINUTE_MS

    fun isOneMinuteSensor(bg: List<BgReading>): Boolean {
        if (bg.size < 2) return false
        val times = bg.map { it.timestamp }.sorted()
        val gaps = times.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return gaps.isNotEmpty() && gaps[gaps.size / 2] <= ONE_MINUTE_MEDIAN_GAP_MS
    }

    /** The start shifts of the grids to learn from: 0 to 4 minutes with a 1-minute sensor, else only 0 */
    fun offsets(bg: List<BgReading>): List<Long> =
        if (isOneMinuteSensor(bg)) (0 until (STEP_MS / MINUTE_MS).toInt()).map { it * MINUTE_MS } else listOf(0L)
}
