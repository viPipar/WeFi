package com.wefi.analyzer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkHealthModelsTest {

    @Test
    fun testHealthScoreGrade_fromScore_assignsCorrectGrade() {
        assertEquals(HealthScoreGrade.A_PLUS, HealthScoreGrade.fromScore(95))
        assertEquals(HealthScoreGrade.A_PLUS, HealthScoreGrade.fromScore(90))
        assertEquals(HealthScoreGrade.A, HealthScoreGrade.fromScore(89))
        assertEquals(HealthScoreGrade.A, HealthScoreGrade.fromScore(80))
        assertEquals(HealthScoreGrade.B, HealthScoreGrade.fromScore(79))
        assertEquals(HealthScoreGrade.B, HealthScoreGrade.fromScore(65))
        assertEquals(HealthScoreGrade.C, HealthScoreGrade.fromScore(64))
        assertEquals(HealthScoreGrade.C, HealthScoreGrade.fromScore(50))
        assertEquals(HealthScoreGrade.CRITICAL, HealthScoreGrade.fromScore(49))
        assertEquals(HealthScoreGrade.CRITICAL, HealthScoreGrade.fromScore(0))
    }
}
