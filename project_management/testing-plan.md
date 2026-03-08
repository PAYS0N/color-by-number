# Color by Number — Unit Testing Plan

## Overview

This plan prioritizes pure Kotlin logic with no Android dependencies first (highest ROI, fastest to run), then classes requiring Robolectric for `android.graphics.Color`, then integration tests, and finally UI tests. The app has no existing test infrastructure beyond the default `testInstrumentationRunner` in build.gradle.kts.

---

## Recommended Testing Stack

| Tool | Purpose | Why |
|------|---------|-----|
| **JUnit 4** | Test runner | Already on the Android classpath; Robolectric and Room testing utilities expect JUnit 4. No benefit to JUnit 5 for this project's scale. |
| **Robolectric** | Android framework stubs (Color, Bitmap) | ColorQuantizer and Pixelator call `android.graphics.Color` and `android.graphics.Bitmap`. Robolectric lets these run on the JVM without an emulator. |
| **MockK** | Mocking DAOs and coroutine-based dependencies | Idiomatic Kotlin mocking. Needed to isolate PuzzleRepository and PixelArtRepository from Room DAOs. |
| **kotlinx-coroutines-test** | `runTest`, `TestDispatcher` | Repository methods are all `suspend`; need structured coroutine testing. |
| **Truth** (Google) or plain JUnit assertions | Assertions | Truth reads well for collection/array assertions. Plain JUnit is fine too — no strong preference at this scale. |

**Not recommended for now**: Turbine (no Flow exposure in the current codebase), Compose UI testing (high cost, low unique coverage given the thin UI layer), Espresso (same reasoning).

---

## Prioritized Test List

Tests are ranked by risk/value — how likely the code is to break and how costly that breakage is.

### Priority 1 — Pure Kotlin Engine Logic (Unit Tests, JVM)

These classes contain the core game logic. Bugs here directly break gameplay. PuzzleState and PixelArtState are pure Kotlin with zero Android imports.

#### 1. `PuzzleState` — [PuzzleState.kt](../app/src/main/java/com/colorbynumber/app/engine/PuzzleState.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 1.1 | `colorCell` with correct color succeeds | Cell is set, returns true, callback fires |
| 1.2 | `colorCell` with wrong color + `preventErrors=true` is rejected | Returns false, cell unchanged |
| 1.3 | `colorCell` with wrong color + `preventErrors=false` succeeds | Cell is set to wrong color |
| 1.4 | `colorCell` on correct cell + `preventOverwrite=true` is rejected | Returns false, cell unchanged |
| 1.5 | `colorCell` on correct cell + `preventOverwrite=false` succeeds | Cell is overwritten |
| 1.6 | `colorCell` out of bounds returns false | Negative index and beyond-grid index |
| 1.7 | `eraseCell` always works regardless of settings | Cell set to -1, callback fires with isErase=true |
| 1.8 | `eraseCell` out of bounds is no-op | No crash, no callback |
| 1.9 | `isCellCorrect` returns true only when userColor matches target | Check filled-correct, filled-wrong, empty |
| 1.10 | `isCellFilled` returns true for any non-(-1) value | Check filled vs empty |
| 1.11 | `remainingForColor` counts only unfilled cells of that color | Partially filled grid |
| 1.12 | `totalForColor` counts all target cells of that color | Static count, unaffected by user progress |
| 1.13 | `isComplete` returns true only when all cells match | Fully correct, one cell wrong, all empty |
| 1.14 | `completedColors` returns only fully-filled color indices | Two colors complete, one partial |
| 1.15 | `onCellChanged` callback receives correct arguments | Verify row, col, colorIndex, isErase for both color and erase |

#### 2. `PixelArtState` — [PixelArtState.kt](../app/src/main/java/com/colorbynumber/app/engine/PixelArtState.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 2.1 | `colorCell` sets cell and returns true | Cell value changes |
| 2.2 | `colorCell` same color returns false (no-op) | Duplicate placement rejected |
| 2.3 | `colorCell` out of bounds returns false | No crash |
| 2.4 | `eraseCell` sets cell to 0 and returns true | Erase works |
| 2.5 | `eraseCell` on empty cell returns false | No-op for already-empty |
| 2.6 | `addRecentColor` prepends and caps at 10 | List order, deduplication, max size |
| 2.7 | `selectColor` sets selectedColor, clears eraser, adds to recent | State transitions |
| 2.8 | `selectEraser` sets isEraser=true, clears selectedColor | State transitions |

#### 3. `PuzzleReplayState.filterCorrectEvents` — [PuzzleReplayState.kt](../app/src/main/java/com/colorbynumber/app/engine/PuzzleReplayState.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 3.1 | Only PLACE events are kept (ERASE filtered out) | Erase events dropped |
| 3.2 | Only events matching target color are kept | Wrong-color placements dropped |
| 3.3 | Duplicate correct fills for same cell keep only the first | Deduplication |
| 3.4 | Out-of-bounds events are filtered | cellIdx outside targetColors.indices |
| 3.5 | Events are processed in timestamp order | Earlier correct fill wins over later one |
| 3.6 | Empty event list returns empty result | Edge case |

