package com.pos.parser

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonParserTest {
    private val parser = JsonParser()

    private fun parse(content: String) = parser.parse("test.json", ByteArrayInputStream(content.toByteArray()))

    @Test
    fun `parses an array of objects with union of keys`() {
        val dataset = parse("""[{"a":1,"b":"x"},{"a":2}]""")
        assertEquals(setOf("a", "b"), dataset.columns.map { it.name }.toSet())
        assertEquals(2, dataset.rows.size)
    }

    @Test
    fun `flattens one level of nested objects with dot notation`() {
        val dataset = parse("""[{"id":1,"address":{"city":"London","zip":"E1"}}]""")
        assertTrue(dataset.columns.any { it.name == "address.city" })
        assertTrue(dataset.columns.any { it.name == "address.zip" })
        assertTrue(dataset.warnings.any { it.contains("flattened", ignoreCase = true) })
    }

    @Test
    fun `stringifies nested arrays instead of exploding rows`() {
        val dataset = parse("""[{"id":1,"tags":["a","b"]}]""")
        assertEquals(1, dataset.rows.size)
        val tagsIndex = dataset.columns.first { it.name == "tags" }.index
        assertEquals("[\"a\",\"b\"]", dataset.rows[0][tagsIndex].raw)
    }

    @Test
    fun `wraps a single top-level object as a one-row dataset`() {
        val dataset = parse("""{"a":1,"b":2}""")
        assertEquals(1, dataset.rows.size)
        assertTrue(dataset.warnings.isNotEmpty())
    }
}
