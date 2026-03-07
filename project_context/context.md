# Color by Number — Project Context

## App Purpose & User Flow

Color by Number is an Android app that converts photos into interactive color-by-number puzzles. Users follow one of three paths: (1) capture or select a photo, configure grid size (8–100 pixels) and color detail level, then solve the puzzle by tapping cells and selecting colors; (2) browse and play pre-made puzzles from a public gallery (GitHub-hosted); (3) draw pixel art freeform on a blank canvas. Completed puzzles can be replayed, downloaded, or deleted. All image processing is local; the app is free and ad-free.

**Navigation**: Home screen branches to Camera, Gallery picker, History, Public Gallery, or Pixel Art mode. Puzzle play can originate from any of these paths and returns to the originating screen on back. Config and Puzzle screens are transient; completion screen auto-transitions to History after replay is viewed.

---

## Tech Stack

**Language**: Kotlin
**UI Framework**: Jetpack Compose with Material 3 theme (light color scheme, primary indigo, secondary blue-grey)
**Database**: Room/SQLite with migrations
**Image Capture**: CameraX (preview + photo capture)
**HTTP**: OkHttp (via Retrofit or direct HttpURLConnection)
**Permissions**: Runtime camera permission handling via Accompanist PermissionsState
**Threading**: Coroutines (Dispatchers.Main, Dispatchers.IO, Dispatchers.Default)
**Dependencies**: androidx-lifecycle, kotlinx-coroutines, material3, room, camerax, json

**Min SDK**: 26
**Target SDK**: 34
**Tested/Built**: Written entirely using Claude Code.

---

## Architecture

**Pattern**: MVVM-like with state-driven UI. MainActivity holds all screen navigation state and puzzle/event callback registration. PuzzleRepository orchestrates persistence and event buffering. No dependency injection framework; components are instantiated directly in onCreate.

**Data Flow**:
1. Photo capture or selection → Bitmap
2. Pixelator: square crop, downscale to grid size
3. ColorQuantizer: reduce unique colors via k-means, merge near-duplicates, assign pixel indices
4. PuzzleState: immutable grid + palette, mutable user colors and settings
5. MainActivity: attaches event recording callback to PuzzleState
6. PuzzleRepository: buffers events in memory, flushes on threshold or explicit call, snapshots user colors on pause
7. Room/SQLite: persists SavedPuzzle entity and PlacementEvent rows

**Database**: Single AppDatabase with SavedPuzzleDao and PlacementEventDao. Entities auto-convert between IntArray/ByteArray via Room type converters. Foreign key cascade on puzzle delete.

---

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

## Data Model

**SavedPuzzle** (Room entity, `saved_puzzles` table):
- `id`: auto-generated primary key (Long)
- `gridSize`: puzzle width/height (Int)
- `paletteJson`: JSON string of RGB ints, parsed to List<Int> on load
- `paletteOrderJson`: JSON string of palette indices, parsed on load
- `targetColors`: ByteArray-encoded IntArray (row-major grid of correct color indices)
- `userColors`: ByteArray-encoded IntArray (row-major grid of user-placed colors, -1 = empty)
- `preventErrors`, `preventOverwrite`: boolean settings (copied from global AppSettings at puzzle start, then overrideable per-puzzle in settings UI)
- `prefillCount`: number of cells pre-filled at creation (excluded from progress calculation)
- `status`: enum PuzzleStatus (IN_PROGRESS, COMPLETED)
- `createdAt`, `updatedAt`: timestamps (milliseconds)
- Custom equals/hashCode by id only (ByteArray would break equality)

**PlacementEvent** (Room entity, `placement_events` table):
- `id`: auto-generated primary key (Long)
- `puzzleId`: foreign key to SavedPuzzle (cascades on delete)
- `row`, `col`: cell coordinates
- `colorIndex`: placed color index (or -1 for erase)
- `eventType`: enum PlacementEventType (PLACE, ERASE)
- `timestamp`: auto-set to current millis on insert
- Indexed on (puzzleId, timestamp) for efficient replay queries

