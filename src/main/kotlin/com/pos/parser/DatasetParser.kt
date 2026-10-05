package com.pos.parser

import com.pos.model.ParsedDataset
import java.io.InputStream

class UnsupportedFileTypeException(fileName: String) : Exception("Unsupported file type: $fileName")
class DatasetParseException(message: String) : Exception(message)

interface DatasetParser {
    fun supports(fileName: String): Boolean
    fun parse(fileName: String, input: InputStream): ParsedDataset
}

object ParserRegistry {
    private val parsers: List<DatasetParser> = listOf(CsvParser(), XlsxParser(), JsonParser())

    fun forFile(fileName: String): DatasetParser =
        parsers.firstOrNull { it.supports(fileName) } ?: throw UnsupportedFileTypeException(fileName)
}
