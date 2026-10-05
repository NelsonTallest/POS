package com.pos.parser

import com.pos.model.CellValue
import com.pos.model.DatasetLimits
import com.pos.model.ParsedColumn
import com.pos.model.ParsedDataset
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser as ApacheCsvParser
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class CsvParser : DatasetParser {
    override fun supports(fileName: String): Boolean = fileName.lowercase().endsWith(".csv")

    override fun parse(fileName: String, input: InputStream): ParsedDataset {
        val reader = InputStreamReader(input, StandardCharsets.UTF_8)
        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(false)
            .setTrim(false)
            .build()

        val parser = ApacheCsvParser.parse(reader, format)

        val headerNames = parser.headerNames.mapIndexed { i, name ->
            stripBomAndBlank(name).ifBlank { "column_${i + 1}" }
        }
        if (headerNames.isEmpty()) throw DatasetParseException("CSV file has no header row")
        if (headerNames.size > DatasetLimits.MAX_COLUMNS) {
            throw DatasetParseException("Too many columns (${headerNames.size}), max is ${DatasetLimits.MAX_COLUMNS}")
        }

        val columns = headerNames.mapIndexed { i, name -> ParsedColumn(name, i) }
        val rows = mutableListOf<List<CellValue>>()
        val warnings = mutableListOf<String>()
        var skippedRows = 0
        var truncated = false

        for (record in parser) {
            if (rows.size >= DatasetLimits.MAX_ROWS) {
                truncated = true
                break
            }
            try {
                val row = ArrayList<CellValue>(columns.size)
                for (i in columns.indices) {
                    val value = if (i < record.size()) record.get(i) else null
                    row.add(if (value.isNullOrEmpty()) CellValue.ofNull() else CellValue.ofString(value))
                }
                rows.add(row)
            } catch (e: Exception) {
                skippedRows++
            }
        }

        if (truncated) warnings.add("Row limit of ${DatasetLimits.MAX_ROWS} reached; remaining rows were ignored")
        if (skippedRows > 0) warnings.add("$skippedRows malformed row(s) were skipped")
        if (rows.isEmpty()) throw DatasetParseException("CSV file has no data rows")

        return ParsedDataset(fileName, columns, rows, warnings)
    }

    private fun stripBomAndBlank(s: String): String {
        val noBom = if (s.isNotEmpty() && s[0].code == 0xFEFF) s.substring(1) else s
        return noBom.trim()
    }
}
