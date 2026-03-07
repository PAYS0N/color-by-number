package com.colorbynumber.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorbynumber.app.engine.PixelArtState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelArtScreen(
    state: PixelArtState,
    savedId: Long?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onSaveAndBack: () -> Unit
) {
    var updateTrigger by remember { mutableIntStateOf(0) }
    var showColorPicker by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var showBackConfirmDialog by remember { mutableStateOf(false) }

    // Track recent colors as snapshot for recomposition
    var recentColorsSnapshot by remember { mutableStateOf(state.recentColors.toList()) }
    var selectedColor by remember { mutableStateOf(state.selectedColor) }
    var isEraser by remember { mutableStateOf(state.isEraser) }

    // Intercept back when dirty
    BackHandler(enabled = isDirty) {
        showBackConfirmDialog = true
    }

    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("Save artwork?") },
            text = { Text("You have unsaved changes. Save your pixel art before leaving?") },
            confirmButton = {
                Button(onClick = {
                    showBackConfirmDialog = false
                    onSaveAndBack()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showBackConfirmDialog = false
                        onBack()
                    }) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showBackConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixel Art") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showBackConfirmDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isDirty = false
                            onSave()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
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
            // Grid canvas
            PixelArtGrid(
                state = state,
                updateTrigger = updateTrigger,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onCellTap = { row, col ->
                    if (isEraser) {
                        if (state.eraseCell(row, col)) {
                            isDirty = true
                            updateTrigger++
                        }
                    } else {
                        val color = selectedColor
                        if (color != null && state.colorCell(row, col, color)) {
                            isDirty = true
                            updateTrigger++
                        }
                    }
                }
            )

            // Palette bar
            PixelArtPaletteBar(
                recentColors = recentColorsSnapshot,
                selectedColor = selectedColor,
                isEraser = isEraser,
                onSelectColor = { argb ->
                    state.selectColor(argb)
                    selectedColor = argb
                    isEraser = false
                    recentColorsSnapshot = state.recentColors.toList()
                },
                onSelectEraser = {
                    state.selectEraser()
                    isEraser = true
                    selectedColor = null
                },
                onOpenColorPicker = { showColorPicker = true }
            )
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            initialColor = selectedColor,
            onColorSelected = { argb ->
                showColorPicker = false
                state.selectColor(argb)
                selectedColor = argb
                isEraser = false
                recentColorsSnapshot = state.recentColors.toList()
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Grid Canvas
// ---------------------------------------------------------------------------

@Composable
private fun PixelArtGrid(
    state: PixelArtState,
    updateTrigger: Int,
    modifier: Modifier = Modifier,
    onCellTap: (row: Int, col: Int) -> Unit
) {
    val gridSize = state.gridSize

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var paintIndicatorPos by remember { mutableStateOf<Offset?>(null) }

    val currentOnCellTap by rememberUpdatedState(onCellTap)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastKnownPos = down.position
                    var mode = PixelArtGestureMode.UNDECIDED
                    var lastCell: Pair<Int, Int>? = null

                    val earlyExit = withTimeoutOrNull(150L) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                mode = PixelArtGestureMode.ZOOMING
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastKnownPos = change.position
                            if (!change.pressed) break
                            val dist = (change.position - down.position).getDistance()
                            if (dist > viewConfiguration.touchSlop) {
                                mode = PixelArtGestureMode.PANNING
                                break
                            }
                        }
                        Unit
                    }

                    if (earlyExit == null) {
                        mode = PixelArtGestureMode.PAINTING
                        paintIndicatorPos = lastKnownPos
                        val initialCell = pixelArtScreenToCell(lastKnownPos, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)

                        var movedDuringSettle = false
                        var liftedDuringSettle = false
                        withTimeoutOrNull(50L) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    paintIndicatorPos = null
                                    liftedDuringSettle = true
                                    break
                                }
                                paintIndicatorPos = change.position
                                lastKnownPos = change.position
                                val (r, c) = pixelArtScreenToCell(change.position, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
                                if ((r to c) != initialCell) {
                                    movedDuringSettle = true
                                    val (ir, ic) = initialCell
                                    if (ir in 0 until gridSize && ic in 0 until gridSize) {
                                        currentOnCellTap(ir, ic)
                                        lastCell = initialCell
                                    }
                                    if (r in 0 until gridSize && c in 0 until gridSize) {
                                        currentOnCellTap(r, c)
                                        lastCell = r to c
                                    }
                                    break
                                }
                            }
                        }
                        if (liftedDuringSettle) {
                            val (r, c) = initialCell
                            if (r in 0 until gridSize && c in 0 until gridSize) {
                                currentOnCellTap(r, c)
                            }
                            return@awaitEachGesture
                        }

                        if (!movedDuringSettle) {
                            val (r, c) = initialCell
                            if (r in 0 until gridSize && c in 0 until gridSize) {
                                currentOnCellTap(r, c)
                                lastCell = initialCell
                            }
                        }
                    }

                    if (mode == PixelArtGestureMode.UNDECIDED) {
                        val (row, col) = pixelArtScreenToCell(down.position, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
                        if (row in 0 until gridSize && col in 0 until gridSize) {
                            currentOnCellTap(row, col)
                        }
                        return@awaitEachGesture
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 && mode != PixelArtGestureMode.ZOOMING) {
                            mode = PixelArtGestureMode.ZOOMING
                        }

                        when (mode) {
                            PixelArtGestureMode.ZOOMING -> {
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
                                    offset = pixelArtClampOffset(rawOffset, newScale, size.width.toFloat(), size.height.toFloat())
                                }
                                event.changes.forEach { it.consume() }
                            }
                            PixelArtGestureMode.PANNING -> {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.position - change.previousPosition
                                offset = pixelArtClampOffset(Offset(offset.x + delta.x, offset.y + delta.y), scale, size.width.toFloat(), size.height.toFloat())
                                change.consume()
                            }
                            PixelArtGestureMode.PAINTING -> {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    paintIndicatorPos = null
                                    break
                                }
                                paintIndicatorPos = change.position
                                val (row, col) = pixelArtScreenToCell(change.position, size.width.toFloat(), size.height.toFloat(), gridSize, scale, offset)
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

        val firstVisCol = max(0, ((-gridOriginX) / cellSize).toInt())
        val lastVisCol = min(gridSize - 1, ((size.width - gridOriginX) / cellSize).toInt() + 1)
        val firstVisRow = max(0, ((-gridOriginY) / cellSize).toInt())
        val lastVisRow = min(gridSize - 1, ((size.height - gridOriginY) / cellSize).toInt() + 1)

        // Cell backgrounds
        for (row in firstVisRow..lastVisRow) {
            for (col in firstVisCol..lastVisCol) {
                val idx = row * gridSize + col
                val argb = state.cells[idx]
                val x = kotlin.math.floor(gridOriginX + col * cellSize)
                val y = kotlin.math.floor(gridOriginY + row * cellSize)
                val w = kotlin.math.floor(gridOriginX + (col + 1) * cellSize) - x
                val h = kotlin.math.floor(gridOriginY + (row + 1) * cellSize) - y

                drawRect(
                    color = if (argb != 0) Color(argb) else Color.White,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w, h)
                )
            }
        }

        // Grid lines
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
        drawPath(gridPath, color = Color(0x40000000), style = Stroke(width = 1.5f))

        // Re-draw filled cells to cover grid lines
        for (row in firstVisRow..lastVisRow) {
            for (col in firstVisCol..lastVisCol) {
                val idx = row * gridSize + col
                val argb = state.cells[idx]
                if (argb != 0) {
                    val x = kotlin.math.floor(gridOriginX + col * cellSize)
                    val y = kotlin.math.floor(gridOriginY + row * cellSize)
                    val w = kotlin.math.floor(gridOriginX + (col + 1) * cellSize) - x
                    val h = kotlin.math.floor(gridOriginY + (row + 1) * cellSize) - y
                    drawRect(Color(argb), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
                }
            }
        }

        // Paint indicator
        paintIndicatorPos?.let { pos ->
            val ringColor = if (state.selectedColor != null && !state.isEraser) {
                Color(state.selectedColor!!)
            } else {
                Color.LightGray
            }
            val radius = cellSize * 1.2f
            drawCircle(color = Color.White, radius = radius + 4f, center = pos, style = Stroke(width = 6f))
            drawCircle(color = ringColor, radius = radius, center = pos, style = Stroke(width = 4f))
            drawCircle(color = Color(0x80000000), radius = radius - 4f, center = pos, style = Stroke(width = 2f))
        }
    }
}