#### 4. `PuzzleReplayState` instance methods — same file

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 4.1 | `advanceToFrame(0)` applies no events | Grid stays empty |
| 4.2 | `advanceToFrame(totalFrames)` applies all events | All cells colored |
| 4.3 | `advanceToFrame` is monotonic (calling with lower frame has no effect) | Append-only behavior |
| 4.4 | `fillComplete` copies targetColors into displayGrid | Grid matches target |
| 4.5 | `reset` clears grid and resets appliedCount | Grid all -1, count 0 |
| 4.6 | `durationSeconds` scales linearly from 10s (gridSize=20) to 30s (gridSize=100) | Boundary values |

### Priority 2 — Robolectric Tests (Unit, JVM + Android stubs)

These classes use `android.graphics.Color` or `android.graphics.Bitmap` and need Robolectric to run on JVM.

#### 5. `ColorQuantizer` — [ColorQuantizer.kt](../app/src/main/java/com/colorbynumber/app/engine/ColorQuantizer.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 5.1 | Single-color image returns palette of size 1 | Trivial case |
| 5.2 | Two distinct colors survive quantization at HIGH detail | No over-merging |
| 5.3 | Near-duplicate colors (RGB distance < 15) are merged | Merge threshold |
| 5.4 | `DetailLevel` target calculation: LOW on gridSize=10 → 7 colors max | Factor * gridSize clamped to [2, 200] |
| 5.5 | `paletteOrder` matches left-to-right, top-to-bottom first appearance | Ordering correctness |
| 5.6 | Every pixel is assigned a valid palette index | No index out of bounds |
| 5.7 | Deterministic output (seed 42) for same input | Reproducibility |

#### 6. `Pixelator` — [Pixelator.kt](../app/src/main/java/com/colorbynumber/app/engine/Pixelator.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 6.1 | Square image is not cropped | Output dimensions match gridSize |
| 6.2 | Landscape image is center-cropped to square | Correct offset calculation |
| 6.3 | Portrait image is center-cropped to square | Correct offset calculation |
| 6.4 | `extractPixels` returns correct pixel count | gridSize * gridSize elements |
| 6.5 | `toGreyscale` produces uniform RGB channels | Each pixel has r == g == b |
| 6.6 | `toGreyscale` lightens by 60% formula | Spot-check known input → expected output |
| 6.7 | `preparePreviewSource` downsamples large images to maxSize | Output <= maxSize |
| 6.8 | `preparePreviewSource` does not upscale small images | Output unchanged |

### Priority 3 — Data Layer (Unit Tests with MockK)

#### 7. `Converters` — [Converters.kt](../app/src/main/java/com/colorbynumber/app/data/Converters.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 7.1 | `IntArray → ByteArray → IntArray` round-trips correctly | Data integrity |
| 7.2 | Empty IntArray round-trips | Edge case |
| 7.3 | `List<Int> → String → List<Int>` round-trips | Comma-separated format |
| 7.4 | Blank string → empty list | Edge case |
| 7.5 | `PuzzleStatus` enum round-trips | IN_PROGRESS and COMPLETED |
| 7.6 | `PlacementEventType` enum round-trips | PLACE and ERASE |

#### 8. `PuzzleRepository` — [PuzzleRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleRepository.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 8.1 | `createPuzzle` serializes state correctly and sets activePuzzleId | DAO receives correct entity |
| 8.2 | `loadPuzzle` restores user progress into PuzzleState | userColors, settings, prefillCount restored |
| 8.3 | `loadPuzzle` returns null for nonexistent ID | Null handling |
| 8.4 | `recordEvent` buffers events below threshold | No DAO call until threshold |
| 8.5 | `recordEvent` auto-flushes at FLUSH_THRESHOLD (50) | DAO.insertAll called |
| 8.6 | `recordEvent` with no activePuzzleId is no-op | No crash, no buffer growth |
| 8.7 | `flush` writes all buffered events and clears buffer | DAO called, buffer empty after |
| 8.8 | `flush` on empty buffer is no-op | No DAO call |
| 8.9 | `snapshotUserColors` updates entity with current state | Verify DAO.update args |
| 8.10 | `markCompleted` flushes, then sets status to COMPLETED | Flush + update order |
| 8.11 | `deletePuzzle` clears activePuzzleId when deleting active puzzle | State cleanup |
| 8.12 | `intArrayToBytes` / `bytesToIntArray` round-trip (private, test via public methods) | Serialization correctness |

