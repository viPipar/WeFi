package com.wefi.analyzer.domain.util

import com.wefi.analyzer.domain.model.DfsParseResult

/**
 * Sanitizer kata sandi untuk Mode DFS.
 * Memecah masukan multi-pemisah (titik koma, koma, baris baru), membersihkan spasi (trim),
 * menyaring hanya kata sandi yang valid secara standar WPA2/WPA3 (8..63 karakter),
 * dan menghapus entri duplikasi secara otomatis.
 */
object DfsPasswordSanitizer {

    fun parse(rawInput: String?): DfsParseResult {
        if (rawInput.isNullOrBlank()) {
            return DfsParseResult(
                validPasswords = emptyList(),
                skippedTooShortCount = 0,
                duplicateCount = 0
            )
        }

        val tokens = rawInput.split(Regex("[;,\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var skippedTooShort = 0
        var duplicates = 0
        val seen = mutableSetOf<String>()
        val validList = mutableListOf<String>()

        for (token in tokens) {
            if (token.length !in 8..63) {
                skippedTooShort++
            } else if (!seen.add(token)) {
                duplicates++
            } else {
                validList.add(token)
            }
        }

        return DfsParseResult(
            validPasswords = validList,
            skippedTooShortCount = skippedTooShort,
            duplicateCount = duplicates
        )
    }
}
