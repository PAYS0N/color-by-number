package com.colorbynumber.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.colorbynumber.app.data.AppDatabase
import com.colorbynumber.app.data.GalleryRepository
import com.colorbynumber.app.data.PixelArtRepository
import com.colorbynumber.app.data.PlacementEventType
import com.colorbynumber.app.data.PuzzleRepository
import com.colorbynumber.app.engine.ColorQuantizer
import com.colorbynumber.app.engine.Pixelator
import com.colorbynumber.app.engine.PixelArtState
import com.colorbynumber.app.engine.PuzzleState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.colorbynumber.app.ui.screens.*
import com.colorbynumber.app.ui.theme.ColorByNumberTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    HOME, CAMERA, CONFIG, PUZZLE, COMPLETE, HISTORY, GALLERY, PIXEL_ART
}

enum class Tab(val label: String, val icon: ImageVector) {
    CREATE("Create", Icons.Default.AddCircle),
    EXPLORE("Explore", Icons.Default.Public),
    MY_WORK("My Work", Icons.Default.FolderOpen);

    fun rootScreen(): Screen = when (this) {
        CREATE -> Screen.HOME
        EXPLORE -> Screen.GALLERY
        MY_WORK -> Screen.HISTORY
    }
}

private val TAB_SCREENS = setOf(Screen.HOME, Screen.GALLERY, Screen.HISTORY)

