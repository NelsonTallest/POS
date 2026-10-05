package com.pos.model

import kotlinx.serialization.Serializable

@Serializable
enum class ColumnType {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    CATEGORICAL,
    IDENTIFIER,
    FREE_TEXT,
    CONSTANT,
    EMPTY,
}
