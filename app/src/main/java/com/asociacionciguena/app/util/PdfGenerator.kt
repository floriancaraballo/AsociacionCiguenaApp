package com.asociacionciguena.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidPath
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.font.constants.StandardFonts
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.asociacionciguena.app.R

object PdfGenerator {

    /**
     * Generar PDF de autorización firmada con firma escalada correctamente
     */
    fun generateSignedAuthorization(
        context: Context,
        excursionTitle: String,
        excursionDate: String,
        tutorName: String,
        tutorDni: String,
        tutorPhone: String,
        minorName: String?,
        signaturePaths: List<androidx.compose.ui.graphics.Path>
    ): File {

        val outputFile = File(context.cacheDir, "autorizacion_${System.currentTimeMillis()}.pdf")
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        // Fuentes y colores
        val boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        val normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        val primaryColor = DeviceRgb(25, 118, 210)

        try {
            // ───────── HEADER CON LOGO ─────────
            runCatching {
                val logoDrawable = context.getDrawable(R.drawable.logo_ciguena)
                if (logoDrawable != null) {
                    val logoBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
                    val logoCanvas = Canvas(logoBitmap)
                    logoDrawable.setBounds(0, 0, logoCanvas.width, logoCanvas.height)
                    logoDrawable.draw(logoCanvas)

                    val logoBytes = ByteArrayOutputStream()
                    logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, logoBytes)

                    Image(ImageDataFactory.create(logoBytes.toByteArray())).apply {
                        setWidth(60f)
                        setHeight(60f)
                        setHorizontalAlignment(HorizontalAlignment.LEFT)
                        setMarginBottom(10f)
                    }.let { document.add(it) }

                    Paragraph("ASOCIACIÓN CIGÜEÑA").apply {
                        setFont(boldFont)
                        setFontSize(20f)
                        setFontColor(primaryColor)
                        setTextAlignment(TextAlignment.CENTER)
                        setMarginTop(5f)
                    }.let { document.add(it) }
                } else {
                    throw IllegalStateException("Logo no encontrado")
                }
            }.onFailure {
                android.util.Log.w("PdfGenerator", "Logo fallback: ${it.message}")
                Paragraph("ASOCIACIÓN CIGÜEÑA").apply {
                    setFont(boldFont)
                    setFontSize(20f)
                    setFontColor(primaryColor)
                    setTextAlignment(TextAlignment.CENTER)
                }.let { document.add(it) }
            }

            // ───────── SUBTÍTULO ─────────
            Paragraph("Autorización de Participación").apply {
                setFont(boldFont)
                setFontSize(16f)
                setTextAlignment(TextAlignment.CENTER)
                setMarginBottom(20f)
            }.let { document.add(it) }

            document.add(LineSeparator(null))

            // ───────── DATOS DE LA EXCURSIÓN ─────────
            addSectionHeader(document, "DATOS DE LA EXCURSIÓN", boldFont, primaryColor)
            document.add(Paragraph("Excursión: $excursionTitle").apply { setFont(normalFont); setFontSize(11f) })
            document.add(Paragraph("Fecha: $excursionDate").apply { setFont(normalFont); setFontSize(11f); setMarginBottom(20f) })
            document.add(LineSeparator(null))

            // ───────── DATOS DEL PARTICIPANTE (si es menor) ─────────
            if (minorName != null) {
                addSectionHeader(document, "DATOS DEL PARTICIPANTE", boldFont, primaryColor)
                document.add(Paragraph("Nombre del menor: $minorName").apply { setFont(normalFont); setFontSize(11f); setMarginBottom(20f) })
                document.add(LineSeparator(null))
            }

            // ───────── DATOS DEL TUTOR ─────────
            val tutorHeaderTitle = if (minorName != null) "DATOS DEL TUTOR LEGAL" else "DATOS DEL PARTICIPANTE"
            addSectionHeader(document, tutorHeaderTitle, boldFont, primaryColor)
            document.add(Paragraph("Nombre: $tutorName").apply { setFont(normalFont); setFontSize(11f) })
            document.add(Paragraph("DNI/NIE: $tutorDni").apply { setFont(normalFont); setFontSize(11f) })
            document.add(Paragraph("Teléfono: $tutorPhone").apply { setFont(normalFont); setFontSize(11f); setMarginBottom(20f) })
            document.add(LineSeparator(null))

            // ───────── TEXTO DE AUTORIZACIÓN ─────────
            addSectionHeader(document, "AUTORIZACIÓN", boldFont, primaryColor)
            val authText = if (minorName != null) {
                "Yo, $tutorName, con DNI $tutorDni, como padre/madre/tutor legal de $minorName, autorizo su participación en la excursión \"$excursionTitle\" organizada por la Asociación Cigüeña."
            } else {
                "Yo, $tutorName, con DNI $tutorDni, autorizo mi participación en la excursión \"$excursionTitle\" organizada por la Asociación Cigüeña."
            }
            document.add(Paragraph(authText).apply { setFont(normalFont); setFontSize(10f); setMarginBottom(10f) })
            document.add(Paragraph("Declaro conocer y aceptar las condiciones de la actividad y eximir de responsabilidad a la Asociación Cigüeña en caso de accidente.")
                .apply { setFont(normalFont); setFontSize(9f); setMarginBottom(20f) })

            // ───────── FECHA Y FIRMA ─────────
            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date())
            document.add(Paragraph("Fecha y hora: $currentDate").apply { setFont(normalFont); setFontSize(10f); setMarginBottom(10f) })
            document.add(Paragraph("Firma:").apply { setFont(boldFont); setFontSize(11f); setMarginBottom(5f) })

