# Color by Number — Project Status

## Active Work

| Item | Status | Notes |
|------|--------|-------|
| — | — | — |

## Open Items

### Not Done (General)
- Cells should not be colorable in greyscale mode
- Add transition animation to/from greyscale
- When exiting a started public gallery puzzle, move it to history
- Delete should keep the same scroll position
- Add global 'disable nav' setting
- Add visual indicator when a color is fully complete
- Add color autocomplete feature
- Remove greyscale preview from pixel art creation (canvas size dialog)
- Make grid lines thicker in all play screens (PuzzleScreen, PixelArtScreen)

### Not Done (Needed for Release)
- Add puzzles to public gallery
- Rework home page
- Update README
- Make completed puzzles exportable

## Closed Items

- Add project context document
- Add project management files (status.md, manifest.md, instructions.md)
- Make completed puzzles downloadable
- Add ability to delete completed puzzles
- Add free pixel art mode
- Add delay to first painting mode cell
- Change cell navigator default threshold
- Config screen performance optimization
- Add puzzle replay system
- Add sparse puzzle support
- Add prevent-errors mode
- Add prevent-overwrite mode
- Add color navigator arrow
- Add pinch-to-zoom grid navigation
- Add vibration feedback
- Add public gallery screen
- Add history screen with saved puzzles and replay
- Add camera screen for photo capture
- Add color quantizer (k-means clustering for palette reduction)
- Add save/load puzzle progress (IN_PROGRESS/COMPLETED states)
- Make pixel art saveable

---

## Project Summary

**Color by Number** is an Android app (Kotlin + Jetpack Compose) that converts photos into interactive color-by-number puzzles. Users photograph or select an image, configure grid size and color detail, then fill in numbered grid cells to reveal the image. The app also includes a free-draw Pixel Art mode, a public puzzle gallery, puzzle replay, and history.

**Tech Stack**: Kotlin, Jetpack Compose (Material 3), Room/SQLite, CameraX, Navigation Compose
**Min SDK**: 26 | **Target SDK**: 34
**Written entirely using Claude Code.**