#### 9. `PixelArtRepository` — [PixelArtRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PixelArtRepository.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 9.1 | `save` serializes PixelArtState correctly | cellColors, selectedColor, recentColors |
| 9.2 | `loadState` restores all fields into new PixelArtState | Cells, selectedColor, recentColors |
| 9.3 | `loadState` handles null selectedColor and null recentColors | Nullable fields |
| 9.4 | `update` writes current state to existing entity | DAO.update called with correct data |
| 9.5 | `delete` calls DAO deleteById | Pass-through |

#### 10. `GalleryRepository.parsePuzzle` (dense and sparse) — [GalleryRepository.kt](../app/src/main/java/com/colorbynumber/app/data/GalleryRepository.kt)

| # | Test Case | Behavior Verified |
|---|-----------|-------------------|
| 10.1 | Dense JSON parsed correctly | name, gridSize, palette, paletteOrder, targetColors |
| 10.2 | Sparse JSON parsed correctly | Palette built with white at index 0, prefillIndices computed |
| 10.3 | Sparse JSON out-of-bounds entries are skipped | row/col >= gridSize ignored |
| 10.4 | `toPuzzleState` pre-fills correct cells and sets prefillCount | userColors match target at prefill indices |
| 10.5 | Dense JSON with no name field uses default | Fallback name |

*Note: `fetchPuzzles()` itself (HTTP call) should not be unit-tested. Mock it or test manually.*

### Priority 4 — Integration Tests (Instrumented, optional)

These require a real Room database on an emulator/device. Lower priority because the DAOs are generated by Room and the repositories are well-covered by mocked unit tests above.

| # | Target | Behavior Verified | Type |
|---|--------|-------------------|------|
| 11.1 | `AppDatabase` migrations | DB migrates from v1→v2→v3 without data loss | Integration |
| 11.2 | `SavedPuzzleDao` CRUD | Insert, query, update, delete work end-to-end | Integration |
| 11.3 | `PlacementEventDao` cascade delete | Deleting puzzle removes all events | Integration |
| 11.4 | `SavedPixelArtDao` CRUD | Insert, query, update, delete | Integration |

### Priority 5 — UI Tests (Instrumented, lowest priority)

| # | Target | Behavior Verified | Type |
|---|--------|-------------------|------|
| 12.1 | PuzzleScreen tap-to-color | Tapping a cell with selected color fills it | UI |
| 12.2 | PuzzleScreen completion detection | Filling last cell triggers onComplete | UI |
| 12.3 | HistoryScreen puzzle list | Saved puzzles appear in grid | UI |

---

## What Not to Test

| Area | Reason |
|------|--------|
| **Composable UI rendering** (HomeScreen, ConfigScreen, CameraScreen, CompletionScreen, ColorPickerSheet) | Thin UI wrappers over state. Manual testing is more cost-effective. Compose UI tests are slow and brittle for layout-only code. |
| **CameraX integration** | Hardware-dependent; not meaningfully testable in CI. |
| **Navigation routing in MainActivity** | Large stateful composable with screen enum switching. Would require heavy mocking for minimal value. Test manually. |
| **Theme.kt** | Declarative color definitions; nothing to assert. |
| **GalleryRepository.fetchPuzzles (network call)** | Tests would be flaky. Parse logic is tested separately (10.1–10.5). |
| **PuzzleReplayPlayer (Composable)** | Animation timing is difficult to test reliably. The underlying PuzzleReplayState logic is fully covered (3.x, 4.x). |
| **Gesture handler logic in PuzzleScreen/PixelArtScreen** | Complex pointer-input flows tied to Compose gesture APIs. Would require instrumented tests with simulated touch sequences — high cost, better covered by manual QA. |
| **AppSettings** | Thin SharedPreferences wrapper with three boolean fields. No logic to test. |
| **Resource files** (strings.xml, colors.xml, drawables) | Static declarations. |

---

## Suggested Dependency Additions (build.gradle.kts)

```kotlin
// Test dependencies
testImplementation("junit:junit:4.13.2")
testImplementation("org.robolectric:robolectric:4.11.1")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("com.google.truth:truth:1.1.5")

// Instrumented test dependencies (only if pursuing Priority 4+)
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.room:room-testing:2.6.1")
```

---

## Implementation Order

1. Add test dependencies to build.gradle.kts
2. Write PuzzleState tests (highest value, zero setup complexity)
3. Write PixelArtState tests (same — pure Kotlin)
4. Write PuzzleReplayState tests (pure Kotlin + filterCorrectEvents)
5. Write Converters tests (pure Kotlin, ByteBuffer only)
6. Add Robolectric, write ColorQuantizer tests
7. Write Pixelator tests (Robolectric for Bitmap)
8. Write PuzzleRepository tests (MockK for DAOs)
9. Write PixelArtRepository tests (MockK)
10. Write GalleryRepository parse tests (Robolectric for Color.parseColor)
11. (Optional) Room integration tests for migrations and cascade deletes