// ---------------------------------------------------------------------------
// Palette Bar
// ---------------------------------------------------------------------------

@Composable
private fun PixelArtPaletteBar(
    recentColors: List<Int>,
    selectedColor: Int?,
    isEraser: Boolean,
    onSelectColor: (Int) -> Unit,
    onSelectEraser: () -> Unit,
    onOpenColorPicker: () -> Unit
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

            Spacer(modifier = Modifier.width(6.dp))

            // Divider between eraser and palette
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Color picker button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { onOpenColorPicker() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Pick Color",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Recent colors
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(recentColors) { argb ->
                    val isSelected = selectedColor == argb && !isEraser
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(argb))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectColor(argb) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers (duplicated from PuzzleScreen to avoid coupling)
// ---------------------------------------------------------------------------

private enum class PixelArtGestureMode { UNDECIDED, PANNING, PAINTING, ZOOMING }

private fun pixelArtClampOffset(offset: Offset, scale: Float, canvasWidth: Float, canvasHeight: Float): Offset {
    val gridPixelSize = min(canvasWidth, canvasHeight) * scale
    val max = gridPixelSize / 2f
    return Offset(offset.x.coerceIn(-max, max), offset.y.coerceIn(-max, max))
}

private fun pixelArtScreenToCell(
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
