package com.pos.parser

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvParserTest {
    private val parser = CsvParser()

    private fun parse(content: String) = parser.parse("test.csv", ByteArrayInputStream(content.toByteArray()))

    @Test
    fun `parses header and rows`() {
        val dataset = parse("name,age\nAlice,30\nBob,25\n")
        assertEquals(listOf("name", "age"), dataset.columns.map { it.name })
        assertEquals(2, dataset.rows.size)
        assertEquals("Alice", dataset.rows[0][0].raw)
        assertEquals("30", dataset.rows[0][1].raw)
    }

    @Test
    fun `handles quoted fields containing commas and newlines`() {
        val dataset = parse("name,note\n\"Doe, John\",\"line1\nline2\"\n")
        assertEquals("Doe, John", dataset.rows[0][0].raw)
        assertEquals("line1\nline2", dataset.rows[0][1].raw)
    }

    @Test
    fun `empty cells become null values`() {
        val dataset = parse("a,b,c\n1,,3\n")
        assertTrue(dataset.rows[0][1].isNull)
    }

    @Test
    fun `rejects a file with no data rows`() {
        assertFailsWith<DatasetParseException> { parse("a,b,c\n") }
    }
}
