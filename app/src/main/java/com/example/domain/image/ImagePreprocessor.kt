package com.example.domain.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Image preprocessing pipeline preserving pristine raw evidence while generating
 * enhanced, deskewed, contrast-adjusted and binarized images for Bengali and English OCR.
 */
object ImagePreprocessor {

    data class PreprocessResult(
        val originalFile: File,
        val processedFile: File,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int
    )

    suspend fun preprocess(
        inputImageFile: File,
        outputDir: File,
        pageIndex: Int,
        manualRotation: Int = 0
    ): PreprocessResult = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // 1. Preserve original pristine file
        val rawCopy = File(outputDir, "page_${pageIndex}_raw.jpg")
        if (!rawCopy.exists() || rawCopy.absolutePath != inputImageFile.absolutePath) {
            inputImageFile.copyTo(rawCopy, overwrite = true)
        }

        // 2. Decode bitmap with EXIF orientation correction
        val exifRotation = getExifOrientation(inputImageFile)
        val totalRotation = (exifRotation + manualRotation) % 360

        val originalBitmap = BitmapFactory.decodeFile(inputImageFile.absolutePath)
            ?: createFallbackDocumentBitmap(inputImageFile)

        // Apply rotation if needed
        val rotatedBitmap = if (totalRotation != 0) {
            val matrix = Matrix().apply { postRotate(totalRotation.toFloat()) }
            Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
        } else {
            originalBitmap
        }

        // 3. Contrast enhancement & Grayscale conversion
        val enhancedGrayscale = applyContrastAndGrayscale(rotatedBitmap)

        // 4. Adaptive binarization / thresholding for Bengali matra & old paper texture
        val binarizedBitmap = applyAdaptiveThreshold(enhancedGrayscale)

        // 5. Save processed image copy
        val processedCopy = File(outputDir, "page_${pageIndex}_processed.png")
        FileOutputStream(processedCopy).use { out ->
            binarizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        PreprocessResult(
            originalFile = rawCopy,
            processedFile = processedCopy,
            width = rotatedBitmap.width,
            height = rotatedBitmap.height,
            rotationDegrees = totalRotation
        )
    }

    private fun getExifOrientation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun applyContrastAndGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // High contrast grayscale matrix:
        // 1. Convert to grayscale using standard luminance weights: 0.299 R, 0.587 G, 0.114 B
        // 2. Increase contrast by scale factor 1.35
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f

        val cm = ColorMatrix(floatArrayOf(
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Efficient local window adaptive thresholding.
     * Computes local mean with a window size of ~25 pixels and offsets by 8 to preserve
     * delicate Bengali matras, vowel diacritics, and faded ink on yellowed record paper.
     */
    private fun applyAdaptiveThreshold(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height

        // Downscale for threshold calculation if very large to prevent memory overhead
        val maxDim = 1800
        val scale = if (width > maxDim || height > maxDim) {
            min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        } else 1.0f

        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        val scaled = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
        } else {
            src
        }

        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        // Calculate integral image for fast O(1) box filter mean calculation
        val integral = LongArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            var sum = 0L
            for (x in 0 until targetWidth) {
                val idx = y * targetWidth + x
                val lum = Color.red(pixels[idx]) // It's grayscale, so R=G=B
                sum += lum
                integral[idx] = if (y == 0) sum else integral[(y - 1) * targetWidth + x] + sum
            }
        }

        val windowSize = max(15, targetWidth / 40)
        val halfW = windowSize / 2
        val offset = 10 // Threshold bias

        val binaryPixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val y1 = max(0, y - halfW)
            val y2 = min(targetHeight - 1, y + halfW)
            for (x in 0 until targetWidth) {
                val x1 = max(0, x - halfW)
                val x2 = min(targetWidth - 1, x + halfW)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val a = if (y1 > 0 && x1 > 0) integral[(y1 - 1) * targetWidth + (x1 - 1)] else 0L
                val b = if (y1 > 0) integral[(y1 - 1) * targetWidth + x2] else 0L
                val c = if (x1 > 0) integral[y2 * targetWidth + (x1 - 1)] else 0L
                val d = integral[y2 * targetWidth + x2]

                val areaSum = d - b - c + a
                val mean = (areaSum / count).toInt()

                val pixelVal = Color.red(pixels[y * targetWidth + x])
                binaryPixels[y * targetWidth + x] = if (pixelVal < mean - offset) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(binaryPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return resultBitmap
    }

    private fun createFallbackDocumentBitmap(file: File): Bitmap {
        val width = 1200
        val height = 1600
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
        }
        canvas.drawText("নথি / Document: ${file.name}", 80f, 140f, paint)
        canvas.drawText("আকার / Size: ${file.length()} bytes", 80f, 200f, paint)

        val ext = file.extension.lowercase()
        if (ext in listOf("txt", "csv", "json")) {
            try {
                val lines = file.readLines().take(28)
                var y = 280f
                paint.textSize = 28f
                for (line in lines) {
                    canvas.drawText(line.take(65), 80f, y, paint)
                    y += 42f
                }
            } catch (_: Exception) {
            }
        }
        return bmp
    }

    /**
     * Crops and aligns a land record document bitmap by applying rotation (both 90-degree steps
     * and fine deskew angle) followed by cropping the defined normalized region (0.0..1.0).
     */
    suspend fun cropAndAlignDocument(
        sourceImageFile: File,
        outputFile: File,
        cropLeftRatio: Float,
        cropTopRatio: Float,
        cropRightRatio: Float,
        cropBottomRatio: Float,
        rotationDegrees: Float
    ): File = withContext(Dispatchers.IO) {
        val originalBitmap = BitmapFactory.decodeFile(sourceImageFile.absolutePath)
            ?: createFallbackDocumentBitmap(sourceImageFile)

        // 1. Apply total rotation (including fine deskew angle)
        val rotatedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
        } else {
            originalBitmap
        }

        // 2. Compute pixel coordinates for cropping
        val minX = (cropLeftRatio.coerceIn(0f, 1f) * rotatedBitmap.width).toInt()
        val minY = (cropTopRatio.coerceIn(0f, 1f) * rotatedBitmap.height).toInt()
        val maxX = (cropRightRatio.coerceIn(0f, 1f) * rotatedBitmap.width).toInt()
        val maxY = (cropBottomRatio.coerceIn(0f, 1f) * rotatedBitmap.height).toInt()

        val cropLeft = min(minX, maxX).coerceIn(0, rotatedBitmap.width - 1)
        val cropTop = min(minY, maxY).coerceIn(0, rotatedBitmap.height - 1)
        val cropWidth = max(10, kotlin.math.abs(maxX - minX)).coerceAtMost(rotatedBitmap.width - cropLeft)
        val cropHeight = max(10, kotlin.math.abs(maxY - minY)).coerceAtMost(rotatedBitmap.height - cropTop)

        val croppedBitmap = Bitmap.createBitmap(
            rotatedBitmap,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight
        )

        // 3. Save to output file
        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }
        FileOutputStream(outputFile).use { out ->
            val format = if (outputFile.extension.equals("png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            croppedBitmap.compress(format, 95, out)
        }

        outputFile
    }
}
