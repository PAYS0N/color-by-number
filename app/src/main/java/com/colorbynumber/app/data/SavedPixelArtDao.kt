package com.colorbynumber.app.data

import androidx.room.*

@Dao
interface SavedPixelArtDao {

    @Insert
    suspend fun insert(art: SavedPixelArt): Long

    @Update
    suspend fun update(art: SavedPixelArt)

    @Query("SELECT * FROM saved_pixel_arts WHERE id = :id")
    suspend fun getById(id: Long): SavedPixelArt?

    @Query("SELECT * FROM saved_pixel_arts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SavedPixelArt>

    @Query("DELETE FROM saved_pixel_arts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
