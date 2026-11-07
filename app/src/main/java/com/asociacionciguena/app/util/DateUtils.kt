package com.asociacionciguena.app.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanishLocale = Locale.forLanguageTag("es-ES")
private val singleDateFormatter = DateTimeFormatter.ofPattern(
    "EEEE, dd 'de' MMMM 'de' yyyy",
    spanishLocale
)
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d", spanishLocale)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanishLocale)
private val fullDateFormatter = DateTimeFormatter.ofPattern(
    "EEEE d 'de' MMMM 'de' yyyy",
    spanishLocale
)

/**
 * Formatea una fecha LocalDateTime al formato: "Lunes, 15 de Marzo de 2026"
 */
fun formatDate(date: LocalDateTime): String {
    return date.toJavaLocalDateTime().format(singleDateFormatter)
        .replaceFirstChar { it.uppercase() }
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

    val startFormatter = when {
        startJava.year != endJava.year -> fullDateFormatter
        startJava.monthValue != endJava.monthValue -> dayMonthFormatter
        else -> dayFormatter
    }
    return "Del ${startJava.format(startFormatter)} al ${endJava.format(fullDateFormatter)}"
}