private fun screenToTab(screen: Screen): Tab = when (screen) {
    Screen.GALLERY -> Tab.EXPLORE
    Screen.HISTORY -> Tab.MY_WORK
    else -> Tab.CREATE
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: PuzzleRepository
    private lateinit var pixelArtRepository: PixelArtRepository

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettings.init(this)

        val db = AppDatabase.getInstance(this)
        repository = PuzzleRepository(db.savedPuzzleDao(), db.placementEventDao())
        pixelArtRepository = PixelArtRepository(db.savedPixelArtDao())

        setContent {
            ColorByNumberTheme {
                    var currentScreen by remember { mutableStateOf(Screen.HOME) }
                    var selectedTab by remember { mutableStateOf(Tab.CREATE) }
                    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    var puzzleState by remember { mutableStateOf<PuzzleState?>(null) }
                    var isProcessing by remember { mutableStateOf(false) }
                    // Track where PUZZLE was entered from so back goes to the right screen
                    var puzzleOrigin by remember { mutableStateOf(Screen.HOME) }
                    // When true, HistoryScreen auto-opens the first completed puzzle's replay
                    var historyAutoOpenFirst by remember { mutableStateOf(false) }
                    // Pixel art state
                    var pixelArtOrigin by remember { mutableStateOf(Screen.HOME) }
                    var pixelArtState by remember { mutableStateOf<PixelArtState?>(null) }
                    var activePixelArtId by remember { mutableStateOf<Long?>(null) }
                    var showPixelArtSizeDialog by remember { mutableStateOf(false) }
                    var pixelArtGridSize by remember { mutableIntStateOf(32) }

                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    val lifecycleOwner = LocalLifecycleOwner.current

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
                    var pendingCameraNavigation by remember { mutableStateOf(false) }
                    val cameraPermission = rememberPermissionState(
                        android.Manifest.permission.CAMERA
                    )
                    LaunchedEffect(cameraPermission.status) {
                        if (pendingCameraNavigation && cameraPermission.status.isGranted) {
                            pendingCameraNavigation = false
                            currentScreen = Screen.CAMERA
                        }
                    }

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

                    val showBottomBar = currentScreen in TAB_SCREENS

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    Tab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = selectedTab == tab,
                                            onClick = {
                                                selectedTab = tab
                                                currentScreen = tab.rootScreen()
                                            },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                Screen.HOME -> {
                                    HomeScreen(
                                        onTakePhoto = {
                                            if (cameraPermission.status.isGranted) {
                                                currentScreen = Screen.CAMERA
                                            } else {
                                                pendingCameraNavigation = true
                                                cameraPermission.launchPermissionRequest()
                                            }
                                        },
                                        onPickGallery = {
                                            galleryLauncher.launch("image/*")
                                        },
                                        onPixelArt = {
                                            pixelArtOrigin = Screen.HOME
                                            showPixelArtSizeDialog = true
                                        }
                                    )
                                }

                                Screen.HISTORY -> {
                                    BackHandler {
                                        selectedTab = Tab.CREATE
                                        currentScreen = Screen.HOME
                                    }
                                    HistoryScreen(
                                        repository = repository,
                                        pixelArtRepository = pixelArtRepository,
                                        autoOpenFirst = historyAutoOpenFirst,
                                        onAutoOpenConsumed = { historyAutoOpenFirst = false },
                                        onResumePuzzle = { id ->
                                            isProcessing = true
                                            coroutineScope.launch {
                                                val state = withContext(Dispatchers.IO) {
                                                    repository.loadPuzzle(id)
                                                }
                                                if (state != null) {
                                                    attachEventRecording(state, coroutineScope)
                                                    puzzleState = state
                                                    puzzleOrigin = Screen.HISTORY
                                                    currentScreen = Screen.PUZZLE
                                                }
                                                isProcessing = false
                                            }
                                        },
                                        onResumePixelArt = { id ->
                                            isProcessing = true
                                            coroutineScope.launch {
                                                val state = withContext(Dispatchers.IO) {
                                                    pixelArtRepository.loadState(id)
                                                }
                                                if (state != null) {
                                                    pixelArtState = state
                                                    activePixelArtId = id
                                                    pixelArtOrigin = Screen.HISTORY
                                                    currentScreen = Screen.PIXEL_ART
                                                }
                                                isProcessing = false
                                            }
                                        }
                                    )
                                }

                                Screen.CAMERA -> {
                                    BackHandler {
                                        selectedTab = Tab.CREATE
                                        currentScreen = Screen.HOME
                                    }
                                    CameraScreen(
                                        onPhotoCaptured = { bitmap ->
                                            capturedBitmap = bitmap
                                            currentScreen = Screen.CONFIG
                                        },
                                        onBack = {
                                            selectedTab = Tab.CREATE
                                            currentScreen = Screen.HOME
                                        }
                                    )
                                }

                                Screen.CONFIG -> {
                                    BackHandler {
                                        selectedTab = Tab.CREATE
                                        currentScreen = Screen.HOME
                                    }
                                    capturedBitmap?.let { bmp ->
                                        ConfigScreen(
                                            sourceBitmap = bmp,
                                            onStartPuzzle = { gridSize, detailLevel ->
                                                isProcessing = true
                                                coroutineScope.launch {
                                                    val state = withContext(Dispatchers.Default) {
                                                        buildPuzzle(bmp, gridSize, detailLevel)
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        repository.createPuzzle(state)
                                                    }
                                                    attachEventRecording(state, coroutineScope)
                                                    puzzleState = state
                                                    puzzleOrigin = Screen.HISTORY
                                                    isProcessing = false
                                                    currentScreen = Screen.PUZZLE
                                                }
                                            },
                                            onBack = {
                                                selectedTab = Tab.CREATE
                                                currentScreen = Screen.HOME
                                            }
                                        )
                                    }
                                }

                                Screen.PUZZLE -> {
                                    val origin = puzzleOrigin
                                    puzzleState?.let { state ->
                                        val navigateBack = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    repository.flush()
                                                    repository.snapshotUserColors(state)
                                                }
                                                selectedTab = screenToTab(origin)
                                                currentScreen = origin
                                            }
                                            Unit
                                        }
                                        BackHandler { navigateBack() }
                                        PuzzleScreen(
                                            puzzleState = state,
                                            onComplete = {
                                                coroutineScope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repository.markCompleted(state)
                                                    }
                                                    currentScreen = Screen.COMPLETE
                                                }
                                            },
                                            onBack = { navigateBack() }
                                        )
                                    }
                                }

                                Screen.GALLERY -> {
                                    BackHandler {
                                        selectedTab = Tab.CREATE
                                        currentScreen = Screen.HOME
                                    }
                                    GalleryScreen(
                                        onSelectPuzzle = { galleryPuzzle ->
                                            isProcessing = true
                                            coroutineScope.launch {
                                                val state = withContext(Dispatchers.Default) {
                                                    GalleryRepository.toPuzzleState(galleryPuzzle)
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.createPuzzle(state)
                                                }
                                                attachEventRecording(state, coroutineScope)
                                                puzzleState = state
                                                puzzleOrigin = Screen.GALLERY
                                                isProcessing = false
                                                currentScreen = Screen.PUZZLE
                                            }
                                        }
                                    )
                                }

                                Screen.PIXEL_ART -> {
                                    val origin = pixelArtOrigin
                                    BackHandler {
                                        pixelArtState = null
                                        activePixelArtId = null
                                        selectedTab = screenToTab(origin)
                                        currentScreen = origin
                                    }
                                    pixelArtState?.let { state ->
                                        PixelArtScreen(
                                            state = state,
                                            savedId = activePixelArtId,
                                            onBack = {
                                                pixelArtState = null
                                                activePixelArtId = null
                                                selectedTab = screenToTab(origin)
                                                currentScreen = origin
                                            },
                                            onSave = {
                                                val artId = activePixelArtId
                                                coroutineScope.launch {
                                                    if (artId == null) {
                                                        val newId = withContext(Dispatchers.IO) {
                                                            pixelArtRepository.save(state)
                                                        }
                                                        activePixelArtId = newId
                                                    } else {
                                                        withContext(Dispatchers.IO) {
                                                            pixelArtRepository.update(artId, state)
                                                        }
                                                    }
                                                }
                                            },
                                            onSaveAndBack = {
                                                val artId = activePixelArtId
                                                coroutineScope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        if (artId == null) {
                                                            pixelArtRepository.save(state)
                                                        } else {
                                                            pixelArtRepository.update(artId, state)
                                                        }
                                                    }
                                                    pixelArtState = null
                                                    activePixelArtId = null
                                                    selectedTab = Tab.MY_WORK
                                                    currentScreen = Screen.HISTORY
                                                }
                                            }
                                        )
                                    }
                                }

                                Screen.COMPLETE -> {
                                    val navigateToHistory = {
                                        coroutineScope.launch {
                                            puzzleState = null
                                            capturedBitmap = null
                                            historyAutoOpenFirst = true
                                            selectedTab = Tab.MY_WORK
                                            currentScreen = Screen.HISTORY
                                        }
                                        Unit
                                    }
                                    BackHandler { navigateToHistory() }
                                    puzzleState?.let { state ->
                                        CompletionScreen(
                                            puzzleState = state,
                                            onViewReplay = { navigateToHistory() }
                                        )
                                    }
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

                    // Pixel art canvas size dialog
                    if (showPixelArtSizeDialog) {
                        AlertDialog(
                            onDismissRequest = { showPixelArtSizeDialog = false },
                            title = { Text("Canvas Size") },
                            text = {
                                Column {
                                    Text(
                                        text = "${pixelArtGridSize}×${pixelArtGridSize}",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Slider(
                                        value = pixelArtGridSize.toFloat(),
                                        onValueChange = { pixelArtGridSize = it.toInt() },
                                        valueRange = 8f..100f,
                                        steps = 91
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showPixelArtSizeDialog = false
                                    pixelArtState = PixelArtState(pixelArtGridSize)
                                    activePixelArtId = null
                                    currentScreen = Screen.PIXEL_ART
                                }) {
                                    Text("Create")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPixelArtSizeDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
            }
        }
    }

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
