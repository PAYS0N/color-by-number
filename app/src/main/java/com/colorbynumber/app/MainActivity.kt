package com.colorbynumber.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.colorbynumber.app.data.AppDatabase
import com.colorbynumber.app.data.PlacementEventType
import com.colorbynumber.app.data.PuzzleRepository
import com.colorbynumber.app.engine.ColorQuantizer
import com.colorbynumber.app.engine.Pixelator
import com.colorbynumber.app.engine.PuzzleState
import com.colorbynumber.app.ui.screens.*
import com.colorbynumber.app.ui.theme.ColorByNumberTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    HOME, CAMERA, CONFIG, PUZZLE, COMPLETE
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: PuzzleRepository

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        repository = PuzzleRepository(db.savedPuzzleDao(), db.placementEventDao())

        setContent {
            ColorByNumberTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.HOME) }
                    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    var puzzleState by remember { mutableStateOf<PuzzleState?>(null) }
                    var isProcessing by remember { mutableStateOf(false) }
                    var hasInProgressPuzzle by remember { mutableStateOf(false) }

                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    val lifecycleOwner = LocalLifecycleOwner.current

                    // Check for an existing in-progress puzzle on launch
                    LaunchedEffect(Unit) {
                        val id = withContext(Dispatchers.IO) {
                            repository.getMostRecentInProgressId()
                        }
                        hasInProgressPuzzle = id != null
                    }

                    // Lifecycle observer: flush events + snapshot on pause/stop
                    DisposableEffect(lifecycleOwner, puzzleState) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                                puzzleState?.let { state ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.flush()
                                        repository.snapshotUserColors(state)
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // Camera permission
                    val cameraPermission = rememberPermissionState(
                        android.Manifest.permission.CAMERA
                    )

                    // Gallery picker
                    val galleryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            val inputStream = context.contentResolver.openInputStream(it)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            if (bitmap != null) {
                                capturedBitmap = bitmap
                                currentScreen = Screen.CONFIG
                            }
                        }
                    }

                    when (currentScreen) {
                        Screen.HOME -> {
                            HomeScreen(
                                onTakePhoto = {
                                    if (cameraPermission.status.isGranted) {
                                        currentScreen = Screen.CAMERA
                                    } else {
                                        cameraPermission.launchPermissionRequest()
                                    }
                                },
                                onPickGallery = {
                                    galleryLauncher.launch("image/*")
                                },
                                hasInProgressPuzzle = hasInProgressPuzzle,
                                onContinuePuzzle = {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        val state = withContext(Dispatchers.IO) {
                                            val id = repository.getMostRecentInProgressId()
                                            id?.let { repository.loadPuzzle(it) }
                                        }
                                        if (state != null) {
                                            attachEventRecording(state, coroutineScope)
                                            puzzleState = state
                                            currentScreen = Screen.PUZZLE
                                        }
                                        isProcessing = false
                                    }
                                }
                            )
                        }

                        Screen.CAMERA -> {
                            CameraScreen(
                                onPhotoCaptured = { bitmap ->
                                    capturedBitmap = bitmap
                                    currentScreen = Screen.CONFIG
                                },
                                onBack = { currentScreen = Screen.HOME }
                            )
                        }

                        Screen.CONFIG -> {
                            capturedBitmap?.let { bmp ->
                                ConfigScreen(
                                    sourceBitmap = bmp,
                                    onStartPuzzle = { gridSize, detailLevel ->
                                        isProcessing = true
                                        coroutineScope.launch {
                                            val state = withContext(Dispatchers.Default) {
                                                buildPuzzle(bmp, gridSize, detailLevel)
                                            }
                                            // Persist the new puzzle
                                            withContext(Dispatchers.IO) {
                                                repository.createPuzzle(state)
                                            }
                                            attachEventRecording(state, coroutineScope)
                                            puzzleState = state
                                            hasInProgressPuzzle = true
                                            isProcessing = false
                                            currentScreen = Screen.PUZZLE
                                        }
                                    },
                                    onBack = { currentScreen = Screen.HOME }
                                )
                            }
                        }

                        Screen.PUZZLE -> {
                            puzzleState?.let { state ->
                                PuzzleScreen(
                                    puzzleState = state,
                                    onComplete = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.markCompleted(state)
                                        }
                                        hasInProgressPuzzle = false
                                        currentScreen = Screen.COMPLETE
                                    },
                                    onBack = {
                                        // Snapshot progress before leaving
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.flush()
                                            repository.snapshotUserColors(state)
                                        }
                                        currentScreen = Screen.HOME
                                    }
                                )
                            }
                        }

                        Screen.COMPLETE -> {
                            puzzleState?.let { state ->
                                CompletionScreen(
                                    puzzleState = state,
                                    onHome = {
                                        puzzleState = null
                                        capturedBitmap = null
                                        currentScreen = Screen.HOME
                                    }
                                )
                            }
                        }
                    }

                    // Loading overlay
                    if (isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    /**
     * Attach the onCellChanged callback so every placement/erase is
     * recorded via the repository's buffered event system.
     */
    private fun attachEventRecording(
        state: PuzzleState,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        state.onCellChanged = { row, col, colorIndex, isErase ->
            scope.launch(Dispatchers.IO) {
                repository.recordEvent(
                    row = row,
                    col = col,
                    colorIndex = colorIndex,
                    eventType = if (isErase) PlacementEventType.ERASE else PlacementEventType.PLACE
                )
            }
        }
    }

    private fun buildPuzzle(
        source: Bitmap,
        gridSize: Int,
        detailLevel: ColorQuantizer.DetailLevel
    ): PuzzleState {
        val pixelated = Pixelator.pixelate(source, gridSize)
        val pixels = Pixelator.extractPixels(pixelated)
        val result = ColorQuantizer.quantize(pixels, gridSize, detailLevel)

        return PuzzleState(
            targetColors = result.colorIndices,
            palette = result.palette,
            paletteOrder = result.paletteOrder,
            gridSize = result.gridSize
        )
    }
}
