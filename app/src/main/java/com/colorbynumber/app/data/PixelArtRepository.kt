package com.colorbynumber.app.data

import com.colorbynumber.app.engine.PixelArtState

class PixelArtRepository(
    private val dao: SavedPixelArtDao
) {

    suspend fun getAll(): List<SavedPixelArt> = dao.getAll()

    suspend fun getById(id: Long): SavedPixelArt? = dao.getById(id)

    /** Persist a new pixel art and return its database ID. */
    suspend fun save(state: PixelArtState): Long {
        val entity = SavedPixelArt(
            gridSize = state.gridSize,
            cellColors = intArrayToBytes(state.cells)
        )
        return dao.insert(entity)
    }

    /** Update an existing pixel art entry. */
    suspend fun update(id: Long, state: PixelArtState) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                cellColors = intArrayToBytes(state.cells),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Load cell colors from a saved entry into a new PixelArtState. */
    suspend fun loadState(id: Long): PixelArtState? {
        val entity = dao.getById(id) ?: return null
        val state = PixelArtState(entity.gridSize)
        val cells = bytesToIntArray(entity.cellColors)
        cells.copyInto(state.cells)
        return state
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    private fun intArrayToBytes(arr: IntArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(arr.size * 4)
        buf.asIntBuffer().put(arr)
        return buf.array()
    }

    private fun bytesToIntArray(bytes: ByteArray): IntArray {
        val intBuf = java.nio.ByteBuffer.wrap(bytes).asIntBuffer()
        val result = IntArray(intBuf.remaining())
        intBuf.get(result)
        return result
    }
}
