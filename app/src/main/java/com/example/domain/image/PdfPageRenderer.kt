package com.example.domain.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfPageRenderer {

    data class RenderedPdfResult(
        val totalPages: Int,
        val pageImageFiles: List<File>
    )

    suspend fun renderPdfPages(
        context: Context,
        pdfFileOrUri: Any, // File or Uri
        outputDirectory: File,
        targetWidth: Int = 1800
    ): RenderedPdfResult = withContext(Dispatchers.IO) {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val parcelFileDescriptor: ParcelFileDescriptor = when (pdfFileOrUri) {
            is File -> ParcelFileDescriptor.open(pdfFileOrUri, ParcelFileDescriptor.MODE_READ_ONLY)
            is Uri -> context.contentResolver.openFileDescriptor(pdfFileOrUri, "r")
                ?: throw IllegalArgumentException("Could not open FileDescriptor for URI: $pdfFileOrUri")
            else -> throw IllegalArgumentException("Unsupported PDF input type: ${pdfFileOrUri::class.java}")
        }

        val pageFiles = mutableListOf<File>()

        parcelFileDescriptor.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            val pageCount = pdfRenderer.pageCount

            for (pageIndex in 0 until pageCount) {
                val page = pdfRenderer.openPage(pageIndex)

                // Maintain aspect ratio with high resolution target width
                val scale = targetWidth.toFloat() / page.width
                val targetHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                // White background for transparent PDF pages
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val outputFile = File(outputDirectory, "page_${pageIndex + 1}_raw.png")
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                pageFiles.add(outputFile)
            }

            pdfRenderer.close()
            RenderedPdfResult(
                totalPages = pageCount,
                pageImageFiles = pageFiles
            )
        }
    }
}