**PuzzleState** (in-memory game state, not a Room entity):
- `targetColors`: IntArray, immutable
- `palette`: List<Int> of RGB values, immutable
- `paletteOrder`: List<Int> of palette indices ordered by first appearance, immutable
- `gridSize`: Int, immutable
- `userColors`: IntArray, mutable (-1 = empty, 0..palette.size-1 = placed color index)
- `preventErrors`, `preventOverwrite`: boolean, mutable per-puzzle
- `prefillCount`: Int, set at creation
- `onCellChanged`: optional callback (row, col, colorIndex, isErase) invoked after every successful change; MainActivity attaches event recording here
- Public methods: `colorCell(row, col, colorIndex): Boolean` (respects prevent settings, fires callback), `eraseCell(row, col)` (always allowed), `isCellCorrect/isCellFilled(row, col): Boolean`, `remainingForColor/totalForColor(colorIndex): Int`, `completedColors(): Set<Int>`, `isComplete(): Boolean`

**PuzzleReplayState** (replay-specific, ephemeral):
- `gridSize`, `palette`, `targetColors`: copied from SavedPuzzle
- `correctEvents`: List<PlacementEvent> filtered to only correct placements (using target colors)
- Static method `filterCorrectEvents()` cross-checks event cell coords against targetColors to drop erases and incorrect placements

---

## Persistence & Event Recording

**Event Buffering**: PuzzleRepository buffers PlacementEvent objects in-memory in a mutableList. When recordEvent() is called from PuzzleState's onCellChanged callback, the event is added. Auto-flush triggers at FLUSH_THRESHOLD (50 events). Mutex guards buffer for thread safety. Manual flush() writes all buffered events to eventDao and clears buffer. activePuzzleId tracks which puzzle events belong to.

**Snapshotting**: On app pause/stop (lifecycle event), MainActivity explicitly calls repository.flush() and repository.snapshotUserColors(). Snapshot updates the userColors and preventErrors/preventOverwrite fields in the SavedPuzzle entity and sets updatedAt. This ensures user progress is not lost if events buffer has not hit threshold.

**Completion**: markCompleted() flushes pending events, then updates status to COMPLETED, snapshots colors, and sets updatedAt.

**Deletion**: deletePuzzle(id) deletes the SavedPuzzle entity; Room cascade deletes all PlacementEvent rows with matching puzzleId.

---

## Replay System

**Recording Phase**: As user plays, each cell placement/erase is recorded via PlacementEvent (row, col, colorIndex, eventType, timestamp). Events are timestamped but replay does not use timestamps; it replays in chronological order at a fixed speed.

**Loading Phase**: loadReplayState(puzzleId) fetches all events, applies filterCorrectEvents() to keep only successful placements matching target colors, and returns a PuzzleReplayState ready for animation.

**Animation Phase**: PuzzleReplayPlayer (referenced in manifest) steps through correctEvents, coloring cells in sequence. UI animations are via Compose transitions (not detailed in core docs but referenced in HistoryScreen composable).

---

## Public Gallery

**GalleryRepository** (`app/src/main/java/com/colorbynumber/app/data/GalleryRepository.kt`):
- `fetchPuzzles()`: HTTP fetch from BASE_URL (`https://pays0n.github.io/color-by-number/data`) for puzzle1.json and puzzle2.json, with 10-second timeouts
- `toPuzzleState(GalleryPuzzle)`: converts GalleryPuzzle to PuzzleState, pre-filling cells for sparse puzzles
- Dual JSON format support:
  - **Dense**: fields name, gridSize, palette (array of ints), paletteOrder, targetColors (array of ints)
  - **Sparse**: gridSize, data (array of {x, y, color}), optional name; white is always color index 0 (padding); unspecified cells are pre-filled as complete at puzzle start

**Data Classes**: GalleryPuzzle(name, gridSize, palette, paletteOrder, targetColors, prefillIndices). Converting sparse to dense builds a unique color list and maps cell colors to palette indices.

**Gallery Selection Flow**: GalleryScreen fetches and displays puzzles. User selects one, toPuzzleState() is called on a background thread, then PuzzleRepository.createPuzzle() persists it to local DB, and gameplay begins. Puzzle origin is set to Screen.GALLERY so back returns to the gallery screen.

---

## Settings

**Global Settings** (AppSettings, `AppSettings.kt`):
- Stored in SharedPreferences under "app_settings"
- `preventErrors` (default true): global setting for new puzzles; can be overridden per-puzzle in PuzzleScreen settings UI
- `preventOverwrite` (default true): global setting for new puzzles; can be overridden per-puzzle
- `vibrate` (default true): vibration feedback on cell placement
- Initialized once in MainActivity.onCreate(); used as defaults when creating/loading puzzles

