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
    onCellTap: (row: Int, col: Int) -> Unit
) {
    val gridSize = puzzleState.gridSize
    val textMeasurer = rememberTextMeasurer()

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 10f)
                    scale = newScale
                    offset = Offset(
                        x = offset.x + pan.x,
                        y = offset.y + pan.y
                    )
                }
            }
            .pointerInput(updateTrigger, selectedColorIndex, isEraser) {
                detectTapGestures { tapOffset ->
                    // Convert screen coordinates to grid coordinates
                    val canvasSize = min(size.width.toFloat(), size.height.toFloat())
                    val cellSize = (canvasSize / gridSize) * scale
                    val gridPixelSize = cellSize * gridSize

                    val gridOriginX = (size.width - gridPixelSize) / 2f + offset.x
                    val gridOriginY = (size.height - gridPixelSize) / 2f + offset.y

                    val col = ((tapOffset.x - gridOriginX) / cellSize).toInt()
                    val row = ((tapOffset.y - gridOriginY) / cellSize).toInt()

                    if (row in 0 until gridSize && col in 0 until gridSize) {
                        onCellTap(row, col)
                    }
                }
            }
    ) {
        val canvasSize = min(size.width, size.height)
        val cellSize = (canvasSize / gridSize) * scale
        val gridPixelSize = cellSize * gridSize

        val gridOriginX = (size.width - gridPixelSize) / 2f + offset.x
        val gridOriginY = (size.height - gridPixelSize) / 2f + offset.y

        // Determine if we should show numbers (based on zoom level / cell size)
        val showNumbers = cellSize > 16f

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = row * gridSize + col
                val targetColorIdx = puzzleState.targetColors[idx]
                val userColorIdx = puzzleState.userColors[idx]
                val targetRgb = puzzleState.palette[targetColorIdx]

                val x = gridOriginX + col * cellSize
                val y = gridOriginY + row * cellSize

                // Skip cells completely outside the visible area
                if (x + cellSize < 0 || x > size.width || y + cellSize < 0 || y > size.height) continue

                // Determine cell color
                val cellColor: Color = if (userColorIdx != -1) {
                    // User has colored this cell - show the actual color
                    val rgb = puzzleState.palette[userColorIdx]
                    Color(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
                } else {
                    // Uncolored: show greyscale
                    val r = AndroidColor.red(targetRgb)
                    val g = AndroidColor.green(targetRgb)
                    val b = AndroidColor.blue(targetRgb)
                    val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
                    Color(grey, grey, grey)
                }

                // Highlight cells matching selected color
                val isHighlighted = selectedColorIndex != null &&
                        targetColorIdx == selectedColorIndex && userColorIdx == -1

                // Draw cell background
                drawRect(
                    color = cellColor,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )

                // Highlight overlay
                if (isHighlighted) {
                    drawRect(
                        color = Color(0x40000000),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }

                // Grid lines
                drawRect(
                    color = Color(0x30000000),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                    style = Stroke(width = 0.5f)
                )

                // Draw number if zoomed in enough and cell is uncolored
                if (showNumbers && userColorIdx == -1) {
                    val number = (targetColorIdx + 1).toString()
                    val fontSize = (cellSize * 0.4f).coerceIn(4f, 24f)
                    val textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHighlighted) Color(0xFF333333) else Color(0xFF666666)
                    )
                    val textLayout = textMeasurer.measure(number, textStyle)
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            x + (cellSize - textLayout.size.width) / 2f,
                            y + (cellSize - textLayout.size.height) / 2f
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
                                text = "${colorIdx + 1}",
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

private fun isColorLight(rgb: Int): Boolean {
    val r = AndroidColor.red(rgb)
    val g = AndroidColor.green(rgb)
    val b = AndroidColor.blue(rgb)
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 128
}
