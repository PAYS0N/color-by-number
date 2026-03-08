package com.colorbynumber.app.data

import com.colorbynumber.app.engine.PuzzleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GalleryPuzzle(
    val name: String,
    val gridSize: Int,
    val palette: List<Int>,
    val paletteOrder: List<Int>,
    val targetColors: IntArray,
    val prefillIndices: Set<Int> = emptySet()
)

object GalleryRepository {

    private const val BASE_URL = "https://pays0n.github.io/color-by-number/data"

    suspend fun fetchPuzzles(): List<GalleryPuzzle> = withContext(Dispatchers.IO) {
        val puzzleFiles = listOf("puzzle1.json", "puzzle2.json", "puzzle3.json", "puzzle4.json", "puzzle5.json", "puzzle6.json", "puzzle7.json")
        puzzleFiles.mapNotNull { file ->
            try {
                val url = "$BASE_URL/$file"
                android.util.Log.d("GalleryRepo", "Fetching $url")
                val json = fetchJson(url)
                android.util.Log.d("GalleryRepo", "Fetched $file (${json.length} chars)")
                val puzzle = parsePuzzle(json)
                android.util.Log.d("GalleryRepo", "Parsed $file: ${puzzle.name}, ${puzzle.gridSize}x${puzzle.gridSize}")
                puzzle
            } catch (e: Exception) {
                android.util.Log.e("GalleryRepo", "Failed to load $file", e)
                null
            }
        }
    }

    fun toPuzzleState(puzzle: GalleryPuzzle): PuzzleState {
        val state = PuzzleState(
            targetColors = puzzle.targetColors,
            palette = puzzle.palette,
            paletteOrder = puzzle.paletteOrder,
            gridSize = puzzle.gridSize
        )
        for (idx in puzzle.prefillIndices) {
            state.userColors[idx] = state.targetColors[idx]
        }
        state.prefillCount = puzzle.prefillIndices.size
        return state
    }

    private fun fetchJson(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePuzzle(json: String): GalleryPuzzle {
        val obj = JSONObject(json)
        return if (obj.has("data")) parseSparse(obj) else parseDense(obj)
    }

    private fun parseDense(obj: JSONObject): GalleryPuzzle {
        val paletteArr = obj.getJSONArray("palette")
        val orderArr = obj.getJSONArray("paletteOrder")
        val colorsArr = obj.getJSONArray("targetColors")
        return GalleryPuzzle(
            name = obj.getString("name"),
            gridSize = obj.getInt("gridSize"),
            palette = List(paletteArr.length()) { paletteArr.getInt(it) },
            paletteOrder = List(orderArr.length()) { orderArr.getInt(it) },
            targetColors = IntArray(colorsArr.length()) { colorsArr.getInt(it) }
        )
    }

    private fun parseSparse(obj: JSONObject): GalleryPuzzle {
        val gridSize = obj.getInt("gridSize")
        val dataArr = obj.getJSONArray("data")

        // Collect unique colors; white is always first (index 0) for padding cells
        val white = android.graphics.Color.WHITE
        val colorList = mutableListOf(white)

        val cellColors = mutableMapOf<Int, Int>()  // cell index → rgb
        for (i in 0 until dataArr.length()) {
            val entry = dataArr.getJSONObject(i)
            val row = entry.getInt("y")
            val col = entry.getInt("x")
            if (row >= gridSize || col >= gridSize) continue
            val rgb = android.graphics.Color.parseColor(entry.getString("color"))
            if (rgb !in colorList) colorList.add(rgb)
            cellColors[row * gridSize + col] = rgb
        }

        // Build targetColors: default = white (index 0), override specified cells
        val targetColors = IntArray(gridSize * gridSize) { 0 }
        for ((idx, rgb) in cellColors) {
            targetColors[idx] = colorList.indexOf(rgb)
        }

        // Compute paletteOrder by first appearance (left-right, top-bottom)
        val seen = mutableSetOf<Int>()
        val paletteOrder = mutableListOf<Int>()
        for (colorIdx in targetColors) {
            if (seen.add(colorIdx)) paletteOrder.add(colorIdx)
        }
        for (i in colorList.indices) { if (seen.add(i)) paletteOrder.add(i) }

        // Unspecified cells are pre-completed at puzzle start
        val prefillIndices = (0 until gridSize * gridSize).filter { it !in cellColors }.toSet()

        return GalleryPuzzle(
            name = obj.optString("name", "Gallery Puzzle"),
            gridSize = gridSize,
            palette = colorList,
            paletteOrder = paletteOrder,
            targetColors = targetColors,
            prefillIndices = prefillIndices
        )
    }
}
