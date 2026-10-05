package com.pos.model

import kotlinx.serialization.Serializable

@Serializable
data class ValueCount(val value: String, val count: Int)

@Serializable
data class ColumnStats(
    val missingCount: Int,
    val missingPercent: Double,
    val distinctCount: Int,
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
    val stdDev: Double? = null,
    val topValues: List<ValueCount> = emptyList(),
)

@Serializable
data class ColumnProfile(
    val name: String,
    val index: Int,
    val type: ColumnType,
    val confidence: Double,
    val stats: ColumnStats,
)

@Serializable
data class DatasetProfile(
    val rowCount: Int,
    val columnCount: Int,
    val columns: List<ColumnProfile>,
    val warnings: List<String> = emptyList(),
)