            // Generar bitmap de la firma con Smart Fit
            val signatureBitmap = pathsToBitmap(
                paths = signaturePaths,
                bitmapWidth = 800,
                bitmapHeight = 300,
                padding = 20f
            )

            // [OPCIONAL] Debug: guardar bitmap para inspeccionar
            // saveBitmapDebug(context, signatureBitmap, "debug_signature")

            // Insertar firma en el PDF
            val signatureBytes = ByteArrayOutputStream()
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, signatureBytes)

            Image(ImageDataFactory.create(signatureBytes.toByteArray())).apply {
                setWidth(300f)  // Ancho en puntos PDF
                setHeight(112.5f)  // Mantener proporción 800:300 → 300:112.5
            }.let { document.add(it) }

            // ───────── FOOTER ─────────
            Paragraph("\n\nDocumento generado electrónicamente por Asociación Cigüeña").apply {
                setFont(normalFont)
                setFontSize(8f)
                setFontColor(ColorConstants.GRAY)
                setTextAlignment(TextAlignment.CENTER)
                setMarginTop(30f)
            }.let { document.add(it) }

        } finally {
            document.close()
        }

        return outputFile
    }

    /**
     * Convierte paths de firma a bitmap con ajuste automático de bounds (Smart Fit)
     * - Calcula el área real dibujada y la escala para que encaje perfectamente
     * - Mantiene el aspect ratio original de la firma
     * - No requiere conocer las dimensiones del canvas original
     */
    fun pathsToBitmap(
        paths: List<androidx.compose.ui.graphics.Path>,
        bitmapWidth: Int = 800,
        bitmapHeight: Int = 300,
        padding: Float = 20f
    ): Bitmap {
        // Bitmap en blanco si no hay firma
        if (paths.isEmpty()) {
            return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
            }
        }

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // 1️⃣ Calcular bounds REALES de todos los paths
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        paths.forEach { composePath ->
            val bounds = RectF()
            composePath.asAndroidPath().computeBounds(bounds, true)
            minX = minOf(minX, bounds.left)
            minY = minOf(minY, bounds.top)
            maxX = maxOf(maxX, bounds.right)
            maxY = maxOf(maxY, bounds.bottom)
        }

        val contentWidth = maxX - minX
        val contentHeight = maxY - minY

        // Evitar división por cero si la firma es inválida
        if (contentWidth < 1f || contentHeight < 1f) {
            android.util.Log.w("PdfGenerator", "Firma con bounds inválidos: ${contentWidth}x$contentHeight")
            return bitmap
        }

        // 2️⃣ Calcular escala manteniendo aspect ratio
        val availableWidth = bitmapWidth - (padding * 2)
        val availableHeight = bitmapHeight - (padding * 2)
        val scaleX = availableWidth / contentWidth
        val scaleY = availableHeight / contentHeight
        val scale = minOf(scaleX, scaleY)  // Usar la menor para que quepa en ambos ejes

        // 3️⃣ Configurar paint con stroke escalado proporcionalmente
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 4f * scale  // Escalar grosor para mantener proporción visual
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // 4️⃣ Dibujar cada path aplicando transformación: trasladar → escalar → posicionar
        paths.forEach { composePath ->
            val androidPath = composePath.asAndroidPath()
            val matrix = Matrix().apply {
                postTranslate(-minX, -minY)      // Mover al origen (0,0)
                postScale(scale, scale)           // Escalar uniformemente
                postTranslate(padding, padding)   // Mover a posición con margen
            }
            androidPath.transform(matrix)
            canvas.drawPath(androidPath, paint)
        }

        android.util.Log.d("PdfGenerator", "✅ Bitmap generado: ${bitmapWidth}x${bitmapHeight}, scale=$scale, bounds=(${minX.toInt()},${minY.toInt()})-(${maxX.toInt()},${maxY.toInt()})")
        return bitmap
    }

    // ───────── HELPERS ─────────

    private fun addSectionHeader(document: Document, title: String, font: com.itextpdf.kernel.font.PdfFont, color: DeviceRgb) {
        Paragraph(title).apply {
            setFont(font)
            setFontSize(12f)
            setFontColor(color)
            setMarginTop(20f)
            setMarginBottom(10f)
        }.let { document.add(it) }
    }

    /**
     * [DEBUG] Guarda el bitmap en cache para inspeccionar visualmente
     * Usar solo en desarrollo: adb pull /data/data/com.asociacionciguena.app/cache/debug_signature.png
     */
    @Suppress("unused")
    private fun saveBitmapDebug(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            val file = File(context.cacheDir, "${fileName}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            android.util.Log.d("PdfGenerator", "🔍 Bitmap debug guardado: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Error guardando bitmap debug", e)
        }
    }
}