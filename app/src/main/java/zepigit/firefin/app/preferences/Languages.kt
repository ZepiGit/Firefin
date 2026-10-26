package zepigit.firefin.app.preferences

import java.util.Locale
import java.util.MissingResourceException

object Languages {
    val CODES = listOf("", "de", "en", "fr", "es", "it", "nl", "pt", "ja")
    private val aliases = listOf(setOf("de", "deu", "ger"), setOf("en", "eng"),
        setOf("fr", "fra", "fre"), setOf("es", "spa"), setOf("it", "ita"),
        setOf("nl", "nld", "dut"), setOf("pt", "por"), setOf("ja", "jpn"))

    fun canonical(value: String): String {
        val code = value.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        aliases.firstOrNull { code in it }?.let { return it.first() }
        return try { Locale(code).isO3Language.ifBlank { code } }
        catch (_: MissingResourceException) { code }
    }
}
