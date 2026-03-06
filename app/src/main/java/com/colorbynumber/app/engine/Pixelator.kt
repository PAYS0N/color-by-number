package com.colorbynumber.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min

/**
 * Handles cropping an image to a square and downscaling it to a pixel grid.
 */
object Pixelator {

    /**
     * Crops the bitmap to a centered square, then scales it down to [gridSize] x [gridSize].
     * Returns the pixelated bitmap.
     */
    fun pixelate(source: Bitmap, gridSize: Int): Bitmap {
        val cropped = cropToSquare(source)
        // Scale down to grid size using nearest-neighbor-like behavior
        // We use FILTER=false for a blocky pixel look
        return Bitmap.createScaledBitmap(cropped, gridSize, gridSize, false).also {
            // Only recycle the crop if it is a distinct intermediate (not the original source,
            // which createBitmap can return as-is for already-square images on some API levels)
            if (it !== cropped && cropped !== source) cropped.recycle()
        }
    }

    /**
     * Crops the bitmap to a centered square whose side length equals the shorter dimension.
     */
    private fun cropToSquare(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val xOffset = (source.width - size) / 2
        val yOffset = (source.height - size) / 2
        return Bitmap.createBitmap(source, xOffset, yOffset, size, size)
    }

    /**
     * Extracts a 2D array of RGB pixel colors from a [gridSize] x [gridSize] bitmap.
     * Returns IntArray of size gridSize*gridSize in row-major order.
     */
    fun extractPixels(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return pixels
    }

    /**
     * Creates a greyscale version of the pixelated bitmap for preview display.
     */
    fun toGreyscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            // Standard luminance formula
            val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            val lightened = (grey * 0.6 + 255 * 0.4).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(lightened, lightened, lightened)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
