# Color by Number — Pipeline & Replay

## Puzzle Generation Pipeline

**Pixelator** (`app/src/main/java/com/colorbynumber/app/engine/Pixelator.kt`):
- `pixelate(Bitmap, gridSize)`: crops input to centered square (shortest dimension), downscales to gridSize×gridSize using nearest-neighbor resampling (Filter=false)
- `extractPixels(Bitmap)`: returns raw ARGB pixel IntArray in row-major order
- `preparePreviewSource(Bitmap, maxSize)`: downsamples to at most maxSize for display efficiency
- `toGreyscale(Bitmap)`: converts to greyscale with standard luminance formula, lightens by 60% for preview

**ColorQuantizer** (`app/src/main/java/com/colorbynumber/app/engine/ColorQuantizer.kt`):
- `quantize(IntArray pixels, Int gridSize, DetailLevel)`: reduces unique colors in a single pass
- DetailLevel enum: LOW (0.75× gridSize), MEDIUM (1.0× gridSize), HIGH (1.5× gridSize); target clamped to [2, 200]
- Step 1: collect unique colors
- Step 2: if unique colors > target, run k-means clustering (20 iterations, random init seed 42)
- Step 3: merge colors closer than RGB distance 15.0 (always, even if k-means skipped)
- Step 4: assign each pixel to nearest palette color (exhaustive search)
- Step 5: compute palette order by scanning left-right, top-bottom; record first appearance index for each color
- Output: QuantizationResult(colorIndices, palette, paletteOrder, gridSize)

**PuzzleState Construction**: targetColors, palette, paletteOrder, and gridSize are set at creation and immutable. userColors initialized to -1 (empty). Prefill is only for gallery puzzles.

---

## Replay System

**Recording Phase**: As user plays, each cell placement/erase is recorded via PlacementEvent (row, col, colorIndex, eventType, timestamp). Events are timestamped but replay does not use timestamps; it replays in chronological order at a fixed speed.

**Loading Phase**: loadReplayState(puzzleId) fetches all events, applies filterCorrectEvents() to keep only successful placements matching target colors, and returns a PuzzleReplayState ready for animation.

**Animation Phase**: PuzzleReplayPlayer (referenced in manifest) steps through correctEvents, coloring cells in sequence. UI animations are via Compose transitions (not detailed in core docs but referenced in HistoryScreen composable).
