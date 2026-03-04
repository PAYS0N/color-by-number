package com.colorbynumber.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorbynumber.app.engine.ColorQuantizer
import com.colorbynumber.app.engine.Pixelator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    sourceBitmap: Bitmap,
    onStartPuzzle: (gridSize: Int, detailLevel: ColorQuantizer.DetailLevel) -> Unit,
    onBack: () -> Unit
) {
    var gridSize by remember { mutableFloatStateOf(50f) }
    var detailLevel by remember { mutableStateOf(ColorQuantizer.DetailLevel.MEDIUM) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Generate greyscale preview when grid size changes
    val currentGridSize = gridSize.toInt()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentGridSize) {
        withContext(Dispatchers.Default) {
            val pixelated = Pixelator.pixelate(sourceBitmap, currentGridSize)
            val grey = Pixelator.toGreyscale(pixelated)
            // Scale up for display so pixels are visible
            val displaySize = 800
            val scaled = Bitmap.createScaledBitmap(grey, displaySize, displaySize, false)
            withContext(Dispatchers.Main) {
                previewBitmap = scaled
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Puzzle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Greyscale preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Greyscale preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid size slider
            Text(
                text = "Grid Size: ${currentGridSize}×${currentGridSize}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Slider(
                value = gridSize,
                onValueChange = { gridSize = it },
                valueRange = 20f..100f,
                steps = 79, // 20 to 100 = 80 values, so 79 steps between them
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Detail level dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Color Detail: ",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = detailLevel.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        ColorQuantizer.DetailLevel.entries.forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Text(level.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    detailLevel = level
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start button
            Button(
                onClick = { onStartPuzzle(currentGridSize, detailLevel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Start Puzzle", fontSize = 18.sp)
            }
        }
    }
}
