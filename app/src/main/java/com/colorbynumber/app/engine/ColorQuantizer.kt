package com.colorbynumber.app.engine

import android.graphics.Color
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Quantizes pixel colors to a reduced palette using k-means clustering
 * followed by RGB distance merging to eliminate near-duplicate colors.
 */
object ColorQuantizer {

    enum class DetailLevel(val factor: Float) {
        LOW(0.75f),
        MEDIUM(1.0f),
        HIGH(1.5f)
    }

    data class QuantizationResult(
        /** The color index for each pixel in row-major order. */
        val colorIndices: IntArray,
        /** The palette: list of RGB colors, indexed by the values in [colorIndices]. */
        val palette: List<Int>,
        /** Order of palette indices by first appearance (left-right, top-bottom scan). */
        val paletteOrder: List<Int>,
        /** Grid width */
        val gridSize: Int
    )

    /**
     * Quantize the pixel array to a reduced color palette.
     *
     * @param pixels Raw ARGB pixel array from the pixelated bitmap
     * @param gridSize Width (and height) of the square grid
     * @param detailLevel Controls target number of colors: factor * gridSize
     */
    fun quantize(pixels: IntArray, gridSize: Int, detailLevel: DetailLevel): QuantizationResult {
        val targetColors = (detailLevel.factor * gridSize).toInt().coerceIn(2, 200)
        val baseMinDistance = 15.0 // Always merge colors closer than this

        // Step 1: Get unique colors
        val uniqueColors = pixels.toSet().toList()

        // Step 2: K-means clustering if needed, otherwise start from unique colors
        var palette: List<Int> = if (uniqueColors.size > targetColors) {
            kMeans(pixels, targetColors, maxIterations = 20)
        } else {
            uniqueColors
        }

        // Step 3: Merge colors closer than baseMinDistance (always, even if we skipped k-means)
        palette = mergeClosePalette(palette, baseMinDistance)

        // Step 4: Assign each pixel to the nearest palette color
        val colorIndices = assignPixels(pixels, palette)

        // Step 5: Merge small clusters — any color covering fewer than minClusterCells
        //         gets reassigned to its nearest neighbour and removed from the palette.
        val minClusterCells = gridSize // e.g. 20 cells on a 20×20 grid
        palette = mergeSmallClusters(colorIndices, palette, minClusterCells)

        // Step 6: Re-assign after small-cluster merges
        val finalColorIndices = assignPixels(pixels, palette)

        // Step 7: Determine palette order by first appearance
        val paletteOrder = computePaletteOrder(finalColorIndices, palette.size, gridSize)

        return QuantizationResult(
            colorIndices = finalColorIndices,
            palette = palette,
            paletteOrder = paletteOrder,
            gridSize = gridSize
        )
    }

    /**
     * Remove palette entries whose assigned cell count is below [minCells].
     * Pixels that were assigned to removed entries will be re-assigned to their
     * nearest remaining colour by the [assignPixels] call in the caller.
     * Always retains at least 1 colour.
     */
    private fun mergeSmallClusters(colorIndices: IntArray, palette: List<Int>, minCells: Int): List<Int> {
        val counts = IntArray(palette.size)
        for (idx in colorIndices) counts[idx]++
        val filtered = palette.filterIndexed { i, _ -> counts[i] >= minCells }
        // Guard: never return an empty palette
        return filtered.ifEmpty {
            listOf(palette[counts.indices.maxByOrNull { counts[it] } ?: 0])
        }
    }

