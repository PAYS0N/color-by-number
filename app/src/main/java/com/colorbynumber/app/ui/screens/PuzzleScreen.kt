package com.colorbynumber.app.ui.screens

import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorbynumber.app.AppSettings
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

    // Settings (global — persisted across puzzles)
    var showSettings by remember { mutableStateOf(false) }
    var preventErrors by remember { mutableStateOf(AppSettings.preventErrors) }
    var preventOverwrite by remember { mutableStateOf(AppSettings.preventOverwrite) }
    var vibrateEnabled by remember { mutableStateOf(AppSettings.vibrate) }

    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    // Track completed colors for palette visibility
    var completedColors by remember { mutableStateOf(puzzleState.completedColors()) }
    // Trigger recomposition
    var updateTrigger by remember { mutableIntStateOf(0) }

    // Sync settings to global store and active puzzle
    LaunchedEffect(preventErrors) {
        AppSettings.preventErrors = preventErrors
        puzzleState.preventErrors = preventErrors
    }
    LaunchedEffect(preventOverwrite) {
        AppSettings.preventOverwrite = preventOverwrite
        puzzleState.preventOverwrite = preventOverwrite
    }

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Vibration", modifier = Modifier.weight(1f))
                            Switch(
                                checked = vibrateEnabled,
                                onCheckedChange = {
                                    vibrateEnabled = it
                                    AppSettings.vibrate = it
                                }
                            )
                        }
                        if (false) Button(
                            onClick = {
                                puzzleState.targetColors.copyInto(puzzleState.userColors)
                                completedColors = puzzleState.completedColors()
                                updateTrigger++
                                showSettings = false
                                if (puzzleState.isComplete()) onComplete()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Auto-complete (Debug)")
                        }
                    }
                }
            }

            // Main puzzle grid (zoomable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
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
                                val prevCompletedCount = completedColors.size
                                val changed = puzzleState.colorCell(row, col, ci)
                                if (changed && puzzleState.isCellCorrect(row, col) && vibrateEnabled) {
                                    val newCompleted = puzzleState.completedColors()
                                    if (newCompleted.size > prevCompletedCount) {
                                        vibrateHaptic(vibrator, large = true)
                                    } else {
                                        vibrateHaptic(vibrator, large = false)
                                    }
                                }
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
    val density = LocalDensity.current.density

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Paint mode indicator: screen position of the active touch, null when not painting
    var paintIndicatorPos by remember { mutableStateOf<Offset?>(null) }

    // Pre-computed Color objects to avoid per-frame allocation
    val paletteColors = remember(puzzleState.palette) {
        puzzleState.palette.map { rgb ->
            Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
        }
    }
    val greyscaleColors = remember(puzzleState.palette) {
        puzzleState.palette.map { rgb ->
            val r = AndroidColor.red(rgb); val g = AndroidColor.green(rgb); val b = AndroidColor.blue(rgb)
            val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            Color(grey, grey, grey, 153)
        }
    }
    // Bitmap cache for zoomed-out rendering: (updateTrigger at render time) → ImageBitmap
    val bitmapCache = remember { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
    // Number sprite cache: rebuilt when cellSize changes significantly
    val numberSpritesRef = remember { arrayOfNulls<Map<Int, ImageBitmap>>(1) }
    val spriteCellSizeKey = remember { intArrayOf(-1) }

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
                    val earlyExit = withTimeoutOrNull(200L) {
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
                        paintIndicatorPos = lastKnownPos
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
                                if (!change.pressed) {
                                    paintIndicatorPos = null
                                    break
                                }
                                paintIndicatorPos = change.position
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
                    paintIndicatorPos = null
                }
            }
    ) {
        val canvasSize = min(size.width, size.height)
        val cellSize = (canvasSize / gridSize) * scale
        val gridPixelSize = cellSize * gridSize

        val gridOriginX = (size.width - gridPixelSize) / 2f + offset.x
        val gridOriginY = (size.height - gridPixelSize) / 2f + offset.y

        // Threshold scales linearly with grid size: 1.2 at 20×20, 2.5 at 100×100
        val t = ((gridSize - 20f) / 80f).coerceIn(0f, 1f)
        val showNumbersThreshold = 1.2f + t * 1.3f
        val showNumbers = scale > showNumbersThreshold

        if (!showNumbers) {
            // ── Zoomed-out fast path: single drawImage from cached bitmap ──────────
            val cached = bitmapCache.value
            val bmp: ImageBitmap = if (cached != null && cached.first == updateTrigger) {
                cached.second
            } else {
                val newBmp = ImageBitmap(gridSize, gridSize)
                val bmpCanvas = androidx.compose.ui.graphics.Canvas(newBmp)
                val paint = androidx.compose.ui.graphics.Paint()
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val idx = row * gridSize + col
                        val targetColorIdx = puzzleState.targetColors[idx]
                        val userColorIdx = puzzleState.userColors[idx]
                        paint.color = if (userColorIdx != -1 && userColorIdx == targetColorIdx) {
                            paletteColors[targetColorIdx]
                        } else {
                            greyscaleColors[targetColorIdx]
                        }
                        bmpCanvas.drawRect(
                            androidx.compose.ui.geometry.Rect(col.toFloat(), row.toFloat(), col + 1f, row + 1f),
                            paint
                        )
                    }
                }
                bitmapCache.value = updateTrigger to newBmp
                newBmp
            }
            drawImage(
                image = bmp,
                dstOffset = androidx.compose.ui.unit.IntOffset(gridOriginX.toInt(), gridOriginY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(gridPixelSize.toInt(), gridPixelSize.toInt()),
                filterQuality = FilterQuality.None
            )
        } else {
            // ── Zoomed-in path ────────────────────────────────────────────────────

            // Rebuild number sprites when zoom changes significantly (quantized to 0.5px steps)
            val sizeKey = (cellSize * 2f).toInt()
            if (sizeKey != spriteCellSizeKey[0]) {
                spriteCellSizeKey[0] = sizeKey
                val spriteSize = cellSize.toInt().coerceAtLeast(4)
                val baseFontSize = (cellSize * 0.4f).coerceIn(4f, 24f) * density
                val maxW = spriteSize * 0.85f
                val maxH = spriteSize * 0.80f
                numberSpritesRef[0] = puzzleState.paletteOrder.withIndex().associate { (idx, colorIdx) ->
                    val number = (idx + 1).toString()
                    val bmp = AndroidBitmap.createBitmap(spriteSize, spriteSize, AndroidBitmap.Config.ARGB_8888)
                    val cvs = AndroidCanvas(bmp)
                    // Per-number paint so font size can be scaled down independently
                    val paint = AndroidPaint().apply {
                        textSize = baseFontSize
                        color = AndroidColor.BLACK
                        isAntiAlias = true
                        textAlign = AndroidPaint.Align.CENTER
                    }
                    val textW = paint.measureText(number)
                    val textH = paint.descent() - paint.ascent()
                    val scale = minOf(
                        if (textW > maxW) maxW / textW else 1f,
                        if (textH > maxH) maxH / textH else 1f
                    )
                    if (scale < 1f) paint.textSize = (baseFontSize * scale).coerceAtLeast(4f * density)
                    val yPos = (spriteSize - paint.descent() - paint.ascent()) / 2f
                    cvs.drawText(number, spriteSize / 2f, yPos, paint)
                    colorIdx to bmp.asImageBitmap()
                }
            }
            val numberSprites = numberSpritesRef[0] ?: emptyMap()

            // Compute visible cell range — only iterate on-screen cells
            val firstVisCol = max(0, ((-gridOriginX) / cellSize).toInt())
            val lastVisCol  = min(gridSize - 1, ((size.width - gridOriginX) / cellSize).toInt() + 1)
            val firstVisRow = max(0, ((-gridOriginY) / cellSize).toInt())
            val lastVisRow  = min(gridSize - 1, ((size.height - gridOriginY) / cellSize).toInt() + 1)

            // Pass A: backgrounds, tints, highlights, and number sprites
            for (row in firstVisRow..lastVisRow) {
                for (col in firstVisCol..lastVisCol) {
                    val idx = row * gridSize + col
                    val targetColorIdx = puzzleState.targetColors[idx]
                    val userColorIdx = puzzleState.userColors[idx]

                    val x = kotlin.math.floor(gridOriginX + col * cellSize)
                    val y = kotlin.math.floor(gridOriginY + row * cellSize)
                    val w = kotlin.math.floor(gridOriginX + (col + 1) * cellSize) - x
                    val h = kotlin.math.floor(gridOriginY + (row + 1) * cellSize) - y

                    val isCorrect = userColorIdx != -1 && userColorIdx == targetColorIdx
                    val isIncorrect = userColorIdx != -1 && userColorIdx != targetColorIdx
                    val isHighlighted = selectedColorIndex != null &&
                            targetColorIdx == selectedColorIndex && !isCorrect

                    val cellRect = androidx.compose.ui.geometry.Size(w, h)
                    drawRect(
                        color = if (isCorrect) paletteColors[userColorIdx] else Color.White,
                        topLeft = Offset(x, y), size = cellRect
                    )

                    if (isIncorrect) {
                        val rgb = puzzleState.palette[userColorIdx]
                        drawRect(
                            color = Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb), 124),
                            topLeft = Offset(x, y), size = cellRect
                        )
                    }
                    if (isHighlighted) {
                        drawRect(color = Color(0x40000000), topLeft = Offset(x, y), size = cellRect)
                    }

                    // Number sprite (replaces drawText — pixel blit only, no text layout per frame)
                    if (!isCorrect) {
                        numberSprites[targetColorIdx]?.let { sprite ->
                            drawImage(
                                image = sprite,
                                dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
                                filterQuality = FilterQuality.Low
                            )
                        }
                    }
                }
            }

            // Pass B: grid lines as a single Path (replaces per-cell drawRect Stroke)
            val gridPath = Path()
            for (row in firstVisRow..lastVisRow + 1) {
                val ly = kotlin.math.floor(gridOriginY + row * cellSize)
                gridPath.moveTo(kotlin.math.floor(gridOriginX + firstVisCol * cellSize), ly)
                gridPath.lineTo(kotlin.math.floor(gridOriginX + (lastVisCol + 1) * cellSize), ly)
            }
            for (col in firstVisCol..lastVisCol + 1) {
                val lx = kotlin.math.floor(gridOriginX + col * cellSize)
                gridPath.moveTo(lx, kotlin.math.floor(gridOriginY + firstVisRow * cellSize))
                gridPath.lineTo(lx, kotlin.math.floor(gridOriginY + (lastVisRow + 1) * cellSize))
            }
            drawPath(gridPath, color = Color(0x40000000), style = Stroke(width = 1f))

            // Pass C: re-draw correct cells to cover their grid lines
            for (row in firstVisRow..lastVisRow) {
                for (col in firstVisCol..lastVisCol) {
                    val idx = row * gridSize + col
                    val userColorIdx = puzzleState.userColors[idx]
                    val targetColorIdx = puzzleState.targetColors[idx]
                    if (userColorIdx != -1 && userColorIdx == targetColorIdx) {
                        val x = kotlin.math.floor(gridOriginX + col * cellSize)
                        val y = kotlin.math.floor(gridOriginY + row * cellSize)
                        val w = kotlin.math.floor(gridOriginX + (col + 1) * cellSize) - x
                        val h = kotlin.math.floor(gridOriginY + (row + 1) * cellSize) - y
                        drawRect(paletteColors[userColorIdx], topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
                    }
                }
            }
        }

        // Paint mode touch indicator: concentric rings at the active touch position
        paintIndicatorPos?.let { pos ->
            val ringColor = if (selectedColorIndex != null && !isEraser) {
                val rgb = puzzleState.palette[selectedColorIndex]
                Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
            } else {
                Color.LightGray
            }
            val radius = cellSize * 1.2f
            drawCircle(color = Color.White, radius = radius + 4f, center = pos,
                style = Stroke(width = 6f))
            drawCircle(color = ringColor, radius = radius, center = pos,
                style = Stroke(width = 4f))
            drawCircle(color = Color(0x80000000), radius = radius - 4f, center = pos,
                style = Stroke(width = 2f))
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

private fun vibrateHaptic(vibrator: Vibrator?, large: Boolean) {
    vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(if (large) 60L else 30L, if (large) 100 else 30))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(if (large) 120L else 30L)
    }
}

private fun isColorLight(rgb: Int): Boolean {
    val r = AndroidColor.red(rgb)
    val g = AndroidColor.green(rgb)
    val b = AndroidColor.blue(rgb)
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 128
}
