package com.asociacionciguena.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object BankingUtils {

    private const val ASSOCIATION_IBAN = "ES5901825332130207495699"
    private const val ASSOCIATION_NAME = "Asociación Cigüeña"

    /**
     * Intenta abrir la app bancaria del usuario con datos prellenados
     * Primero intenta SEPA URI, si falla muestra los datos para copiar
     */
    fun openBankingApp(
        context: Context,
        amount: Double,
        concept: String
    ): Boolean {
        return try {
            // Intentar varios formatos de URI bancario
            val uris = listOf(
                // Formato 1: SEPA estándar
                buildSepaUri(ASSOCIATION_IBAN, amount, concept),
                // Formato 2: Intent genérico de transferencia
                "bank://transfer?iban=$ASSOCIATION_IBAN&amount=${String.format("%.2f", amount)}&concept=${Uri.encode(concept)}",
                // Formato 3: Intent de pago
                "payment://sepa?iban=$ASSOCIATION_IBAN&amount=${String.format("%.2f", amount)}&reference=${Uri.encode(concept)}"
            )

            var opened = false

            for (uriString in uris) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                val packageManager = context.packageManager
                val activities = packageManager.queryIntentActivities(intent, 0)

                if (activities.isNotEmpty()) {
                    context.startActivity(intent)
                    opened = true
                    break
                }
            }

            if (!opened) {
                android.util.Log.d("BankingUtils", "No se encontró app bancaria compatible")
            }

            opened

        } catch (e: Exception) {
            android.util.Log.e("BankingUtils", "Error abriendo app bancaria: ${e.message}")
            false
        }
    }

    /**
     * Construir URI SEPA estándar
     */
    private fun buildSepaUri(
        iban: String,
        amount: Double,
        concept: String
    ): String {
        return "sepa://sct?iban=$iban&amount=${String.format("%.2f", amount)}&text=${Uri.encode(concept)}"
    }

    /**
     * Obtener datos bancarios como texto formateado
     */
    fun getBankingDetailsText(
        amount: Double,
        concept: String
    ): String {
        return """
💶 DATOS PARA TRANSFERENCIA

IBAN: $ASSOCIATION_IBAN
Titular: $ASSOCIATION_NAME
Importe: ${String.format("%.2f€", amount)}
Concepto: $concept

Copia estos datos y ábrelos en tu app bancaria.
        """.trimIndent()
    }

    /**
     * Copiar datos al portapapeles
     */
    fun copyToClipboard(
        context: Context,
        amount: Double,
        concept: String
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager

        val clip = android.content.ClipData.newPlainText(
            "Datos bancarios",
            getBankingDetailsText(amount, concept)
        )

        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            context,
            "✓ Datos copiados al portapapeles",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Copiar solo el IBAN al portapapeles
     */
    fun copyIbanToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager

        val clip = android.content.ClipData.newPlainText("IBAN", ASSOCIATION_IBAN)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            context,
            "✓ IBAN copiado: $ASSOCIATION_IBAN",
            Toast.LENGTH_SHORT
        ).show()
    }
}