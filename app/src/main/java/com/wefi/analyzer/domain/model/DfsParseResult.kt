package com.wefi.analyzer.domain.model

data class DfsParseResult(
    val validPasswords: List<String>,
    val skippedTooShortCount: Int,
    val duplicateCount: Int
)
