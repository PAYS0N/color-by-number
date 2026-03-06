package com.colorbynumber.app.engine

/**
 * Manages the state of a color-by-number puzzle.
 *
 * @param targetColors The correct color index for each cell (row-major, gridSize*gridSize).
 * @param palette The list of RGB color values.
 * @param paletteOrder Palette indices ordered by first appearance in the grid.
 * @param gridSize The width/height of the square grid.
 */
class PuzzleState(
    val targetColors: IntArray,
    val palette: List<Int>,
    val paletteOrder: List<Int>,
    val gridSize: Int
) {
    /** User's placed colors. -1 means empty/uncolored. */
    val userColors: IntArray = IntArray(gridSize * gridSize) { -1 }

    /** Number of cells pre-filled at creation (excluded from progress). */
    var prefillCount: Int = 0

    /** Settings */
    var preventErrors: Boolean = true
    var preventOverwrite: Boolean = true

    /**
     * Optional listener invoked after every successful cell change.
     * Parameters: row, col, colorIndex (-1 for erase), isErase.
     *
     * The repository subscribes to this to record placement events.
     */
    var onCellChanged: ((row: Int, col: Int, colorIndex: Int, isErase: Boolean) -> Unit)? = null

    /**
     * Attempt to color a cell at [row], [col] with [colorIndex].
     * Returns true if the cell was colored, false if prevented.
     */
    fun colorCell(row: Int, col: Int, colorIndex: Int): Boolean {
        val idx = row * gridSize + col
        if (idx < 0 || idx >= userColors.size) return false

        // Prevent overwrite: can't color over a correct cell
        if (preventOverwrite && userColors[idx] == targetColors[idx]) {
            return false
        }

        // Prevent errors: must match target
        if (preventErrors && colorIndex != targetColors[idx]) {
            return false
        }

        userColors[idx] = colorIndex
        onCellChanged?.invoke(row, col, colorIndex, false)
        return true
    }

    /**
     * Erase a cell. Always allowed regardless of settings.
     */
    fun eraseCell(row: Int, col: Int) {
        val idx = row * gridSize + col
        if (idx in userColors.indices) {
            userColors[idx] = -1
            onCellChanged?.invoke(row, col, -1, true)
        }
    }

    /**
     * Returns true if the cell at [row], [col] has been colored correctly.
     */
    fun isCellCorrect(row: Int, col: Int): Boolean {
        val idx = row * gridSize + col
        return idx in userColors.indices && userColors[idx] == targetColors[idx]
    }

    /**
     * Returns true if the cell at [row], [col] has any color placed.
     */
    fun isCellFilled(row: Int, col: Int): Boolean {
        val idx = row * gridSize + col
        return idx in userColors.indices && userColors[idx] != -1
    }

    /**
     * Count how many cells of a given palette color index need to be filled.
     */
    fun remainingForColor(colorIndex: Int): Int {
        var count = 0
        for (i in targetColors.indices) {
            if (targetColors[i] == colorIndex && userColors[i] != colorIndex) {
                count++
            }
        }
        return count
    }

    /**
     * Count total cells that use this color.
     */
    fun totalForColor(colorIndex: Int): Int {
        return targetColors.count { it == colorIndex }
    }

    /**
     * Returns true if all cells are correctly colored.
     */
    fun isComplete(): Boolean {
        return userColors.indices.all { userColors[it] == targetColors[it] }
    }

    /**
     * Returns the set of palette indices that are fully complete (no remaining cells).
     */
    fun completedColors(): Set<Int> {
        val completed = mutableSetOf<Int>()
        for (ci in palette.indices) {
            if (totalForColor(ci) > 0 && remainingForColor(ci) == 0) {
                completed.add(ci)
            }
        }
        return completed
    }
}
