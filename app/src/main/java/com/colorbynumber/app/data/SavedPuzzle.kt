package com.colorbynumber.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PuzzleStatus {
    IN_PROGRESS,
    COMPLETED
}

@Entity(tableName = "saved_puzzles")
data class SavedPuzzle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val gridSize: Int,

    /** JSON-encoded List<Int> of RGB values. */
    val paletteJson: String,

    /** JSON-encoded List<Int> of palette ordering. */
    val paletteOrderJson: String,

    /** Blob-encoded IntArray – the correct color index per cell (row-major). */
    val targetColors: ByteArray,

    /** Blob-encoded IntArray – the user's placed colors (-1 = empty). */
    val userColors: ByteArray,

    val preventErrors: Boolean = true,
    val preventOverwrite: Boolean = true,

    /** Number of cells pre-filled at puzzle creation (excluded from progress). */
    val prefillCount: Int = 0,

    val status: PuzzleStatus = PuzzleStatus.IN_PROGRESS,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Room needs equals/hashCode when ByteArray fields are present
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedPuzzle) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
