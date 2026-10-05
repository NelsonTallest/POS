package com.pos.parser

import com.pos.model.CellValue
import com.pos.model.DatasetLimits
import com.pos.model.ParsedColumn
import com.pos.model.ParsedDataset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.InputStream

class JsonParser : DatasetParser {
    override fun supports(fileName: String): Boolean = fileName.lowercase().endsWith(".json")

    override fun parse(fileName: String, input: InputStream): ParsedDataset {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        if (text.isBlank()) throw DatasetParseException("JSON file is empty")

        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw DatasetParseException("Invalid JSON: ${e.message}")
        }

        val warnings = mutableListOf<String>()
        val objects: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                warnings.add("Top-level JSON was a single object; treated as a one-row dataset")
                listOf(root)
            }
            else -> throw DatasetParseException("JSON must be an array of objects, or an object")
        }
        if (objects.isEmpty()) throw DatasetParseException("JSON contains no records")

        var truncated = false
        val limitedObjects = if (objects.size > DatasetLimits.MAX_ROWS) {
            truncated = true
            objects.take(DatasetLimits.MAX_ROWS)
        } else {
            objects
        }

        var anyFlattened = false
        val flatObjects = limitedObjects.map { obj ->
            val (flat, flattened) = flattenOneLevel(obj)
            if (flattened) anyFlattened = true
            flat
        }

        val columnNames = LinkedHashSet<String>()
        flatObjects.forEach { columnNames.addAll(it.keys) }
        if (columnNames.size > DatasetLimits.MAX_COLUMNS) {
            throw DatasetParseException("Too many columns (${columnNames.size}), max is ${DatasetLimits.MAX_COLUMNS}")
        }

        val columns = columnNames.mapIndexed { i, name -> ParsedColumn(name, i) }
        var anyStringified = false
        val rows = flatObjects.map { obj ->
            columns.map { col ->
                val (value, stringified) = jsonElementToCell(obj[col.name])
                if (stringified) anyStringified = true
                value
            }
        }

        if (truncated) warnings.add("Row limit of ${DatasetLimits.MAX_ROWS} reached; remaining records were ignored")
        if (anyFlattened) warnings.add("Nested object fields were flattened using dot notation (e.g. 'address.city')")
        if (anyStringified) warnings.add("Nested arrays/objects beyond one level were converted to text")

        return ParsedDataset(fileName, columns, rows, warnings)
    }

    /** Expands one level of nested JsonObject values into "parent.child" keys. */
    private fun flattenOneLevel(obj: JsonObject): Pair<Map<String, JsonElement>, Boolean> {
        val result = LinkedHashMap<String, JsonElement>()
        var flattened = false
        for ((key, value) in obj) {
            if (value is JsonObject) {
                flattened = true
                for ((childKey, childValue) in value) {
                    result["$key.$childKey"] = childValue
                }
            } else {
                result[key] = value
            }
        }
        return result to flattened
    }

    private fun jsonElementToCell(element: JsonElement?): Pair<CellValue, Boolean> {
        if (element == null || element is JsonNull) return CellValue.ofNull() to false
        return when (element) {
            is JsonPrimitive -> primitiveToCell(element) to false
            is JsonObject -> CellValue.ofString(element.toString()) to true
            is JsonArray -> CellValue.ofString(element.toString()) to true
            else -> CellValue.ofNull() to false
        }
    }

    private fun primitiveToCell(p: JsonPrimitive): CellValue {
        if (p.isString) return CellValue.ofString(p.content)
        p.booleanOrNull?.let { return CellValue.ofBoolean(p.content, it) }
        p.longOrNull?.let { return CellValue.ofLong(p.content, it) }
        p.doubleOrNull?.let { return CellValue.ofDouble(p.content, it) }
        return CellValue.ofString(p.content)
    }
}
