package com.colorbynumber.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlacementEventType {
    PLACE,
    ERASE
}

@Entity(
    tableName = "placement_events",
    foreignKeys = [
        ForeignKey(
            entity = SavedPuzzle::class,
            parentColumns = ["id"],
            childColumns = ["puzzleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("puzzleId")]
)
data class PlacementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val puzzleId: Long,

    /** Epoch millis when this placement occurred. */
    val timestamp: Long = System.currentTimeMillis(),

    val row: Int,
    val col: Int,

    /** The palette color index placed, or -1 for erase. */
    val colorIndex: Int,

    val eventType: PlacementEventType
)
