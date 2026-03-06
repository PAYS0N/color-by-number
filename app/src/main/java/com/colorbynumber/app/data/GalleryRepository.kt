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
    val targetColors: IntArray
)

object GalleryRepository {

    private const val BASE_URL = "https://pays0n.github.io/color-by-number/data"

    suspend fun fetchPuzzles(): List<GalleryPuzzle> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("$BASE_URL/puzzle1.json")
            listOf(parsePuzzle(json))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toPuzzleState(puzzle: GalleryPuzzle): PuzzleState {
        return PuzzleState(
            targetColors = puzzle.targetColors,
            palette = puzzle.palette,
            paletteOrder = puzzle.paletteOrder,
            gridSize = puzzle.gridSize
        )
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
}
