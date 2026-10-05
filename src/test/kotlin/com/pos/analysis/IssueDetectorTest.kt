package com.pos.analysis

import com.pos.model.CellValue
import com.pos.model.ParsedColumn
import com.pos.model.ParsedDataset
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class IssueDetectorTest {

    private fun row(vararg values: String?) = values.map { if (it == null) CellValue.ofNull() else CellValue.ofString(it) }

    private fun analyze(columns: List<ParsedColumn>, rows: List<List<CellValue>>) =
        columns.map { col -> ColumnClassifier.classify(col, rows.map { it[col.index] }) }

    @Test
    fun `detects duplicate rows`() {
        val columns = listOf(ParsedColumn("name", 0), ParsedColumn("age", 1))
        val rows = listOf(row("Alice", "30"), row("Bob", "25"), row("Alice", "30"))
        val dataset = ParsedDataset("test.csv", columns, rows)

        val issues = IssueDetector.detect(dataset, analyze(columns, rows))
        assertTrue(issues.any { it.category == "duplicates" })
    }

    @Test
    fun `detects missing values per column`() {
        val columns = listOf(ParsedColumn("name", 0))
        val rows = listOf(row("Alice"), row(null), row("Bob"))
        val dataset = ParsedDataset("test.csv", columns, rows)

        val issues = IssueDetector.detect(dataset, analyze(columns, rows))
        assertTrue(issues.any { it.category == "missing_values" && it.columnName == "name" })
    }

    @Test
    fun `detects numeric outliers via IQR`() {
        val columns = listOf(ParsedColumn("value", 0))
        val rows = listOf("10", "12", "11", "13", "12", "11", "1000").map { row(it) }
        val dataset = ParsedDataset("test.csv", columns, rows)

        val issues = IssueDetector.detect(dataset, analyze(columns, rows))
        assertTrue(issues.any { it.category == "outliers" })
    }

    @Test
    fun `detects inconsistent casing in categorical values`() {
        val columns = listOf(ParsedColumn("country", 0))
        val rows = (1..10).map { row(if (it % 2 == 0) "USA" else "usa") }
        val dataset = ParsedDataset("test.csv", columns, rows)

        val issues = IssueDetector.detect(dataset, analyze(columns, rows))
        assertTrue(issues.any { it.category == "inconsistent_formatting" && it.message.contains("casing") })
    }
}
