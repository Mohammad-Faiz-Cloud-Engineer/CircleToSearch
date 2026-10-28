# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-10-28

### Added
- Edge docking auto-hide: ball automatically tucks half of itself into the screen edge and dims to 60% opacity after 2 seconds of inactivity, unhiding instantly upon touch.

### Performance
- Eliminated all heap object allocations during lasso drawing by replacing dynamic `PointF` lists with primitive float tracking for 120Hz smooth touch drawing.
- Offloaded full-screen bitmap copying and pixel sampling from the main UI thread to `Dispatchers.Default` background worker thread, preventing frame drops.
- Cached text width measurements and `touchSlop` lookups to eliminate redundant per-frame calculations in render and drag loops.

### Fixed
- Fixed floating ball initial placement on first enable by defaulting X coordinate to screen edge (1.0f) instead of an inward offset (0.92f).
- Prevented `Canvas: trying to use a recycled bitmap` race condition when touch completion races with queued frame drawing in `CircleSelectionView`.
- Added `IOException` recovery to Preferences DataStore stream in `SettingsRepository` to prevent permanent disconnection of settings observation.
- Added 5-second capture watchdog timer in `BallAccessibilityService` to restore floating ball visibility if OS screenshot IPC hangs.
- Resolved Android 13+ (API 33+) deprecations for package and activity resolution using `ApplicationInfoFlags` and `ResolveInfoFlags`.
- Deduplicated `"Circle an item  ·  Back to cancel"` hint string in `CircleSelectionView`.

### Changed
- Bumped Gradle wrapper to 9.8.2 and updated AndroidX core and Compose dependencies.
- Configured CI/CD workflows for automated PR validation, debug artifact generation, and signed release APK publishing.

## [1.0.0] - 2026-10-28

### Added
- Floating accessibility ball overlay with configurable size and live preview.
- Drag-and-drop ball repositioning with automatic snap-to-edge.
- Screen capture via Android Accessibility Service (`canTakeScreenshot`).
- Custom `CircleSelectionView` allowing drawing of circle/lasso paths to select screen regions.
- Seamless intent handoff to Google Lens (`ACTION_SEND`) via scoped `FileProvider`.
- System fallback to Android share chooser if the Google app is unavailable.
- Responsive Material 3 settings interface adapting to expanded and compact screens.

### Privacy & Security
- Completely offline: zero internet permissions, zero analytics, zero trackers.
- Scoped cache directory (`cacheDir/lens/`) with automatic 60-second file expiration.
- Auto-cleanup on screen off, service startup, and service shutdown.
- Blank/black screen detection for DRM and `FLAG_SECURE` protected content.
