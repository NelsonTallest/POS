package com.pos.parser

import com.pos.model.CellValue
import com.pos.model.DatasetLimits
import com.pos.model.ParsedColumn
import com.pos.model.ParsedDataset
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType as PoiCellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.time.format.DateTimeFormatter

class XlsxParser : DatasetParser {
    override fun supports(fileName: String): Boolean = fileName.lowercase().endsWith(".xlsx")

    override fun parse(fileName: String, input: InputStream): ParsedDataset {
        WorkbookFactory.create(input).use { workbook ->
            if (workbook.numberOfSheets == 0) throw DatasetParseException("Workbook has no sheets")
            val sheet = workbook.getSheetAt(0)
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            val warnings = mutableListOf<String>()

            if (workbook.numberOfSheets > 1) {
                val otherNames = (1 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
                warnings.add("Only the first sheet ('${sheet.sheetName}') was analyzed; ignored sheets: ${otherNames.joinToString(", ")}")
            }

            val headerRow = sheet.getRow(sheet.firstRowNum)
                ?: throw DatasetParseException("Sheet has no header row")
            val lastCol = headerRow.lastCellNum.toInt().coerceAtLeast(0)
            if (lastCol > DatasetLimits.MAX_COLUMNS) {
                throw DatasetParseException("Too many columns ($lastCol), max is ${DatasetLimits.MAX_COLUMNS}")
            }

            val columns = (0 until lastCol).map { i ->
                val cell = headerRow.getCell(i)
                val name = cell?.let { cellToValue(it, evaluator).raw }?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "column_${i + 1}"
                ParsedColumn(name, i)
            }
            if (columns.isEmpty()) throw DatasetParseException("Sheet has no columns")

            val rows = mutableListOf<List<CellValue>>()
            var truncated = false
            for (rowIndex in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                if (rows.size >= DatasetLimits.MAX_ROWS) {
                    truncated = true
                    break
                }
                val row = sheet.getRow(rowIndex)
                val rowValues = columns.map { col -> cellToValue(row?.getCell(col.index), evaluator) }
                if (rowValues.any { !it.isNull }) rows.add(rowValues)
            }
            if (truncated) warnings.add("Row limit of ${DatasetLimits.MAX_ROWS} reached; remaining rows were ignored")
            if (rows.isEmpty()) throw DatasetParseException("Sheet has no data rows")

            return ParsedDataset(fileName, columns, rows, warnings)
        }
    }

    private fun cellToValue(cell: Cell?, evaluator: FormulaEvaluator): CellValue {
        if (cell == null) return CellValue.ofNull()
        val effectiveType = if (cell.cellType == PoiCellType.FORMULA) {
            evaluator.evaluateFormulaCell(cell)
        } else {
            cell.cellType
        }
        return when (effectiveType) {
            PoiCellType.BLANK -> CellValue.ofNull()
            PoiCellType.STRING -> {
                val s = cell.stringCellValue
                if (s.isBlank()) CellValue.ofNull() else CellValue.ofString(s)
            }
            PoiCellType.BOOLEAN -> {
                val b = cell.booleanCellValue
                CellValue.ofBoolean(b.toString(), b)
            }
            PoiCellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val dateTime = cell.localDateTimeCellValue
                    CellValue.ofString(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                } else {
                    val d = cell.numericCellValue
                    if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) {
                        CellValue.ofLong(formatNumber(d), d.toLong())
                    } else {
                        CellValue.ofDouble(formatNumber(d), d)
                    }
                }
            }
            else -> CellValue.ofNull()
        }
    }

    private fun formatNumber(d: Double): String =
        if (d == Math.floor(d)) d.toLong().toString() else d.toString()
}
