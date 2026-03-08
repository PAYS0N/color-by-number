# Color by Number

Turn any photo into a color-by-number puzzle. Take a photo or pick one from your gallery, choose a grid size and color detail level, and the app pixelates and quantizes it into a numbered grid for you to fill in. Features include pinch-to-zoom, a scrolling palette bar that hides completed colors, prevent-errors and prevent-overwrite modes, a color navigator arrow, saved puzzles with replay, and a public gallery of pre-made puzzles.

This project is open source — feel free to fork it or open a pull request. The only condition is that any distributed version must remain free and ad-free.

This app was written entirely using Claude Code with no manual coding. The project uses a structured Claude Code workflow to keep the codebase navigable:

- **[CLAUDE.md](CLAUDE.md)** — project rules and conventions that Claude reads at the start of every session
- **[project_management/manifest.md](project_management/manifest.md)** — a full file listing with descriptions; Claude updates this whenever files are added or removed
- **[project_management/status.md](project_management/status.md)** — open and active work items tracked across sessions
- **[project_management/cdocs/](project_management/cdocs/)** — context documents covering the app's architecture, data model, UI, pipeline, and settings; Claude updates only the affected file after each change
- **[project_management/prompting.md](project_management/prompting.md)** — template for generating task prompts that include all relevant context for a new session

## Instructions to publish to Google Play Store (remove later)

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
