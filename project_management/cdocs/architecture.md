# Color by Number — Architecture

## Architecture

**Pattern**: MVVM-like with state-driven UI. MainActivity holds all screen navigation state and puzzle/event callback registration. Navigation uses a `Screen` enum with `currentScreen` mutableState, plus a `Tab` enum with `selectedTab` for bottom tab navigation (Create, Explore, My Work). A Material 3 `Scaffold` with `NavigationBar` provides the persistent bottom tabs; the bar is hidden on transient screens. PuzzleRepository orchestrates persistence and event buffering. No dependency injection framework; components are instantiated directly in onCreate.

**Data Flow**:
1. Photo capture or selection → Bitmap
2. Pixelator: square crop, downscale to grid size
3. ColorQuantizer: reduce unique colors via k-means, merge near-duplicates, assign pixel indices
4. PuzzleState: immutable grid + palette, mutable user colors and settings
5. MainActivity: attaches event recording callback to PuzzleState
6. PuzzleRepository: buffers events in memory, flushes on threshold or explicit call, snapshots user colors on pause
7. Room/SQLite: persists SavedPuzzle entity and PlacementEvent rows

**Database**: Single AppDatabase (version 3) with SavedPuzzleDao, PlacementEventDao, and SavedPixelArtDao. Entities auto-convert between IntArray/ByteArray via Room type converters. Foreign key cascade on puzzle delete. PixelArtRepository handles pixel art CRUD independently of PuzzleRepository.
