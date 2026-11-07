package com.asociacionciguena.app.util

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {
    private val start = LocalDateTime(2026, 3, 9, 10, 0)

    @Test
    fun formatsSingleDateWhenEndIsMissingOnSameDayOrBeforeStart() {
        val expected = "Lunes, 09 de marzo de 2026"

        assertEquals(expected, formatDate(start))
        assertEquals(expected, formatDateRange(start, null))
        assertEquals(expected, formatDateRange(start, LocalDateTime(2026, 3, 9, 14, 0)))
        assertEquals(expected, formatDateRange(start, LocalDateTime(2026, 3, 8, 10, 0)))
    }

    @Test
    fun includesMonthAndYearInRangeOnlyWhenTheyDiffer() {
        assertEquals(
            "Del lunes 9 al martes 10 de marzo de 2026",
            formatDateRange(start, LocalDateTime(2026, 3, 10, 10, 0))
        )
        assertEquals(
            "Del lunes 9 de marzo al jueves 9 de abril de 2026",
            formatDateRange(start, LocalDateTime(2026, 4, 9, 10, 0))
        )
        assertEquals(
            "Del lunes 9 de marzo de 2026 al sábado 9 de enero de 2027",
            formatDateRange(start, LocalDateTime(2027, 1, 9, 10, 0))
        )
    }
}
