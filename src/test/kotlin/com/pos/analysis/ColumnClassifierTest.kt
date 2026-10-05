package com.pos.analysis

import com.pos.model.CellValue
import com.pos.model.ColumnType
import com.pos.model.ParsedColumn
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ColumnClassifierTest {

    private fun cells(vararg values: String?) = values.map { if (it == null) CellValue.ofNull() else CellValue.ofString(it) }

    @Test
    fun `classifies whole numbers as integer`() {
        val result = ColumnClassifier.classify(ParsedColumn("count", 0), cells("1", "2", "3", "4"))
        assertEquals(ColumnType.INTEGER, result.type)
    }

    @Test
    fun `classifies fractional numbers as decimal`() {
        val result = ColumnClassifier.classify(ParsedColumn("price", 0), cells("1.5", "2.25", "3.0"))
        assertEquals(ColumnType.DECIMAL, result.type)
    }

    @Test
    fun `classifies yes-no values as boolean`() {
        val result = ColumnClassifier.classify(ParsedColumn("active", 0), cells("yes", "no", "yes", "no"))
        assertEquals(ColumnType.BOOLEAN, result.type)
    }

    @Test
    fun `classifies iso dates as date`() {
        val result = ColumnClassifier.classify(ParsedColumn("created", 0), cells("2023-01-01", "2023-02-15", "2023-03-30"))
        assertEquals(ColumnType.DATE, result.type)
    }

    @Test
    fun `classifies id-named near-unique column as identifier`() {
        val result = ColumnClassifier.classify(ParsedColumn("user_id", 0), cells("1", "2", "3", "4", "5"))
        assertEquals(ColumnType.IDENTIFIER, result.type)
    }

    @Test
    fun `classifies low-cardinality repeated strings as categorical`() {
        val values = (1..20).map { if (it % 2 == 0) "North" else "South" }.toTypedArray()
        val result = ColumnClassifier.classify(ParsedColumn("region", 0), cells(*values))
        assertEquals(ColumnType.CATEGORICAL, result.type)
    }

    @Test
    fun `classifies highly distinct long strings as free text`() {
        val values = (1..20).map { "This is a unique comment number $it with enough length" }.toTypedArray()
        val result = ColumnClassifier.classify(ParsedColumn("comment", 0), cells(*values))
        assertEquals(ColumnType.FREE_TEXT, result.type)
    }

    @Test
    fun `classifies all-null column as empty`() {
        val result = ColumnClassifier.classify(ParsedColumn("blank", 0), cells(null, null, null))
        assertEquals(ColumnType.EMPTY, result.type)
    }

    @Test
    fun `classifies single repeated value as constant`() {
        val result = ColumnClassifier.classify(ParsedColumn("flag", 0), cells("X", "X", "X", "X"))
        assertEquals(ColumnType.CONSTANT, result.type)
    }
}
