package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DfsPasswordSanitizerTest {

    @Test
    fun parse_mixedDelimiters_extractsAndSanitizesCorrectly() {
        val input = "pass12345; pass67890, short \n pass12345; validPassword99\r\n"
        val result = DfsPasswordSanitizer.parse(input)

        assertEquals(listOf("pass12345", "pass67890", "validPassword99"), result.validPasswords)
        assertEquals(1, result.skippedTooShortCount)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun parse_emptyInput_returnsZeroValid() {
        val result = DfsPasswordSanitizer.parse("   ;; \n  ")
        assertTrue(result.validPasswords.isEmpty())
        assertEquals(0, result.skippedTooShortCount)
        assertEquals(0, result.duplicateCount)
    }

    @Test
    fun parse_nullInput_returnsZeroValid() {
        val result = DfsPasswordSanitizer.parse(null)
        assertTrue(result.validPasswords.isEmpty())
        assertEquals(0, result.skippedTooShortCount)
        assertEquals(0, result.duplicateCount)
    }
}