    /**
     * Simple k-means clustering on RGB values.
     */
    private fun kMeans(pixels: IntArray, k: Int, maxIterations: Int): List<Int> {
        val rng = Random(42)
        val uniquePixels = pixels.toSet().toList()

        // Initialize centroids by random selection from unique pixels
        val centroids = Array(k) {
            val c = uniquePixels[rng.nextInt(uniquePixels.size)]
            floatArrayOf(Color.red(c).toFloat(), Color.green(c).toFloat(), Color.blue(c).toFloat())
        }

        val assignments = IntArray(pixels.size)

        repeat(maxIterations) {
            // Assign each pixel to nearest centroid
            var changed = false
            for (i in pixels.indices) {
                val r = Color.red(pixels[i]).toFloat()
                val g = Color.green(pixels[i]).toFloat()
                val b = Color.blue(pixels[i]).toFloat()
                var bestDist = Float.MAX_VALUE
                var bestIdx = 0
                for (c in centroids.indices) {
                    val dr = r - centroids[c][0]
                    val dg = g - centroids[c][1]
                    val db = b - centroids[c][2]
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = c
                    }
                }
                if (assignments[i] != bestIdx) {
                    assignments[i] = bestIdx
                    changed = true
                }
            }

            if (!changed) return@repeat

            // Recalculate centroids
            val sums = Array(k) { floatArrayOf(0f, 0f, 0f) }
            val counts = IntArray(k)
            for (i in pixels.indices) {
                val ci = assignments[i]
                sums[ci][0] += Color.red(pixels[i]).toFloat()
                sums[ci][1] += Color.green(pixels[i]).toFloat()
                sums[ci][2] += Color.blue(pixels[i]).toFloat()
                counts[ci]++
            }
            for (c in centroids.indices) {
                if (counts[c] > 0) {
                    centroids[c][0] = sums[c][0] / counts[c]
                    centroids[c][1] = sums[c][1] / counts[c]
                    centroids[c][2] = sums[c][2] / counts[c]
                }
            }
        }

        return centroids.map { Color.rgb(it[0].toInt().coerceIn(0, 255), it[1].toInt().coerceIn(0, 255), it[2].toInt().coerceIn(0, 255)) }
    }

    /**
     * Iteratively merge palette colors that are within [minDistance] RGB Euclidean distance.
     */
    private fun mergeClosePalette(palette: List<Int>, minDistance: Double): List<Int> {
        val merged = palette.toMutableList()

        var foundMerge = true
        while (foundMerge) {
            foundMerge = false
            var i = 0
            while (i < merged.size) {
                var j = i + 1
                while (j < merged.size) {
                    if (rgbDistance(merged[i], merged[j]) < minDistance) {
                        // Merge j into i by averaging
                        val r = (Color.red(merged[i]) + Color.red(merged[j])) / 2
                        val g = (Color.green(merged[i]) + Color.green(merged[j])) / 2
                        val b = (Color.blue(merged[i]) + Color.blue(merged[j])) / 2
                        merged[i] = Color.rgb(r, g, b)
                        merged.removeAt(j)
                        foundMerge = true
                    } else {
                        j++
                    }
                }
                i++
            }
        }

        return merged
    }

    /**
     * Assign each pixel to its nearest palette color index.
     */
    private fun assignPixels(pixels: IntArray, palette: List<Int>): IntArray {
        return IntArray(pixels.size) { i ->
            var bestDist = Double.MAX_VALUE
            var bestIdx = 0
            for (c in palette.indices) {
                val dist = rgbDistance(pixels[i], palette[c])
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = c
                }
            }
            bestIdx
        }
    }

    /**
     * Compute palette order by scanning the grid left-right, top-bottom
     * and recording the order in which each color index first appears.
     */
    private fun computePaletteOrder(colorIndices: IntArray, paletteSize: Int, gridSize: Int): List<Int> {
        val seen = mutableSetOf<Int>()
        val order = mutableListOf<Int>()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = colorIndices[row * gridSize + col]
                if (idx !in seen) {
                    seen.add(idx)
                    order.add(idx)
                }
            }
        }
        // Add any palette entries that somehow weren't found (shouldn't happen)
        for (i in 0 until paletteSize) {
            if (i !in seen) order.add(i)
        }
        return order
    }

    private fun rgbDistance(c1: Int, c2: Int): Double {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
