package com.pos.analysis

import com.pos.model.ColumnProfile
import com.pos.model.ColumnStats
import com.pos.model.ColumnType
import com.pos.model.DatasetProfile
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SuggestionEngineTest {

    private fun column(name: String, index: Int, type: ColumnType, distinctCount: Int = 10, mean: Double? = null, stdDev: Double? = null) =
        ColumnProfile(name, index, type, 1.0, ColumnStats(0, 0.0, distinctCount, mean = mean, stdDev = stdDev))

    @Test
    fun `suggests correlation analysis for multiple numeric columns`() {
        val profile = DatasetProfile(
            rowCount = 100,
            columnCount = 2,
            columns = listOf(
                column("x", 0, ColumnType.INTEGER, mean = 5.0, stdDev = 2.0),
                column("y", 1, ColumnType.DECIMAL, mean = 5.0, stdDev = 2.0),
            ),
        )
        val suggestions = SuggestionEngine.suggest(profile, emptyList())
        assertTrue(suggestions.any { it.type == "Correlation analysis" })
    }

    @Test
    fun `suggests time-series analysis when date and numeric columns exist`() {
        val profile = DatasetProfile(
            rowCount = 100,
            columnCount = 2,
            columns = listOf(
                column("date", 0, ColumnType.DATE),
                column("sales", 1, ColumnType.INTEGER, mean = 5.0, stdDev = 2.0),
            ),
        )
        val suggestions = SuggestionEngine.suggest(profile, emptyList())
        assertTrue(suggestions.any { it.type == "Time-series analysis" })
    }

    @Test
    fun `suggests classification for low-cardinality categorical target`() {
        val profile = DatasetProfile(
            rowCount = 100,
            columnCount = 2,
            columns = listOf(
                column("label", 0, ColumnType.CATEGORICAL, distinctCount = 3),
                column("feature", 1, ColumnType.INTEGER, mean = 5.0, stdDev = 2.0),
            ),
        )
        val suggestions = SuggestionEngine.suggest(profile, emptyList())
        assertTrue(suggestions.any { it.type == "Classification" })
    }

    @Test
    fun `adds small sample caveat for datasets under 30 rows`() {
        val profile = DatasetProfile(
            rowCount = 10,
            columnCount = 2,
            columns = listOf(
                column("x", 0, ColumnType.INTEGER, mean = 5.0, stdDev = 2.0),
                column("y", 1, ColumnType.DECIMAL, mean = 5.0, stdDev = 2.0),
            ),
        )
        val suggestions = SuggestionEngine.suggest(profile, emptyList())
        assertTrue(suggestions.any { s -> s.caveats.any { it.contains("small", ignoreCase = true) } })
    }
}
