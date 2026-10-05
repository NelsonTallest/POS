package com.pos.model

enum class CellType { STRING, LONG, DOUBLE, BOOLEAN, NULL }

data class CellValue(
    val type: CellType,
    val raw: String?,
    val asLong: Long? = null,
    val asDouble: Double? = null,
    val asBoolean: Boolean? = null,
) {
    val isNull: Boolean get() = type == CellType.NULL

    companion object {
        fun ofNull() = CellValue(CellType.NULL, null)
        fun ofString(value: String) = CellValue(CellType.STRING, value)
        fun ofLong(raw: String?, value: Long) = CellValue(CellType.LONG, raw ?: value.toString(), asLong = value)
        fun ofDouble(raw: String?, value: Double) = CellValue(CellType.DOUBLE, raw ?: value.toString(), asDouble = value)
        fun ofBoolean(raw: String?, value: Boolean) = CellValue(CellType.BOOLEAN, raw ?: value.toString(), asBoolean = value)
    }
}

data class ParsedColumn(val name: String, val index: Int)

data class ParsedDataset(
    val sourceFileName: String,
    val columns: List<ParsedColumn>,
    val rows: List<List<CellValue>>,
    val warnings: List<String> = emptyList(),
)

object DatasetLimits {
    const val MAX_ROWS = 200_000
    const val MAX_COLUMNS = 500
    const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
}
