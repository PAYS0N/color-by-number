package com.colorbynumber.app.engine

class PixelArtState(
    val gridSize: Int
) {
    /** Cell colors as ARGB ints. 0 = empty (renders as white). */
    val cells: IntArray = IntArray(gridSize * gridSize) { 0 }

    /** Currently selected color (ARGB), or null for no selection. */
    var selectedColor: Int? = null

    /** Recent colors used, most recent first. Max 10. */
    val recentColors: MutableList<Int> = mutableListOf()

    /** Whether eraser mode is active. */
    var isEraser: Boolean = false

    fun colorCell(row: Int, col: Int, colorArgb: Int): Boolean {
        val idx = row * gridSize + col
        if (idx !in cells.indices) return false
        if (cells[idx] == colorArgb) return false
        cells[idx] = colorArgb
        return true
    }

    fun eraseCell(row: Int, col: Int): Boolean {
        val idx = row * gridSize + col
        if (idx !in cells.indices) return false
        if (cells[idx] == 0) return false
        cells[idx] = 0
        return true
    }

    fun addRecentColor(colorArgb: Int) {
        recentColors.remove(colorArgb)
        recentColors.add(0, colorArgb)
        if (recentColors.size > 10) recentColors.removeAt(10)
    }

    fun selectColor(colorArgb: Int) {
        selectedColor = colorArgb
        isEraser = false
        addRecentColor(colorArgb)
    }

    fun selectEraser() {
        isEraser = true
        selectedColor = null
    }
}
