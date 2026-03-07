# Color by Number — App & Tech Stack

## App Purpose & User Flow

Color by Number is an Android app that converts photos into interactive color-by-number puzzles. Users follow one of three paths: (1) capture or select a photo, configure grid size (8–100 pixels) and color detail level, then solve the puzzle by tapping cells and selecting colors; (2) browse and play pre-made puzzles from a public gallery (GitHub-hosted); (3) draw pixel art freeform on a blank canvas. Completed puzzles can be replayed, downloaded, or deleted. All image processing is local; the app is free and ad-free.

**Navigation**: Bottom tab navigation with three tabs: Create (camera, gallery pick, pixel art), Explore (public gallery), and My Work (history). Transient screens (Camera, Config, Puzzle, Complete, PixelArt) push onto the stack and hide the bottom bar. Puzzle back navigates to the originating tab; save-and-back flows (puzzle, pixel art) navigate to My Work. Completion screen auto-transitions to My Work after replay is viewed.

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
