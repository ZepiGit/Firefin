package zepigit.firefin.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationParityTest {
    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    private fun load(path: String): Map<String, List<String>> {
        val file = File(path)
        assertTrue("Missing ${file.absolutePath}", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val entries = mutableMapOf<String, List<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            val values = when (node.tagName) {
                "string" -> listOf(node.textContent)
                "string-array", "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                }
                else -> continue
            }
            entries["${node.tagName}:$name"] = values
        }
        return entries
    }

    private fun placeholders(values: List<String>) = values.map { value ->
        placeholder.findAll(value).map { it.value }.sorted().toList()
    }

    @Test fun `english default and german translation define the same resources`() {
        val english = load("src/main/res/values/strings.xml")
        val german = load("src/main/res/values-de/strings.xml")
        assertEquals("Resources missing in German", emptySet<String>(), english.keys - german.keys)
        assertEquals("Resources missing in the English default", emptySet<String>(), german.keys - english.keys)
    }

    @Test fun `unquoted values have no edge whitespace that the resource compiler would drop`() {
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-de/strings.xml")) {
            for ((key, values) in load(path)) {
                values.forEach { value ->
                    val quoted = value.startsWith("\"") && value.endsWith("\"")
                    assertTrue("$path $key has leading or trailing whitespace: '$value'", quoted || value == value.trim())
                }
            }
        }
    }

    @Test fun `translations keep the same format placeholders and item counts`() {
        val english = load("src/main/res/values/strings.xml")
        val german = load("src/main/res/values-de/strings.xml")
        for ((key, values) in english) {
            val translated = german.getValue(key)
            assertEquals("$key item count", values.size, translated.size)
            assertEquals("$key placeholders", placeholders(values), placeholders(translated))
        }
    }
    /** The option arrays are indexed by code tables; a missing item would throw at the index. */
    @Test fun `option arrays match the code tables that index them`() {
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-de/strings.xml")) {
            val entries = load(path)
            fun size(key: String) = entries.getValue("string-array:$key").size
            for (key in listOf("audio_language_options", "subtitle_language_options"))
                assertEquals(key, zepigit.firefin.app.preferences.Languages.CODES.size, size(key))
            assertEquals("home_order_options", zepigit.firefin.app.ui.HomeSections.presets.size, size("home_order_options"))
            assertEquals("subtitle_size_options", 3, size("subtitle_size_options")) // SettingsActivity clamps subtitleSize to 0..2
            assertEquals("library_filter_options", 4, size("library_filter_options")) // LibraryActivity.filters has four entries
        }
    }
}
