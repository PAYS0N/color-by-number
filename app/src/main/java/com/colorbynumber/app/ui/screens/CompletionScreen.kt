package com.colorbynumber.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorbynumber.app.engine.PuzzleState

@Composable
fun CompletionScreen(
    puzzleState: PuzzleState,
    onHome: () -> Unit
) {
    // Generate the full-color image from the puzzle
    val colorBitmap = remember {
        val size = puzzleState.gridSize
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val idx = row * size + col
                val colorIdx = puzzleState.targetColors[idx]
                bitmap.setPixel(col, row, puzzleState.palette[colorIdx])
            }
        }
        // Scale up for display
        Bitmap.createScaledBitmap(bitmap, 800, 800, false)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Puzzle Complete!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Image(
                bitmap = colorBitmap.asImageBitmap(),
                contentDescription = "Completed puzzle",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Back to Home", fontSize = 18.sp)
            }
        }
    }
}
