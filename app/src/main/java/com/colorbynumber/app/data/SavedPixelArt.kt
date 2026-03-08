package com.colorbynumber.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_pixel_arts")
data class SavedPixelArt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val gridSize: Int,

    /** ByteArray-encoded IntArray of ARGB values, row-major. 0 = empty (white). */
    val cellColors: ByteArray,

    /** Currently selected color (ARGB), or null if none was selected. */
    val selectedColor: Int? = null,

    /** Recent colors used, encoded as ByteArray of ARGB ints (most recent first). Null if empty. */
    val recentColors: ByteArray? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedPixelArt) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
