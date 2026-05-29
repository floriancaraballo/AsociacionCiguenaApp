package com.asociacionciguena.app.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatea una fecha LocalDateTime al formato: "Lunes, 15 de Marzo de 2026"
 */
fun formatDate(date: LocalDateTime): String {
    val javaDate = date.toJavaLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    return javaDate.format(formatter).replaceFirstChar { it.uppercase() }
}

/**
 * Formatea un rango de fechas al formato premium:
 * - Un solo día: "Lunes, 15 de Marzo de 2026"
 * - Mismo mes: "Del lunes 12 al jueves 15 de marzo de 2026"
 * - Diferente mes: "Del lunes 12 de febrero al jueves 15 de marzo de 2026"
 * - Diferente año: "Del lunes 12 de diciembre de 2025 al jueves 15 de enero de 2026"
 */
fun formatDateRange(startDate: LocalDateTime, endDate: LocalDateTime?): String {
    if (endDate == null || startDate.date == endDate.date) {
        return formatDate(startDate)
    }

    val startJava = startDate.toJavaLocalDateTime()
    val endJava = endDate.toJavaLocalDateTime()

    if (endJava.isBefore(startJava)) {
        return formatDate(startDate)
    }

    val formatterDay = DateTimeFormatter.ofPattern("EEEE d", Locale("es", "ES"))
    val formatterDayMonth = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))
    val formatterFull = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES"))

    return if (startJava.year == endJava.year) {
        if (startJava.monthValue == endJava.monthValue) {
            val startStr = startJava.format(formatterDay)
            val endStr = endJava.format(formatterFull)
            "Del $startStr al $endStr".replaceFirstChar { it.uppercase() }
        } else {
            val startStr = startJava.format(formatterDayMonth)
            val endStr = endJava.format(formatterFull)
            "Del $startStr al $endStr".replaceFirstChar { it.uppercase() }
        }
    } else {
        val startStr = startJava.format(formatterFull)
        val endStr = endJava.format(formatterFull)
        "Del $startStr al $endStr".replaceFirstChar { it.uppercase() }
    }
}