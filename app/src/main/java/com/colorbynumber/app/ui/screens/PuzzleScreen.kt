package com.colorbynumber.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorbynumber.app.engine.PuzzleState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    puzzleState: PuzzleState,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    // Tool state: selected palette index or -1 for eraser, null for nothing
    var selectedColorIndex by remember { mutableStateOf<Int?>(null) }
    var isEraser by remember { mutableStateOf(false) }

    // Settings
    var showSettings by remember { mutableStateOf(false) }
    var preventErrors by remember { mutableStateOf(puzzleState.preventErrors) }
    var preventOverwrite by remember { mutableStateOf(puzzleState.preventOverwrite) }

    // Track completed colors for palette visibility
    var completedColors by remember { mutableStateOf(puzzleState.completedColors()) }
    // Trigger recomposition
    var updateTrigger by remember { mutableIntStateOf(0) }

    // Sync settings
    LaunchedEffect(preventErrors) { puzzleState.preventErrors = preventErrors }
    LaunchedEffect(preventOverwrite) { puzzleState.preventOverwrite = preventOverwrite }

    // Build visible palette (exclude completed colors)
    val visiblePalette = remember(completedColors, updateTrigger) {
        puzzleState.paletteOrder.filter { it !in completedColors }
    }

    // Map colorIdx -> 1-based display number by paletteOrder position
    val colorDisplayNumbers = remember {
        puzzleState.paletteOrder.withIndex().associate { (i, ci) -> ci to (i + 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puzzle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Settings panel (collapsible)
            if (showSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Prevent Errors", modifier = Modifier.weight(1f))
                            Switch(
                                checked = preventErrors,
                                onCheckedChange = { preventErrors = it }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Prevent Overwrite", modifier = Modifier.weight(1f))
                            Switch(
                                checked = preventOverwrite,
                                onCheckedChange = { preventOverwrite = it }
                            )
                        }
                    }
                }
            }

            // Main puzzle grid (zoomable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PuzzleGrid(
                    puzzleState = puzzleState,
                    selectedColorIndex = if (isEraser) null else selectedColorIndex,
                    isEraser = isEraser,
                    updateTrigger = updateTrigger,
                    colorDisplayNumbers = colorDisplayNumbers,
                    onCellTap = { row, col ->
                        if (isEraser) {
                            puzzleState.eraseCell(row, col)
                        } else {
                            selectedColorIndex?.let { ci ->
                                puzzleState.colorCell(row, col, ci)
                            }
                        }
                        completedColors = puzzleState.completedColors()
                        updateTrigger++
                        if (puzzleState.isComplete()) {
                            onComplete()
                        }
                    }
                )
            }

            // Palette bar
            PaletteBar(
                puzzleState = puzzleState,
                visiblePalette = visiblePalette,
                selectedColorIndex = selectedColorIndex,
                isEraser = isEraser,
                colorDisplayNumbers = colorDisplayNumbers,
                onSelectColor = { ci ->
                    selectedColorIndex = ci
                    isEraser = false
                },
                onSelectEraser = {
                    isEraser = true
                    selectedColorIndex = null
                }
            )
        }
    }
}

