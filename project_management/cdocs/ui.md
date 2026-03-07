# Color by Number — UI Screens

## UI Screens

**HomeScreen (Create tab)**: Card-based layout with three actions: Take Photo, Pick from Gallery, and Pixel Art. Each is an ElevatedCard with icon, title, and subtitle. No internal state except callbacks.

**CameraScreen**: CameraX preview with pinch-to-zoom gesture. Capture button takes photo and returns Bitmap to MainActivity. Portrait-only or rotation-responsive depending on manifest config.

**ConfigScreen**: Grid size slider (8–100), detail level radio buttons (Low/Medium/High), and greyscale preview of the puzzle. Preview regenerates as sliders move. User configures, taps "Start Puzzle", triggers puzzle generation on Default dispatcher.

**PuzzleScreen**: Main gameplay UI. Draws grid with cells colored by userColors or targetColors (if correct). Grid lines drawn at Stroke(width = 1.5f). Palette bar at bottom shows all paletteOrder colors; completed colors animate out with a slide-up, fade, and horizontal shrink (300ms) via AnimatedVisibility in a scrollable Row. A vertical divider separates the eraser button from color swatches. Cells are not colorable in greyscale (zoomed-out) mode — taps/paints are ignored when isGreyscaleMode is true. Eraser tool. Cell navigator shows which cells need a specific color. Settings button toggles preventErrors, preventOverwrite, and vibrate. Completion is detected in real-time; onComplete callback fires when isComplete() becomes true.

**CompletionScreen**: Celebration screen showing final image (grid colored by targetColors). Play Replay button to view replay animation. Clicking either closes to transition to History screen with auto-open-first flag set.

**HistoryScreen (My Work tab)**: Combined grid of saved puzzles and pixel art (newest first), each showing thumbnail preview. Puzzle cards show a completion badge or progress percentage. Pixel art cards show a brush badge. Tapping a puzzle opens InProgressPuzzleDialog (Resume/Delete) or CompletedPuzzleDialog (replay/download/delete). Tapping pixel art opens PixelArtDialog (Resume/Delete with confirmation, plus a Code icon button that exports sparse JSON to the Downloads folder). Both InProgressPuzzleDialog and PixelArtDialog have an X close button in the title row (Icons.Default.Close). Both dialogs match Material 3 style. No back arrow; navigation via bottom tabs.

**GalleryScreen (Explore tab)**: Fetches public gallery via GalleryRepository.fetchPuzzles() on load. Displays puzzles as a 2-column grid of cards showing greyscale preview thumbnails and puzzle name only (no grid size or other metadata). User selects one, converted to PuzzleState via toPuzzleState(), and gameplay starts with puzzleOrigin = Screen.GALLERY (so back returns here). Network errors gracefully degrade to empty list. No back arrow; navigation via bottom tabs.

**PixelArtScreen**: Blank canvas sized by user choice (8–100). Cells are colorable via palette or custom color picker. A vertical divider separates the eraser from the color picker and swatches. Eraser. Save icon button in top bar saves without leaving. On back with unsaved changes, prompts Save / Discard / Cancel. Artwork is persisted to `saved_pixel_arts` via PixelArtRepository. Resumed from HistoryScreen by loading cell colors into a fresh PixelArtState. Grid always renders per-cell with visible gridlines at all zoom levels (Stroke(width = 1.5f); no bitmap overview mode).

**PuzzleScreen and PixelArtScreen gesture flows** (both screens share identical logic, duplicated):
- `touch down → (<150ms) → lift` — tap: colors tapped cell immediately (UNDECIDED path)
- `touch down → (<150ms) → swipe past touchSlop` — pan: moves the grid (PANNING path)
- `touch down → (150ms timeout) → enter painting mode → (50ms timeout, no move)` — colors the held cell, continues in painting mode
- `touch down → (150ms timeout) → enter painting mode → (<50ms) → lift` — colors the initial cell and exits
- `touch down → (150ms timeout) → enter painting mode → (<50ms) → swipe to new cell` — colors the initial cell, then colors the destination cell, continues in painting mode

**ColorPickerSheet**: Modal bottom sheet (Material 3 BottomSheet). HSV picker with saturation/value pad (2D) and hue slider. Selected color previewed. Confirm/dismiss.

---

## Critical File Paths & Responsibilities

**Entry**: [MainActivity.kt](../app/src/main/java/com/colorbynumber/app/MainActivity.kt) — screen navigation, event recording attachment, lifecycle flushing

**Game State**: [PuzzleState.kt](../app/src/main/java/com/colorbynumber/app/engine/PuzzleState.kt) — grid + color logic

**Processing**: [Pixelator.kt](../app/src/main/java/com/colorbynumber/app/engine/Pixelator.kt), [ColorQuantizer.kt](../app/src/main/java/com/colorbynumber/app/engine/ColorQuantizer.kt)

**Persistence**: [PuzzleRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleRepository.kt), [SavedPuzzle.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPuzzle.kt), [AppDatabase.kt](../app/src/main/java/com/colorbynumber/app/data/AppDatabase.kt), [PixelArtRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PixelArtRepository.kt), [SavedPixelArt.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPixelArt.kt)

**Gallery**: [GalleryRepository.kt](../app/src/main/java/com/colorbynumber/app/data/GalleryRepository.kt)

**Settings**: [AppSettings.kt](../app/src/main/java/com/colorbynumber/app/AppSettings.kt)

**Replay**: [PuzzleReplayState.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayState.kt), [PuzzleReplayPlayer.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayPlayer.kt)

**UI Screens**: [HomeScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HomeScreen.kt), [CameraScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CameraScreen.kt), [ConfigScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ConfigScreen.kt), [PuzzleScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PuzzleScreen.kt), [CompletionScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CompletionScreen.kt), [HistoryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HistoryScreen.kt), [GalleryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/GalleryScreen.kt), [PixelArtScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PixelArtScreen.kt), [ColorPickerSheet.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ColorPickerSheet.kt)