**Per-Puzzle Settings**:
- SavedPuzzle stores preventErrors and preventOverwrite; PuzzleScreen UI allows toggle of these settings at runtime, syncing to both AppSettings and the active PuzzleState
- PuzzleScreen also tracks navigatorThreshold (color cell navigator arrow threshold, defaulting to gridSize/5) but this is UI-only (not persisted)

---

## UI Screens

**HomeScreen**: Entry point with buttons for Camera, Gallery, My Puzzles (History), Public Gallery, and Pixel Art. No internal state except callbacks.

**CameraScreen**: CameraX preview with pinch-to-zoom gesture. Capture button takes photo and returns Bitmap to MainActivity. Portrait-only or rotation-responsive depending on manifest config.

**ConfigScreen**: Grid size slider (8–100), detail level radio buttons (Low/Medium/High), and greyscale preview of the puzzle. Preview regenerates as sliders move. User configures, taps "Start Puzzle", triggers puzzle generation on Default dispatcher.

**PuzzleScreen**: Main gameplay UI. Draws grid with cells colored by userColors or targetColors (if correct). Palette bar at bottom shows all paletteOrder colors, excluding completed colors. Color picker icon opens ColorPickerSheet (HSV picker). Eraser tool. Cell navigator shows which cells need a specific color. Settings button toggles preventErrors, preventOverwrite, and vibrate. Top bar shows completion progress (X of Y cells done). Completion is detected in real-time; onComplete callback fires when isComplete() becomes true.

**CompletionScreen**: Celebration screen showing final image (grid colored by targetColors). Play Replay button to view replay animation. Clicking either closes to transition to History screen with auto-open-first flag set.

**HistoryScreen**: Grid of all saved puzzles (newest first), each showing thumbnail preview. Delete action per puzzle (with optional confirmation). Long-press or dedicated button plays replay. Replay player animates cells being filled in sequence (via PuzzleReplayPlayer). Back button returns to Home.

**GalleryScreen**: Fetches public gallery via GalleryRepository.fetchPuzzles() on load. Displays puzzles as grid. User selects one, converted to PuzzleState via toPuzzleState(), and gameplay starts with puzzleOrigin = Screen.GALLERY (so back returns here). Network errors gracefully degrade to empty list.

**PixelArtScreen**: Blank canvas sized by user choice (8–100). Cells are colorable via palette or custom color picker. Eraser. No save/load yet (noted as open item). Back returns to Home.

**ColorPickerSheet**: Modal bottom sheet (Material 3 BottomSheet). HSV picker with saturation/value pad (2D) and hue slider. Selected color previewed. Confirm/dismiss.

---

## Critical File Paths & Responsibilities

**Entry**: [MainActivity.kt](../app/src/main/java/com/colorbynumber/app/MainActivity.kt) — screen navigation, event recording attachment, lifecycle flushing

**Game State**: [PuzzleState.kt](../app/src/main/java/com/colorbynumber/app/engine/PuzzleState.kt) — grid + color logic

**Processing**: [Pixelator.kt](../app/src/main/java/com/colorbynumber/app/engine/Pixelator.kt), [ColorQuantizer.kt](../app/src/main/java/com/colorbynumber/app/engine/ColorQuantizer.kt)

**Persistence**: [PuzzleRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleRepository.kt), [SavedPuzzle.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPuzzle.kt), [AppDatabase.kt](../app/src/main/java/com/colorbynumber/app/data/AppDatabase.kt)

**Gallery**: [GalleryRepository.kt](../app/src/main/java/com/colorbynumber/app/data/GalleryRepository.kt)

**Settings**: [AppSettings.kt](../app/src/main/java/com/colorbynumber/app/AppSettings.kt)

**Replay**: [PuzzleReplayState.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayState.kt), [PuzzleReplayPlayer.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayPlayer.kt)

**UI Screens**: [HomeScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HomeScreen.kt), [CameraScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CameraScreen.kt), [ConfigScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ConfigScreen.kt), [PuzzleScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PuzzleScreen.kt), [CompletionScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CompletionScreen.kt), [HistoryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HistoryScreen.kt), [GalleryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/GalleryScreen.kt), [PixelArtScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PixelArtScreen.kt), [ColorPickerSheet.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ColorPickerSheet.kt)
