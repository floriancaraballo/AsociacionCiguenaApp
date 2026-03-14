package com.asociacionciguena.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.font.constants.StandardFonts
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.asociacionciguena.app.R

object PdfGenerator {

    /**
     * Generar PDF de autorización firmada
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

        // Crear archivo temporal
        val outputFile = File(context.cacheDir, "autorizacion_${System.currentTimeMillis()}.pdf")

        // Crear PDF
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        // Fuentes
        val boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        val normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)

        // Color corporativo (azul)
        val primaryColor = DeviceRgb(25, 118, 210)

        try {
            // HEADER CON LOGO
            try {
                val logoDrawable = context.getDrawable(R.drawable.logo_ciguena)

                if (logoDrawable != null) {
                    // Crear bitmap del logo
                    val logoBitmap = android.graphics.Bitmap.createBitmap(
                        150, 150, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val logoCanvas = android.graphics.Canvas(logoBitmap)
                    logoDrawable.setBounds(0, 0, logoCanvas.width, logoCanvas.height)
                    logoDrawable.draw(logoCanvas)

                    val logoBytes = ByteArrayOutputStream()
                    logoBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, logoBytes)

                    val logoImageData = ImageDataFactory.create(logoBytes.toByteArray())
                    val logoImage = Image(logoImageData)
                    logoImage.setWidth(60f)
                    logoImage.setHeight(60f)

                    // Logo a la izquierda (esquina superior izquierda)
                    logoImage.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.LEFT)
                    logoImage.setMarginBottom(10f)
                    document.add(logoImage)

                    // Texto centrado debajo del logo
                    val headerText = Paragraph("ASOCIACIÓN CIGÜEÑA")
                    headerText.setFont(boldFont)
                    headerText.setFontSize(20f)
                    headerText.setFontColor(primaryColor)
                    headerText.setTextAlignment(TextAlignment.CENTER)
                    headerText.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)  // ← Sin borde
                    headerText.setMarginTop(5f)
                    document.add(headerText)

                } else {
                    // Sin logo, solo texto
                    val header = Paragraph("ASOCIACIÓN CIGÜEÑA")
                    header.setFont(boldFont)
                    header.setFontSize(20f)
                    header.setFontColor(primaryColor)
                    header.setTextAlignment(TextAlignment.CENTER)
                    document.add(header)
                }
            } catch (e: Exception) {
                // Si falla el logo, solo texto
                android.util.Log.w("PdfGenerator", "Logo no encontrado: ${e.message}")
                val header = Paragraph("ASOCIACIÓN CIGÜEÑA")
                header.setFont(boldFont)
                header.setFontSize(20f)
                header.setFontColor(primaryColor)
                header.setTextAlignment(TextAlignment.CENTER)
                document.add(header)
            }

            val subtitle = Paragraph("Autorización de Participación")
            subtitle.setFont(boldFont)
            subtitle.setFontSize(16f)
            subtitle.setTextAlignment(TextAlignment.CENTER)
            subtitle.setMarginBottom(20f)
            document.add(subtitle)

            document.add(LineSeparator(null))

            // DATOS DE LA EXCURSIÓN
            val excursionHeader = Paragraph("DATOS DE LA EXCURSIÓN")
            excursionHeader.setFont(boldFont)
            excursionHeader.setFontSize(12f)
            excursionHeader.setFontColor(primaryColor)
            excursionHeader.setMarginTop(20f)
            excursionHeader.setMarginBottom(10f)
            document.add(excursionHeader)

            val excursionTitlePara = Paragraph("Excursión: $excursionTitle")
            excursionTitlePara.setFont(normalFont)
            excursionTitlePara.setFontSize(11f)
            document.add(excursionTitlePara)

            val excursionDatePara = Paragraph("Fecha: $excursionDate")
            excursionDatePara.setFont(normalFont)
            excursionDatePara.setFontSize(11f)
            excursionDatePara.setMarginBottom(20f)
            document.add(excursionDatePara)

            document.add(LineSeparator(null))

            // DATOS DEL PARTICIPANTE
            if (minorName != null) {
                val participantHeader = Paragraph("DATOS DEL PARTICIPANTE")
                participantHeader.setFont(boldFont)
                participantHeader.setFontSize(12f)
                participantHeader.setFontColor(primaryColor)
                participantHeader.setMarginTop(20f)
                participantHeader.setMarginBottom(10f)
                document.add(participantHeader)

                val minorNamePara = Paragraph("Nombre del menor: $minorName")
                minorNamePara.setFont(normalFont)
                minorNamePara.setFontSize(11f)
                minorNamePara.setMarginBottom(20f)
                document.add(minorNamePara)

                document.add(LineSeparator(null))
            }

            // DATOS DEL TUTOR
            val tutorHeader = Paragraph(if (minorName != null) "DATOS DEL TUTOR LEGAL" else "DATOS DEL PARTICIPANTE")
            tutorHeader.setFont(boldFont)
            tutorHeader.setFontSize(12f)
            tutorHeader.setFontColor(primaryColor)
            tutorHeader.setMarginTop(20f)
            tutorHeader.setMarginBottom(10f)
            document.add(tutorHeader)

            val tutorNamePara = Paragraph("Nombre: $tutorName")
            tutorNamePara.setFont(normalFont)
            tutorNamePara.setFontSize(11f)
            document.add(tutorNamePara)

            val tutorDniPara = Paragraph("DNI/NIE: $tutorDni")
            tutorDniPara.setFont(normalFont)
            tutorDniPara.setFontSize(11f)
            document.add(tutorDniPara)

            val tutorPhonePara = Paragraph("Teléfono: $tutorPhone")
            tutorPhonePara.setFont(normalFont)
            tutorPhonePara.setFontSize(11f)
            tutorPhonePara.setMarginBottom(20f)
            document.add(tutorPhonePara)

            document.add(LineSeparator(null))

            // AUTORIZACIÓN
            val authHeader = Paragraph("AUTORIZACIÓN")
            authHeader.setFont(boldFont)
            authHeader.setFontSize(12f)
            authHeader.setFontColor(primaryColor)
            authHeader.setMarginTop(20f)
            authHeader.setMarginBottom(10f)
            document.add(authHeader)

            val authText = if (minorName != null) {
                "Yo, $tutorName, con DNI $tutorDni, como padre/madre/tutor legal de $minorName, autorizo su participación en la excursión \"$excursionTitle\" organizada por la Asociación Cigüeña."
            } else {
                "Yo, $tutorName, con DNI $tutorDni, autorizo mi participación en la excursión \"$excursionTitle\" organizada por la Asociación Cigüeña."
            }

            val authPara = Paragraph(authText)
            authPara.setFont(normalFont)
            authPara.setFontSize(10f)
            authPara.setMarginBottom(10f)
            document.add(authPara)

            val disclaimerPara = Paragraph("Declaro conocer y aceptar las condiciones de la actividad y eximir de responsabilidad a la Asociación Cigüeña en caso de accidente.")
            disclaimerPara.setFont(normalFont)
            disclaimerPara.setFontSize(9f)
            disclaimerPara.setMarginBottom(20f)
            document.add(disclaimerPara)

            // FECHA Y FIRMA
            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date())

            val datePara = Paragraph("Fecha y hora: $currentDate")
            datePara.setFont(normalFont)
            datePara.setFontSize(10f)
            datePara.setMarginBottom(10f)
            document.add(datePara)

            val signatureLabelPara = Paragraph("Firma:")
            signatureLabelPara.setFont(boldFont)
            signatureLabelPara.setFontSize(11f)
            signatureLabelPara.setMarginBottom(5f)
            document.add(signatureLabelPara)

            // Convertir firma a imagen
            android.util.Log.d("PdfGen", "📝 Paths de firma: ${signaturePaths.size}")

// Canvas real: fillMaxWidth (~1000px) x 200.dp (~600px) = ratio 5:3
            val signatureBitmap = pathsToBitmap(signaturePaths, 800, 300)

            android.util.Log.d("PdfGen", "🖼️ Bitmap creado: ${signatureBitmap.width}x${signatureBitmap.height}")

            val signatureBytes = ByteArrayOutputStream()
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, signatureBytes)

            android.util.Log.d("PdfGen", "📦 Bytes de imagen: ${signatureBytes.size()}")

            val imageData = ImageDataFactory.create(signatureBytes.toByteArray())
            val signatureImage = Image(imageData)
            signatureImage.setWidth(300f)
            signatureImage.setHeight(112.5f)

            document.add(signatureImage)

            android.util.Log.d("PdfGen", "✅ Firma añadida al PDF")

            // FOOTER
            val footerPara = Paragraph("\n\nDocumento generado electrónicamente por Asociación Cigüeña")
            footerPara.setFont(normalFont)
            footerPara.setFontSize(8f)
            footerPara.setFontColor(ColorConstants.GRAY)
            footerPara.setTextAlignment(TextAlignment.CENTER)
            footerPara.setMarginTop(30f)
            document.add(footerPara)

        } finally {
            document.close()
        }

        return outputFile
    }

    /**
     * Convertir paths de firma a bitmap
     */
    fun pathsToBitmap(
        paths: List<androidx.compose.ui.graphics.Path>,
        width: Int,
        height: Int
    ): Bitmap {
        android.util.Log.d("PdfGen", "🎨 Convirtiendo ${paths.size} paths a bitmap")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fondo blanco
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        paths.forEach { composePath ->
            val androidPath = composePath.asAndroidPath()
            canvas.drawPath(androidPath, paint)
        }

        android.util.Log.d("PdfGen", "✅ Bitmap generado")

        return bitmap
    }
}