# Color by Number — Data Model & Persistence

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

**SavedPixelArt** (Room entity, `saved_pixel_arts` table):
- `id`: auto-generated primary key (Long)
- `gridSize`: canvas width/height (Int)
- `cellColors`: ByteArray-encoded IntArray of ARGB values, row-major; 0 = empty (renders as white)
- `createdAt`, `updatedAt`: timestamps (milliseconds)
- Custom equals/hashCode by id only

---

## Persistence & Event Recording

**Event Buffering**: PuzzleRepository buffers PlacementEvent objects in-memory in a mutableList. When recordEvent() is called from PuzzleState's onCellChanged callback, the event is added. Auto-flush triggers at FLUSH_THRESHOLD (50 events). Mutex guards buffer for thread safety. Manual flush() writes all buffered events to eventDao and clears buffer. activePuzzleId tracks which puzzle events belong to.

**Snapshotting**: On app pause/stop (lifecycle event), MainActivity explicitly calls repository.flush() and repository.snapshotUserColors(). Snapshot updates the userColors and preventErrors/preventOverwrite fields in the SavedPuzzle entity and sets updatedAt. This ensures user progress is not lost if events buffer has not hit threshold.

**Completion**: markCompleted() flushes pending events, then updates status to COMPLETED, snapshots colors, and sets updatedAt.

**Deletion**: deletePuzzle(id) deletes the SavedPuzzle entity; Room cascade deletes all PlacementEvent rows with matching puzzleId.

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
