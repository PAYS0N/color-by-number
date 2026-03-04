# Color by Number

Turn any photo into a color-by-number puzzle.

## Features (MVP)

- Take a photo or pick from gallery
- Adjustable grid size (20×20 to 100×100)
- Color detail level: Low / Medium / High
- Greyscale preview hides the image until you color it
- Tap cells to fill with the selected palette color
- Scrolling palette bar that hides completed colors
- Eraser tool
- Prevent Errors mode (default on): blocks wrong color placement
- Prevent Overwrite mode (default on): protects correctly colored cells
- Pinch-to-zoom with pan on the puzzle grid
- Selected color highlights matching cells on the grid
- Completion screen reveals the full-color image

## Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps
1. Open this folder in Android Studio ("Open an existing project")
2. Let Gradle sync (first time takes a few minutes)
3. Run on a device or emulator: Run > Run 'app'

## Publish to Google Play Store

### One-time setup
1. Create a Google Play Developer account ($25 one-time fee) at https://play.google.com/console
2. Create a privacy policy page — can be a simple GitHub Pages site or Google Doc stating "This app does not collect or transmit any user data. All photos are processed locally on-device."

### Generate a signed release bundle
1. In Android Studio: Build > Generate Signed Bundle / APK
2. Choose "Android App Bundle"
3. Create a new keystore (or use existing):
   - Key store path: choose a secure location (BACK THIS UP — you can never update the app without it)
   - Set passwords, alias, validity (25 years is fine)
4. Select "release" build type
5. Finish — the AAB file will be in `app/release/`

### Upload to Play Console
1. Go to Play Console > Create app
2. Fill in app name: "Color by Number"
3. Set app category: Games > Puzzle
4. Content rating: complete the questionnaire (no violence, no user data = easy)
5. Under "Release" > "Production" > "Create new release"
6. Upload the `.aab` file
7. Add the privacy policy URL
8. Fill in the store listing:
   - Short description: "Turn any photo into a color-by-number puzzle"
   - Full description: whatever you want
   - Screenshots: take a few on an emulator
   - Feature graphic: 1024x500 image (can be simple)
   - App icon: auto-generated from the adaptive icon
9. Submit for review (typically takes a few hours to a couple days)

## Project Structure

```
app/src/main/java/com/colorbynumber/app/
├── MainActivity.kt              # Single-activity navigation
├── engine/
│   ├── Pixelator.kt             # Square crop + downscale
│   ├── ColorQuantizer.kt        # K-means + RGB distance merge
│   └── PuzzleState.kt           # Game state + rules
└── ui/
    ├── theme/Theme.kt           # Material 3 theme
    └── screens/
        ├── HomeScreen.kt        # Landing page
        ├── CameraScreen.kt      # CameraX capture
        ├── ConfigScreen.kt      # Grid size + detail config
        ├── PuzzleScreen.kt      # Main game grid + palette
        └── CompletionScreen.kt  # Victory screen
```

## Deferred Features
- Save/resume puzzles (Room database)
- Pre-made puzzle database / browsing
- Undo/redo
