package com.colorbynumber.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.colorbynumber.app.engine.PuzzleReplayState
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Playback state machine:
 *   SHOWING_COMPLETE  →  (play)  →  PLAYING  ↔  PAUSED  →  (finished)  →  SHOWING_COMPLETE
 */
enum class ReplayPlaybackState {
    SHOWING_COMPLETE,
    PLAYING,
    PAUSED
}

/**
 * Composable that renders an animated replay of a completed puzzle.
 *
 * Initially shows the fully-colored image. Pressing play clears the grid
 * and animates each correct cell placement. When finished, automatically
 * returns to showing the complete image.
 */
@Composable
fun PuzzleReplayPlayer(
    replayState: PuzzleReplayState,
    modifier: Modifier = Modifier
) {
    var playbackState by remember { mutableStateOf(ReplayPlaybackState.SHOWING_COMPLETE) }
    var currentFrame by remember { mutableIntStateOf(0) }

    // Pre-compute Color objects for palette
    val paletteColors = remember(replayState.palette) {
        replayState.palette.map { rgb ->
            Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
        }
    }
    val greyscaleColors = remember(replayState.palette) {
        replayState.palette.map { rgb ->
            val r = AndroidColor.red(rgb); val g = AndroidColor.green(rgb); val b = AndroidColor.blue(rgb)
            val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            Color(grey, grey, grey)
        }
    }

    // Initialize to showing complete
    LaunchedEffect(Unit) {
        replayState.fillComplete()
    }

    // Frame ticker — runs while PLAYING
    LaunchedEffect(playbackState) {
        if (playbackState == ReplayPlaybackState.PLAYING) {
            while (currentFrame < replayState.totalFrames) {
                delay(16L) // ~60fps
                currentFrame++
                replayState.advanceToFrame(currentFrame)

                if (currentFrame >= replayState.totalFrames) {
                    // Animation finished — return to showing complete
                    replayState.fillComplete()
                    playbackState = ReplayPlaybackState.SHOWING_COMPLETE
                    currentFrame = 0
                }
            }
        }
    }

    // Bitmap cache for rendering (avoids per-pixel drawRect)
    val gridSize = replayState.gridSize
    val renderTrigger = currentFrame // recompose on frame change

    val imageBitmap = remember(renderTrigger, playbackState) {
        val bmp = ImageBitmap(gridSize, gridSize)
        val canvas = Canvas(bmp)
        val paint = Paint()

        val grid = replayState.displayGrid
        val targets = replayState.targetColors
        val isComplete = playbackState == ReplayPlaybackState.SHOWING_COMPLETE

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = row * gridSize + col
                val cellValue = grid[idx]
                val targetIdx = targets[idx]

                paint.color = if (isComplete || (cellValue != -1 && cellValue == targetIdx)) {
                    // Show the correct color
                    if (isComplete) paletteColors[targetIdx] else paletteColors[cellValue]
                } else if (cellValue == -1) {
                    // Empty cell — show greyscale of target
                    greyscaleColors[targetIdx]
                } else {
                    // Shouldn't happen in replay, but fallback to greyscale
                    greyscaleColors[targetIdx]
                }

                canvas.drawRect(
                    androidx.compose.ui.geometry.Rect(
                        col.toFloat(), row.toFloat(), col + 1f, row + 1f
                    ),
                    paint
                )
            }
        }
        bmp
    }

    Box(modifier = modifier) {
        // Grid image
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None
            )
        }

        // Play/Pause controls — overlay at bottom center of the image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause button
                FilledIconButton(
                    onClick = {
                        when (playbackState) {
                            ReplayPlaybackState.SHOWING_COMPLETE -> {
                                // Start replay from scratch
                                replayState.reset()
                                currentFrame = 0
                                playbackState = ReplayPlaybackState.PLAYING
                            }
                            ReplayPlaybackState.PLAYING -> {
                                playbackState = ReplayPlaybackState.PAUSED
                            }
                            ReplayPlaybackState.PAUSED -> {
                                playbackState = ReplayPlaybackState.PLAYING
                            }
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = when (playbackState) {
                            ReplayPlaybackState.PLAYING -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = when (playbackState) {
                            ReplayPlaybackState.PLAYING -> "Pause"
                            else -> "Play"
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Progress indicator during playback
                if (playbackState == ReplayPlaybackState.PLAYING ||
                    playbackState == ReplayPlaybackState.PAUSED
                ) {
                    Spacer(modifier = Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (replayState.totalFrames > 0) {
                                currentFrame.toFloat() / replayState.totalFrames
                            } else 0f
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
