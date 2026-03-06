package com.colorbynumber.app.engine

import com.colorbynumber.app.data.PlacementEvent
import com.colorbynumber.app.data.PlacementEventType

/**
 * Manages replay of a completed puzzle's placement history.
 *
 * Holds a filtered, sorted list of only-correct placement events and
 * exposes frame-based advancement for smooth animated playback.
 *
 * Duration scales with grid size:
 *   20×20 → 10 seconds, 100×100 → 30 seconds (linear interpolation).
 */
class PuzzleReplayState(
    val gridSize: Int,
    val palette: List<Int>,
    val targetColors: IntArray,
    correctEvents: List<PlacementEvent>
) {
    /** Sorted correct-placement events to replay. */
    val events: List<PlacementEvent> = correctEvents.sortedBy { it.timestamp }

    /** Current grid state for rendering. -1 = empty. */
    val displayGrid: IntArray = IntArray(gridSize * gridSize) { -1 }

    /** How many events have been applied so far (0 = empty grid). */
    var appliedCount: Int = 0
        private set

    /** Total replay duration in seconds, based on grid size. */
    val durationSeconds: Float = run {
        val t = ((gridSize - 20f) / 80f).coerceIn(0f, 1f)
        10f + 20f * t
    }

    /** Total number of frames at 60fps. */
    val totalFrames: Int = (durationSeconds * 60f).toInt().coerceAtLeast(1)

    /** Total events to replay. */
    val totalEvents: Int get() = events.size

    /**
     * Advance the replay to match the given frame number (0-based).
     * Applies the correct number of events proportionally.
     */
    fun advanceToFrame(frame: Int) {
        if (events.isEmpty()) return

        val clampedFrame = frame.coerceIn(0, totalFrames)
        // Target event count for this frame (proportional)
        val targetCount = if (clampedFrame >= totalFrames) {
            events.size
        } else {
            ((clampedFrame.toLong() * events.size) / totalFrames).toInt()
        }

        // Only go forward — replay is append-only during playback
        while (appliedCount < targetCount && appliedCount < events.size) {
            val event = events[appliedCount]
            val idx = event.row * gridSize + event.col
            if (idx in displayGrid.indices) {
                displayGrid[idx] = event.colorIndex
            }
            appliedCount++
        }
    }

    /**
     * Fill the grid completely with correct colors (for SHOWING_COMPLETE state).
     */
    fun fillComplete() {
        targetColors.copyInto(displayGrid)
        appliedCount = events.size
    }

    /**
     * Reset the grid to empty (for starting playback).
     */
    fun reset() {
        displayGrid.fill(-1)
        appliedCount = 0
    }

    val isFullyApplied: Boolean get() = appliedCount >= events.size

    companion object {
        /**
         * Filter raw placement events down to only correct fills.
         * A correct fill is a PLACE event where colorIndex matches
         * the target color for that cell.
         *
         * If the same cell was correctly filled multiple times (e.g.
         * erased then re-filled), only the first correct fill is kept.
         */
        fun filterCorrectEvents(
            events: List<PlacementEvent>,
            targetColors: IntArray,
            gridSize: Int
        ): List<PlacementEvent> {
            val seen = mutableSetOf<Int>() // cell indices already recorded
            val result = mutableListOf<PlacementEvent>()

            for (event in events.sortedBy { it.timestamp }) {
                if (event.eventType != PlacementEventType.PLACE) continue
                val cellIdx = event.row * gridSize + event.col
                if (cellIdx !in targetColors.indices) continue
                if (event.colorIndex != targetColors[cellIdx]) continue
                if (cellIdx in seen) continue

                seen.add(cellIdx)
                result.add(event)
            }
            return result
        }
    }
}
