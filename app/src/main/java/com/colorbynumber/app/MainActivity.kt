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

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()

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
                                            puzzleState = state
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
                                    onComplete = { currentScreen = Screen.COMPLETE },
                                    onBack = { currentScreen = Screen.HOME }
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
