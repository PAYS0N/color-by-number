# Color by Number — Settings

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
