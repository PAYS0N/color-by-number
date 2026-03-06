package com.colorbynumber.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initialColor: Int? = null,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // HSV state
    val initialHsv = remember {
        if (initialColor != null) {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(initialColor, hsv)
            hsv
        } else {
            floatArrayOf(0f, 1f, 1f)
        }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, saturation, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Saturation-Value pad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    // Background: white to hue color (horizontal saturation)
                    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.White, hueColor)
                        )
                    )
                    // Overlay: transparent to black (vertical value)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black)
                        )
                    )
                    // Selector circle
                    val cx = saturation * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 10.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hue bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        }
                    }
            ) {
                // Rainbow gradient
                val hueColors = (0..6).map { i ->
                    Color(AndroidColor.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
                }
                drawRect(
                    brush = Brush.horizontalGradient(colors = hueColors),
                    size = Size(size.width, size.height)
                )
                // Selector
                val hx = (hue / 360f) * size.width
                drawRect(
                    color = Color.White,
                    topLeft = Offset(hx - 3.dp.toPx(), 0f),
                    size = Size(6.dp.toPx(), size.height),
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(hx - 3.dp.toPx(), 0f),
                    size = Size(6.dp.toPx(), size.height),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview + Select button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Color preview swatch
                Canvas(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    drawRect(color = Color(currentColor))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { onColorSelected(currentColor) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Select Color", fontSize = 16.sp)
                }
            }
        }
    }
}
