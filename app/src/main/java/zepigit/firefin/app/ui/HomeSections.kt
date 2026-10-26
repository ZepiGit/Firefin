package zepigit.firefin.app.ui

object HomeSections {
    val presets = listOf(
        listOf("resume", "nextUp", "favorites", "latest"),
        listOf("latest", "resume", "nextUp", "favorites"),
        listOf("favorites", "resume", "nextUp", "latest"),
        listOf("resume", "nextUp", "latest"),
    )

    fun order(keys: List<String>, rows: List<HomeRow>): List<HomeRow> =
        keys.distinct().flatMap { key -> rows.filter { it.kind == key } }
}