@Composable
private fun PuzzleGrid(
    puzzleState: PuzzleState,
    selectedColorIndex: Int?,
    isEraser: Boolean,
    updateTrigger: Int,
    colorDisplayNumbers: Map<Int, Int>,
    onCellTap: (row: Int, col: Int) -> Unit
) {
    val gridSize = puzzleState.gridSize
    val textMeasurer = rememberTextMeasurer()

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Always use the latest callback without restarting the gesture coroutine
    val currentOnCellTap by rememberUpdatedState(onCellTap)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastKnownPos = down.position
                    var mode = GestureMode.UNDECIDED
                    var lastCell: Pair<Int, Int>? = null

                    // Phase 1: determine gesture type within 500ms
                    // withTimeoutOrNull returns null on timeout (= long press), Unit on early exit
                    val earlyExit = withTimeoutOrNull(500L) {
                        while (true) {
                            val event = awaitPointerEvent()

                            if (event.changes.count { it.pressed } >= 2) {
                                mode = GestureMode.ZOOMING
                                break
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastKnownPos = change.position

                            if (!change.pressed) {
                                // Finger lifted before timeout = tap
                                break
                            }

                            val dist = (change.position - down.position).getDistance()
                            if (dist > viewConfiguration.touchSlop) {
                                mode = GestureMode.PANNING
                                break
                            }
                        }
                        Unit
                    }

                    // null return = timeout = long press
                    if (earlyExit == null) {
                        mode = GestureMode.PAINTING
                        val (row, col) = screenToCell(lastKnownPos, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
                        if (row in 0 until gridSize && col in 0 until gridSize) {
                            currentOnCellTap(row, col)
                            lastCell = row to col
                        }
                    }

                    // Quick tap: finger lifted during phase 1
                    if (mode == GestureMode.UNDECIDED) {
                        val (row, col) = screenToCell(down.position, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
                        if (row in 0 until gridSize && col in 0 until gridSize) {
                            currentOnCellTap(row, col)
                        }
                        return@awaitEachGesture
                    }

                    // Phase 2: handle active mode until all fingers lift
                    while (true) {
                        val event = awaitPointerEvent()

                        if (event.changes.count { it.pressed } >= 2 && mode != GestureMode.ZOOMING) {
                            mode = GestureMode.ZOOMING
                        }

                        when (mode) {
                            GestureMode.ZOOMING -> {
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val zoomFactor = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    val centroidPrev = pressed
                                        .map { it.previousPosition }
                                        .fold(Offset.Zero) { acc, p -> acc + p } / pressed.size.toFloat()
                                    val newScale = (scale * zoomFactor).coerceIn(0.5f, 10f)
                                    val actualZoom = newScale / scale
                                    val rawOffset = Offset(
                                        x = (1 - actualZoom) * (centroidPrev.x - size.width / 2f) + panDelta.x + actualZoom * offset.x,
                                        y = (1 - actualZoom) * (centroidPrev.y - size.height / 2f) + panDelta.y + actualZoom * offset.y
                                    )
                                    scale = newScale
                                    offset = clampOffset(rawOffset, newScale, size.width.toFloat(), size.height.toFloat())
                                }
                                event.changes.forEach { it.consume() }
                            }
                            GestureMode.PANNING -> {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.position - change.previousPosition
                                offset = clampOffset(Offset(offset.x + delta.x, offset.y + delta.y), scale, size.width.toFloat(), size.height.toFloat())
                                change.consume()
                            }
                            GestureMode.PAINTING -> {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val (row, col) = screenToCell(change.position, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
                                val cell = row to col
                                if (row in 0 until gridSize && col in 0 until gridSize && cell != lastCell) {
                                    currentOnCellTap(row, col)
                                    lastCell = cell
                                }
                                change.consume()
                            }
                            else -> break
                        }

                        if (!event.changes.any { it.pressed }) break
                    }
                }
            }
    ) {
        val canvasSize = min(size.width, size.height)
        val cellSize = (canvasSize / gridSize) * scale
        val gridPixelSize = cellSize * gridSize

        val gridOriginX = (size.width - gridPixelSize) / 2f + offset.x
        val gridOriginY = (size.height - gridPixelSize) / 2f + offset.y

        // Determine if we should show numbers (based on zoom level)
        val showNumbers = scale > 1.5f

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = row * gridSize + col
                val targetColorIdx = puzzleState.targetColors[idx]
                val userColorIdx = puzzleState.userColors[idx]
                val targetRgb = puzzleState.palette[targetColorIdx]

                // Snap to pixel boundaries to eliminate sub-pixel gaps between cells
                val x = kotlin.math.floor(gridOriginX + col * cellSize)
                val y = kotlin.math.floor(gridOriginY + row * cellSize)
                val x2 = kotlin.math.floor(gridOriginX + (col + 1) * cellSize)
                val y2 = kotlin.math.floor(gridOriginY + (row + 1) * cellSize)
                val w = x2 - x
                val h = y2 - y

                // Skip cells completely outside the visible area
                if (x2 < 0 || x > size.width || y2 < 0 || y > size.height) continue

                val isCorrect = userColorIdx != -1 && userColorIdx == targetColorIdx
                val isIncorrect = userColorIdx != -1 && userColorIdx != targetColorIdx

                // Determine cell background color
                val cellColor: Color = when {
                    isCorrect -> {
                        val rgb = puzzleState.palette[userColorIdx]
                        Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
                    }
                    showNumbers -> Color.White
                    else -> {
                        // Zoomed out: greyscale
                        val r = AndroidColor.red(targetRgb)
                        val g = AndroidColor.green(targetRgb)
                        val b = AndroidColor.blue(targetRgb)
                        val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
                        Color(grey, grey, grey)
                    }
                }

                // Highlight cells matching selected color (uncolored or incorrectly colored)
                val isHighlighted = selectedColorIndex != null &&
                        targetColorIdx == selectedColorIndex && !isCorrect

                val cellRect = androidx.compose.ui.geometry.Size(w, h)

                // Draw cell background
                drawRect(color = cellColor, topLeft = Offset(x, y), size = cellRect)

                // Incorrect color: faint tint over white
                if (isIncorrect) {
                    val rgb = puzzleState.palette[userColorIdx]
                    drawRect(
                        color = Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb), 124),
                        topLeft = Offset(x, y),
                        size = cellRect
                    )
                }

                // Highlight overlay (only when zoomed in enough to show numbers)
                if (isHighlighted && showNumbers) {
                    drawRect(color = Color(0x40000000), topLeft = Offset(x, y), size = cellRect)
                }

                // Grid lines (only for uncolored or incorrect cells)
                if (!isCorrect) {
                    drawRect(
                        color = Color(0x30000000),
                        topLeft = Offset(x, y),
                        size = cellRect,
                        style = Stroke(width = 0.5f)
                    )
                }

                // Draw number if zoomed in and cell is not correctly colored
                if (showNumbers && !isCorrect) {
                    val number = (colorDisplayNumbers[targetColorIdx] ?: (targetColorIdx + 1)).toString()
                    val fontSize = (cellSize * 0.4f).coerceIn(4f, 24f)
                    val textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        color = Color.Black
                    )
                    val textLayout = textMeasurer.measure(number, textStyle)
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            x + (w - textLayout.size.width) / 2f,
                            y + (h - textLayout.size.height) / 2f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteBar(
    puzzleState: PuzzleState,
    visiblePalette: List<Int>,
    selectedColorIndex: Int?,
    isEraser: Boolean,
    colorDisplayNumbers: Map<Int, Int>,
    onSelectColor: (Int) -> Unit,
    onSelectEraser: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Eraser button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isEraser) 3.dp else 1.dp,
                        color = if (isEraser) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onSelectEraser() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eraser",
                    tint = if (isEraser) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Scrollable color palette
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visiblePalette) { colorIdx ->
                    val rgb = puzzleState.palette[colorIdx]
                    val color = Color(
                        AndroidColor.red(rgb),
                        AndroidColor.green(rgb),
                        AndroidColor.blue(rgb)
                    )
                    val isSelected = selectedColorIndex == colorIdx && !isEraser
                    val remaining = puzzleState.remainingForColor(colorIdx)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectColor(colorIdx) },
                            contentAlignment = Alignment.Center
                        ) {
                            // Show the palette number
                            Text(
                                text = "${colorDisplayNumbers[colorIdx] ?: (colorIdx + 1)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isColorLight(rgb)) Color.Black else Color.White
                            )
                        }
                        // Remaining count
                        Text(
                            text = "$remaining",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private enum class GestureMode { UNDECIDED, PANNING, PAINTING, ZOOMING }

/**
 * Clamps [offset] so that the screen center always lies inside the grid.
 * Grid pixel size = min(canvasWidth, canvasHeight) * scale, centered by default.
 * The constraint is offset ∈ [-gridPixelSize/2, gridPixelSize/2] on each axis.
 */
private fun clampOffset(offset: Offset, scale: Float, canvasWidth: Float, canvasHeight: Float): Offset {
    val gridPixelSize = min(canvasWidth, canvasHeight) * scale
    val max = gridPixelSize / 2f
    return Offset(offset.x.coerceIn(-max, max), offset.y.coerceIn(-max, max))
}

private fun screenToCell(
    pos: Offset, canvasWidth: Float, canvasHeight: Float,
    gridSize: Int, scale: Float, offset: Offset
): Pair<Int, Int> {
    val canvasSize = min(canvasWidth, canvasHeight)
    val cellSize = (canvasSize / gridSize) * scale
    val gridPixelSize = cellSize * gridSize
    val gridOriginX = (canvasWidth - gridPixelSize) / 2f + offset.x
    val gridOriginY = (canvasHeight - gridPixelSize) / 2f + offset.y
    val col = ((pos.x - gridOriginX) / cellSize).toInt()
    val row = ((pos.y - gridOriginY) / cellSize).toInt()
    return row to col
}

private fun isColorLight(rgb: Int): Boolean {
    val r = AndroidColor.red(rgb)
    val g = AndroidColor.green(rgb)
    val b = AndroidColor.blue(rgb)
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 128
}
