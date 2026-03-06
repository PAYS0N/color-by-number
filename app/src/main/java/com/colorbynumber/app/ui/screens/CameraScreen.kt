package com.colorbynumber.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        provider.unbindAll()
                        // Use ViewPort so ImageCapture captures exactly what the preview shows.
                        // previewView.viewPort is non-null once the view is measured (which
                        // always happens before the camera-provider future resolves in practice).
                        val viewPort = previewView.viewPort
                        if (viewPort != null) {
                            val useCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(capture)
                                .setViewPort(viewPort)
                                .build()
                            provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
                        } else {
                            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Square crop overlay — mirrors Pixelator.cropToSquare behavior
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sqSize = minOf(size.width, size.height)
            val left = (size.width - sqSize) / 2f
            val top = (size.height - sqSize) / 2f
            val dimColor = Color(0x88000000)

            // Darken areas outside the square
            if (left > 0f) {
                drawRect(dimColor, topLeft = Offset.Zero, size = Size(left, size.height))
                drawRect(dimColor, topLeft = Offset(left + sqSize, 0f), size = Size(left, size.height))
            }
            if (top > 0f) {
                drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
                drawRect(dimColor, topLeft = Offset(0f, top + sqSize), size = Size(size.width, top))
            }

            // White border around the crop square
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(sqSize, sqSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Capture button at the bottom center
        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                capture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val bitmap = imageProxyToBitmap(imageProxy)
                            imageProxy.close()
                            if (bitmap != null) {
                                // Post to main thread
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onPhotoCaptured(bitmap)
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Capture",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    // Apply cropRect first — it is in pre-rotation (sensor) coordinates.
    // With ViewPort this defines the region matching the preview; without ViewPort
    // it is typically the full image so no crop occurs.
    val cropRect = imageProxy.cropRect
    if (cropRect.left != 0 || cropRect.top != 0 ||
        cropRect.width() != bitmap.width || cropRect.height() != bitmap.height) {
        bitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top,
            cropRect.width(), cropRect.height())
    }

    // Apply rotation
    val rotation = imageProxy.imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}
