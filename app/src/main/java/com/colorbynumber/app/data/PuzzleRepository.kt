package com.colorbynumber.app.data

import com.colorbynumber.app.engine.PuzzleReplayState
import com.colorbynumber.app.engine.PuzzleState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates persistence of puzzles and placement events.
 *
 * Buffers placement events in memory and flushes them to the database
 * in batches to keep drag-painting smooth.
 */
class PuzzleRepository(
    private val puzzleDao: SavedPuzzleDao,
    private val eventDao: PlacementEventDao
) {
    companion object {
        /** Flush the event buffer when it reaches this size. */
        const val FLUSH_THRESHOLD = 50
    }

    private val eventBuffer = mutableListOf<PlacementEvent>()
    private val bufferMutex = Mutex()

    /** The database ID of the currently active puzzle, or null. */
    var activePuzzleId: Long? = null
        private set

    // ---------------------------------------------------------------
    // Creating & loading puzzles
    // ---------------------------------------------------------------

    /**
     * Persist a newly-built puzzle and return its database ID.
     */
    suspend fun createPuzzle(puzzleState: PuzzleState): Long {
        val entity = SavedPuzzle(
            gridSize = puzzleState.gridSize,
            paletteJson = puzzleState.palette.joinToString(","),
            paletteOrderJson = puzzleState.paletteOrder.joinToString(","),
            targetColors = intArrayToBytes(puzzleState.targetColors),
            userColors = intArrayToBytes(puzzleState.userColors),
            preventErrors = puzzleState.preventErrors,
            preventOverwrite = puzzleState.preventOverwrite
        )
        val id = puzzleDao.insert(entity)
        activePuzzleId = id
        return id
    }

    /**
     * Reconstruct a [PuzzleState] from a saved entity.
     */
    suspend fun loadPuzzle(id: Long): PuzzleState? {
        val entity = puzzleDao.getById(id) ?: return null
        activePuzzleId = id

        val palette = entity.paletteJson.split(",").map { it.trim().toInt() }
        val paletteOrder = entity.paletteOrderJson.split(",").map { it.trim().toInt() }
        val targetColors = bytesToIntArray(entity.targetColors)
        val userColors = bytesToIntArray(entity.userColors)

        val state = PuzzleState(
            targetColors = targetColors,
            palette = palette,
            paletteOrder = paletteOrder,
            gridSize = entity.gridSize
        )
        // Restore user progress
        userColors.copyInto(state.userColors)
        state.preventErrors = entity.preventErrors
        state.preventOverwrite = entity.preventOverwrite
        return state
    }

    /**
     * Return the most recent in-progress puzzle ID, or null.
     */
    suspend fun getMostRecentInProgressId(): Long? {
        return puzzleDao.getMostRecentInProgress()?.id
    }

    /**
     * Return all saved puzzles, newest first.
     */
    suspend fun getAll(): List<SavedPuzzle> = puzzleDao.getAll()

    // ---------------------------------------------------------------
    // Recording placement events (buffered)
    // ---------------------------------------------------------------

    /**
     * Record a single cell placement or erase. Call this from
     * PuzzleState's change callback.
     *
     * Events are buffered in memory; call [flush] to write them to DB.
     * The buffer auto-flushes when it hits [FLUSH_THRESHOLD].
     */
    suspend fun recordEvent(
        row: Int,
        col: Int,
        colorIndex: Int,
        eventType: PlacementEventType
    ) {
        val puzzleId = activePuzzleId ?: return

        val event = PlacementEvent(
            puzzleId = puzzleId,
            row = row,
            col = col,
            colorIndex = colorIndex,
            eventType = eventType
        )

        val shouldFlush: Boolean
        bufferMutex.withLock {
            eventBuffer.add(event)
            shouldFlush = eventBuffer.size >= FLUSH_THRESHOLD
        }

        if (shouldFlush) {
            flush()
        }
    }

    /**
     * Write all buffered events to the database and clear the buffer.
     */
    suspend fun flush() {
        val toWrite: List<PlacementEvent>
        bufferMutex.withLock {
            if (eventBuffer.isEmpty()) return
            toWrite = eventBuffer.toList()
            eventBuffer.clear()
        }
        eventDao.insertAll(toWrite)
    }

    // ---------------------------------------------------------------
    // Snapshotting & completing
    // ---------------------------------------------------------------

    /**
     * Persist the current user-color state (call on pause / periodically).
     */
    suspend fun snapshotUserColors(puzzleState: PuzzleState) {
        val id = activePuzzleId ?: return
        val entity = puzzleDao.getById(id) ?: return
        puzzleDao.update(
            entity.copy(
                userColors = intArrayToBytes(puzzleState.userColors),
                preventErrors = puzzleState.preventErrors,
                preventOverwrite = puzzleState.preventOverwrite,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Mark the active puzzle as completed, flush events, snapshot colors.
     */
    suspend fun markCompleted(puzzleState: PuzzleState) {
        flush()
        val id = activePuzzleId ?: return
        val entity = puzzleDao.getById(id) ?: return
        puzzleDao.update(
            entity.copy(
                userColors = intArrayToBytes(puzzleState.userColors),
                status = PuzzleStatus.COMPLETED,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Retrieve all placement events for a puzzle, ordered by timestamp.
     * This is the data source for future video generation.
     */
    suspend fun getEventsForPuzzle(puzzleId: Long): List<PlacementEvent> {
        return eventDao.getAllForPuzzle(puzzleId)
    }

    // ---------------------------------------------------------------
    // Replay support
    // ---------------------------------------------------------------

    /**
     * Load a [PuzzleReplayState] for the given completed puzzle.
     *
     * Fetches all events from the database, filters to only correct
     * placements using [PuzzleReplayState.filterCorrectEvents], and
     * builds the replay state object.
     *
     * Returns null if the puzzle doesn't exist.
     */
    suspend fun loadReplayState(puzzleId: Long): PuzzleReplayState? {
        val entity = puzzleDao.getById(puzzleId) ?: return null
        val palette = entity.paletteJson.split(",").map { it.trim().toInt() }
        val targetColors = bytesToIntArray(entity.targetColors)
        val allEvents = eventDao.getAllForPuzzle(puzzleId)

        val correctEvents = PuzzleReplayState.filterCorrectEvents(
            events = allEvents,
            targetColors = targetColors,
            gridSize = entity.gridSize
        )

        return PuzzleReplayState(
            gridSize = entity.gridSize,
            palette = palette,
            targetColors = targetColors,
            correctEvents = correctEvents
        )
    }

    /**
     * Delete a saved puzzle and its events (cascade).
     */
    suspend fun deletePuzzle(id: Long) {
        puzzleDao.deleteById(id)
        if (activePuzzleId == id) activePuzzleId = null
    }

    // ---------------------------------------------------------------
    // ByteArray <-> IntArray helpers (duplicated from Converters for
    // use outside Room's automatic conversion)
    // ---------------------------------------------------------------

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
