package com.colorbynumber.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.colorbynumber.app.data.PuzzleRepository
import com.colorbynumber.app.data.PuzzleStatus
import com.colorbynumber.app.data.SavedPuzzle
import com.colorbynumber.app.engine.PuzzleReplayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: PuzzleRepository,
    onResumePuzzle: (Long) -> Unit,
    onBack: () -> Unit,
    autoOpenFirst: Boolean = false,
    onAutoOpenConsumed: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    // HistoryScreen owns its own data — loads fresh from DB every time
    var puzzles by remember { mutableStateOf<List<SavedPuzzle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Reload trigger — increment to force a refresh
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) { repository.getAll() }
        puzzles = loaded
        isLoading = false
    }

    // Dialog state
    var showCompletedDialog by remember { mutableStateOf<SavedPuzzle?>(null) }
    var showInProgressDialog by remember { mutableStateOf<SavedPuzzle?>(null) }

    // Auto-open the most recent completed puzzle's replay (one-shot)
    LaunchedEffect(puzzles) {
        if (autoOpenFirst && puzzles.isNotEmpty()) {
            puzzles.firstOrNull { it.status == PuzzleStatus.COMPLETED }
                ?.let {
                    onAutoOpenConsumed()
                    showCompletedDialog = it
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Puzzles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            puzzles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No puzzles yet.\nTake a photo or pick from your gallery to get started!",
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(puzzles, key = { it.id }) { puzzle ->
                        PuzzleCard(
                            puzzle = puzzle,
                            onClick = {
                                if (puzzle.status == PuzzleStatus.COMPLETED) {
                                    showCompletedDialog = puzzle
                                } else {
                                    showInProgressDialog = puzzle
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Completed puzzle dialog — replay video
    showCompletedDialog?.let { puzzle ->
        CompletedPuzzleDialog(
            puzzle = puzzle,
            repository = repository,
            onDelete = {
                showCompletedDialog = null
                val id = puzzle.id
                coroutineScope.launch {
                    withContext(Dispatchers.IO) { repository.deletePuzzle(id) }
                    reloadTrigger++
                }
            },
            onDismiss = { showCompletedDialog = null }
        )
    }

    // In-progress puzzle dialog — resume or delete
    showInProgressDialog?.let { puzzle ->
        InProgressPuzzleDialog(
            puzzle = puzzle,
            onResume = {
                showInProgressDialog = null
                onResumePuzzle(puzzle.id)
            },
            onDelete = {
                showInProgressDialog = null
                val id = puzzle.id
                coroutineScope.launch {
                    withContext(Dispatchers.IO) { repository.deletePuzzle(id) }
                    reloadTrigger++
                }
            },
            onDismiss = { showInProgressDialog = null }
        )
    }
}

@Composable
private fun PuzzleCard(
    puzzle: SavedPuzzle,
    onClick: () -> Unit
) {
    val thumbnail = remember(puzzle.id, puzzle.updatedAt) {
        buildThumbnail(puzzle)
    }

    val progress = remember(puzzle.id, puzzle.updatedAt) {
        computeProgress(puzzle)
    }

    val dateStr = remember(puzzle.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(puzzle.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Thumbnail image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                ) {
                    thumbnail?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Puzzle thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Status badge
                    val isCompleted = puzzle.status == PuzzleStatus.COMPLETED
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = if (isCompleted) Color(0xFF4CAF50) else Color(0xFFFFA726),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isCompleted) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Info row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${puzzle.gridSize}×${puzzle.gridSize}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedPuzzleDialog(
    puzzle: SavedPuzzle,
    repository: PuzzleRepository,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    // Load replay state asynchronously
    var replayState by remember { mutableStateOf<PuzzleReplayState?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(puzzle.id) {
        val state = withContext(Dispatchers.IO) {
            repository.loadReplayState(puzzle.id)
        }
        if (state != null) {
            state.fillComplete()
            replayState = state
        } else {
            loadError = true
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete puzzle?") },
            text = { Text("This will permanently delete the puzzle and replay data. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Top row: delete + save + close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            saveStatus?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        buildFullColorBitmap(puzzle)
                                    }
                                    if (bitmap != null) {
                                        val ok = withContext(Dispatchers.IO) {
                                            saveImageToGallery(context, bitmap)
                                        }
                                        saveStatus = if (ok) "Saved!" else "Failed"
                                    } else {
                                        saveStatus = "Failed"
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Save image",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    val state = replayState
                    if (state != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = false, onClick = {})
                        ) {
                            PuzzleReplayPlayer(
                                replayState = state,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (loadError) {
                        Text(
                            text = "Could not load replay data.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    } else {
                        // Loading
                        CircularProgressIndicator(color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap outside to close",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InProgressPuzzleDialog(
    puzzle: SavedPuzzle,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val thumbnail = remember(puzzle.id) {
        buildThumbnail(puzzle)
    }

    val progress = remember(puzzle.id) {
        computeProgress(puzzle)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete puzzle?") },
            text = { Text("This will permanently delete the puzzle and all progress. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("${puzzle.gridSize}×${puzzle.gridSize} Puzzle")
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    thumbnail?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Puzzle preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}% complete",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(onClick = onResume) {
                    Text("Resume")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

private fun buildThumbnail(puzzle: SavedPuzzle): Bitmap? {
    return try {
        val size = puzzle.gridSize
        val palette = puzzle.paletteJson.split(",").map { it.trim().toInt() }
        val targetColors = bytesToIntArray(puzzle.targetColors)
        val userColors = bytesToIntArray(puzzle.userColors)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val idx = row * size + col
                val targetIdx = targetColors[idx]
                val userIdx = userColors[idx]
                val targetRgb = palette[targetIdx]

                val pixel = if (puzzle.status == PuzzleStatus.COMPLETED) {
                    targetRgb or (0xFF shl 24)
                } else if (userIdx != -1 && userIdx == targetIdx) {
                    palette[userIdx] or (0xFF shl 24)
                } else {
                    val r = AndroidColor.red(targetRgb)
                    val g = AndroidColor.green(targetRgb)
                    val b = AndroidColor.blue(targetRgb)
                    val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
                    val lightened = (grey * 0.6 + 255 * 0.4).toInt().coerceIn(0, 255)
                    AndroidColor.rgb(lightened, lightened, lightened)
                }
                bitmap.setPixel(col, row, pixel)
            }
        }
        Bitmap.createScaledBitmap(bitmap, 400, 400, false)
    } catch (e: Exception) {
        null
    }
}

private fun buildFullColorBitmap(puzzle: SavedPuzzle): Bitmap? {
    return try {
        val size = puzzle.gridSize
        val palette = puzzle.paletteJson.split(",").map { it.trim().toInt() }
        val targetColors = bytesToIntArray(puzzle.targetColors)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val idx = row * size + col
                val colorIdx = targetColors[idx]
                bitmap.setPixel(col, row, palette[colorIdx] or (0xFF shl 24))
            }
        }
        Bitmap.createScaledBitmap(bitmap, 800, 800, false)
    } catch (e: Exception) {
        null
    }
}

private fun computeProgress(puzzle: SavedPuzzle): Float {
    return try {
        val targetColors = bytesToIntArray(puzzle.targetColors)
        val userColors = bytesToIntArray(puzzle.userColors)
        val total = targetColors.size - puzzle.prefillCount
        if (total <= 0) return 1f
        val correct = targetColors.indices.count { userColors[it] == targetColors[it] }
        val userCorrect = correct - puzzle.prefillCount
        (userCorrect.coerceAtLeast(0)).toFloat() / total
    } catch (e: Exception) {
        0f
    }
}

private fun bytesToIntArray(bytes: ByteArray): IntArray {
    val intBuf = java.nio.ByteBuffer.wrap(bytes).asIntBuffer()
    val result = IntArray(intBuf.remaining())
    intBuf.get(result)
    return result
}

private fun saveImageToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "color_by_number_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Color by Number")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        false
    }
}
