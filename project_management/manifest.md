# Project Manifest

Color by Number is an Android app (Kotlin, Jetpack Compose, Material 3) that converts photos into interactive color-by-number puzzles. Users capture or select an image, configure grid size and color detail level, then fill in numbered grid cells to recreate the image. The app stores puzzles locally via Room/SQLite, supports puzzle replay by recording cell placement events, offers a free-draw Pixel Art mode, and connects to a public gallery of pre-made puzzles hosted on GitHub. All image processing is local; the app is free and ad-free.

---

## Project Config

| File | Description |
|------|-------------|
| [build.gradle.kts](../build.gradle.kts) | Root-level Gradle build configuration |
| [app/build.gradle.kts](../app/build.gradle.kts) | App module dependencies, SDK versions, and build settings |
| [gradle.properties](../gradle.properties) | JVM args, AndroidX, Kotlin code style, and R class flags |
| [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | Gradle wrapper version and distribution URL |
| [local.properties](../local.properties) | Local Android SDK path (not tracked in git) |
| [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml) | App manifest: MainActivity, camera/internet/vibrate permissions, file provider |

---

## Project Context

| File | Description |
|------|-------------|
| [CLAUDE.md](../CLAUDE.md) | Project rules and guidelines for Claude and file management |
| [project_management/status.md](status.md) | Active work, open items, and closed items tracking |
| [project_management/manifest.md](manifest.md) | This file — full project file listing with descriptions |
| [project_management/cdocs/app.md](cdocs/app.md) | App purpose, user flow, navigation, and tech stack |
| [project_management/cdocs/architecture.md](cdocs/architecture.md) | Architecture pattern, data flow, and database overview |
| [project_management/cdocs/pipeline.md](cdocs/pipeline.md) | Puzzle generation pipeline and replay system |
| [project_management/cdocs/data.md](cdocs/data.md) | Data model entities, persistence/event recording, and public gallery |
| [project_management/cdocs/settings.md](cdocs/settings.md) | Global and per-puzzle settings |
| [project_management/cdocs/ui.md](cdocs/ui.md) | UI screen descriptions and critical file paths |
| [project_management/testing-plan.md](testing-plan.md) | Prioritized unit testing plan: test cases, frameworks, and implementation order |
| [project_management/cdoc.md](cdoc.md) | Template instructions for generating context documents |
| [project_management/prompting.md](prompting.md) | Template instructions for generating task prompts |

---

## Root Files

| File | Description |
|------|-------------|
| [README.md](../README.md) | Project overview, features, open-source notice, and Play Store publishing guide |
| [index.html](../index.html) | Simple privacy policy page stating no user data is collected |

---

## App Entry & Navigation

| File | Description |
|------|-------------|
| [MainActivity.kt](../app/src/main/java/com/colorbynumber/app/MainActivity.kt) | Main activity: bottom tab navigation (Create/Explore/My Work), screen routing, photo capture, puzzle creation, database init |
| [AppSettings.kt](../app/src/main/java/com/colorbynumber/app/AppSettings.kt) | Global SharedPreferences for preventErrors, preventOverwrite, and vibrate settings |

---

## Data Layer

| File | Description |
|------|-------------|
| [AppDatabase.kt](../app/src/main/java/com/colorbynumber/app/data/AppDatabase.kt) | Room database setup with migration support for all entities |
| [SavedPuzzle.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPuzzle.kt) | Entity for a saved puzzle: grid, palette, progress, and completion status |
| [SavedPuzzleDao.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPuzzleDao.kt) | DAO for puzzle CRUD and status-based queries |
| [PlacementEvent.kt](../app/src/main/java/com/colorbynumber/app/data/PlacementEvent.kt) | Entity recording individual cell placements/erases with timestamps |
| [PlacementEventDao.kt](../app/src/main/java/com/colorbynumber/app/data/PlacementEventDao.kt) | DAO for inserting and querying placement events by puzzle ID |
| [PlacementEventType.kt](../app/src/main/java/com/colorbynumber/app/data/PlacementEventType.kt) | Enum distinguishing PLACE vs ERASE event types |
| [PuzzleRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleRepository.kt) | Orchestrates puzzle persistence, event buffering/flushing, and replay loading |
| [SavedPixelArt.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPixelArt.kt) | Entity for a saved pixel art creation: grid size and cell color array |
| [SavedPixelArtDao.kt](../app/src/main/java/com/colorbynumber/app/data/SavedPixelArtDao.kt) | DAO for pixel art CRUD operations |
| [PixelArtRepository.kt](../app/src/main/java/com/colorbynumber/app/data/PixelArtRepository.kt) | Orchestrates pixel art persistence: save, update, load, and delete |
| [PuzzleReplayState.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayState.kt) | State object for replay: grid, palette, and filtered correct placement events |
| [PuzzleReplayPlayer.kt](../app/src/main/java/com/colorbynumber/app/data/PuzzleReplayPlayer.kt) | Animates puzzle replay by stepping through placement events |
| [GalleryRepository.kt](../app/src/main/java/com/colorbynumber/app/data/GalleryRepository.kt) | Fetches and parses public gallery puzzles from GitHub (dense/sparse JSON formats) |
| [Converters.kt](../app/src/main/java/com/colorbynumber/app/data/Converters.kt) | Room type converters for IntArray, ByteArray, and enum serialization |

---

## Engine

| File | Description |
|------|-------------|
| [PuzzleState.kt](../app/src/main/java/com/colorbynumber/app/engine/PuzzleState.kt) | Core game state: target colors, user colors, palette, cell coloring, and completion logic |
| [Pixelator.kt](../app/src/main/java/com/colorbynumber/app/engine/Pixelator.kt) | Image processing: square crop, downscale, pixel extraction, and greyscale conversion |
| [ColorQuantizer.kt](../app/src/main/java/com/colorbynumber/app/engine/ColorQuantizer.kt) | Palette reduction via k-means clustering with RGB distance-based color merging |
| [PixelArtState.kt](../app/src/main/java/com/colorbynumber/app/engine/PixelArtState.kt) | Freeform pixel art canvas state: cell colors, selected color, and recent color history |

---

## UI Screens

| File | Description |
|------|-------------|
| [HomeScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HomeScreen.kt) | Create tab: card layout with Take Photo, Pick from Gallery, and Pixel Art actions |
| [CameraScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CameraScreen.kt) | CameraX preview with pinch-to-zoom and photo capture |
| [ConfigScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ConfigScreen.kt) | Puzzle configuration: grid size slider, detail level, and greyscale preview |
| [PuzzleScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PuzzleScreen.kt) | Main gameplay: grid rendering, color selection, palette bar, and settings |
| [CompletionScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/CompletionScreen.kt) | Completion celebration screen with final image display and replay button |
| [HistoryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/HistoryScreen.kt) | My Work tab: combined grid of saved puzzles and pixel art; delete/download/replay for puzzles; resume/delete dialog for pixel art |
| [GalleryScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/GalleryScreen.kt) | Explore tab: public gallery screen fetching and displaying remote puzzles |
| [PixelArtScreen.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/PixelArtScreen.kt) | Freeform pixel art canvas with color picker, eraser, painting tools, save button, and save-on-back prompt |
| [ColorPickerSheet.kt](../app/src/main/java/com/colorbynumber/app/ui/screens/ColorPickerSheet.kt) | Modal bottom sheet HSV color picker with saturation/value pad and hue slider |

---

## UI Theme

| File | Description |
|------|-------------|
| [Theme.kt](../app/src/main/java/com/colorbynumber/app/ui/theme/Theme.kt) | Material 3 theme with light color scheme (primary indigo, secondary blue-grey) |

---

## Resources

| File | Description |
|------|-------------|
| [res/values/strings.xml](../app/src/main/res/values/strings.xml) | String resources (app name) |
| [res/values/colors.xml](../app/src/main/res/values/colors.xml) | Color resource definitions |
| [res/values/themes.xml](../app/src/main/res/values/themes.xml) | Theme styling configuration |
| [res/drawable/ic_launcher_foreground.xml](../app/src/main/res/drawable/ic_launcher_foreground.xml) | App icon foreground vector drawable |
| [res/drawable/ic_launcher_background.xml](../app/src/main/res/drawable/ic_launcher_background.xml) | App icon background drawable |
| [res/mipmap-hdpi/ic_launcher.xml](../app/src/main/res/mipmap-hdpi/ic_launcher.xml) | Adaptive icon config for hdpi |
| [res/mipmap-hdpi/ic_launcher_round.xml](../app/src/main/res/mipmap-hdpi/ic_launcher_round.xml) | Rounded adaptive icon config for hdpi |
| [res/xml/file_paths.xml](../app/src/main/res/xml/file_paths.xml) | FileProvider cache path config for sharing files |

---

## Data Files

| File | Description |
|------|-------------|
| [data/puzzle1.json](../data/puzzle1.json) | "Pine Tree" sample puzzle: 20x20 grid, 5-color palette, target color indices |
| [data/puzzle2.json](../data/puzzle2.json) | Second sample puzzle in sparse JSON format, a pizza slice. |
| [data/puzzle3.json](../data/puzzle3.json) | Third sample puzzle in sparse JSON format, a strawberry. |
