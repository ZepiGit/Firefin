package zepigit.firefin.app.util

object Ticks {
    const val TICKS_PER_MS: Long = 10_000L

    fun toMs(ticks: Long): Long = ticks / TICKS_PER_MS
    fun fromMs(ms: Long): Long = ms * TICKS_PER_MS

    fun format(ticks: Long): String {
        val totalSeconds = toMs(ticks) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
