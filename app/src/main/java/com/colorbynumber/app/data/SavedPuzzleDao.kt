package com.colorbynumber.app.data

import androidx.room.*

@Dao
interface SavedPuzzleDao {

    @Insert
    suspend fun insert(puzzle: SavedPuzzle): Long

    @Update
    suspend fun update(puzzle: SavedPuzzle)

    @Query("SELECT * FROM saved_puzzles WHERE id = :id")
    suspend fun getById(id: Long): SavedPuzzle?

    @Query("SELECT * FROM saved_puzzles WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getByStatus(status: PuzzleStatus): List<SavedPuzzle>

    @Query("SELECT * FROM saved_puzzles ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SavedPuzzle>

    @Query("SELECT * FROM saved_puzzles WHERE status = 'IN_PROGRESS' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentInProgress(): SavedPuzzle?

    @Delete
    suspend fun delete(puzzle: SavedPuzzle)

    @Query("DELETE FROM saved_puzzles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
