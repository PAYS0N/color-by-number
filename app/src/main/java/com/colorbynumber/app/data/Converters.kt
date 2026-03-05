package com.colorbynumber.app.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * Room type converters for custom types used in entities.
 */
class Converters {

    // ---- IntArray <-> ByteArray (blob storage) ----

    @TypeConverter
    fun fromIntArray(value: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * 4)
        buffer.asIntBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    fun toIntArray(bytes: ByteArray): IntArray {
        val intBuffer: IntBuffer = ByteBuffer.wrap(bytes).asIntBuffer()
        val result = IntArray(intBuffer.remaining())
        intBuffer.get(result)
        return result
    }

    // ---- List<Int> <-> String (simple comma-separated JSON-like encoding) ----

    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return value.split(",").map { it.trim().toInt() }
    }

    // ---- PuzzleStatus enum ----

    @TypeConverter
    fun fromPuzzleStatus(status: PuzzleStatus): String = status.name

    @TypeConverter
    fun toPuzzleStatus(value: String): PuzzleStatus = PuzzleStatus.valueOf(value)

    // ---- PlacementEventType enum ----

    @TypeConverter
    fun fromEventType(type: PlacementEventType): String = type.name

    @TypeConverter
    fun toEventType(value: String): PlacementEventType = PlacementEventType.valueOf(value)
}
