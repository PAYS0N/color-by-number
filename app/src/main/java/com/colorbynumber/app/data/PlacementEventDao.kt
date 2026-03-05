package com.colorbynumber.app.data

import androidx.room.*

@Dao
interface PlacementEventDao {

    @Insert
    suspend fun insert(event: PlacementEvent)

    @Insert
    suspend fun insertAll(events: List<PlacementEvent>)

    @Query("SELECT * FROM placement_events WHERE puzzleId = :puzzleId ORDER BY timestamp ASC")
    suspend fun getAllForPuzzle(puzzleId: Long): List<PlacementEvent>

    @Query("SELECT COUNT(*) FROM placement_events WHERE puzzleId = :puzzleId")
    suspend fun countForPuzzle(puzzleId: Long): Int

    @Query("DELETE FROM placement_events WHERE puzzleId = :puzzleId")
    suspend fun deleteForPuzzle(puzzleId: Long)
}
