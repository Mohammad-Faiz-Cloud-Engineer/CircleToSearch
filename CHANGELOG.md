# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-10-28

### Added
- Edge docking auto-hide: ball automatically tucks half of itself into the screen edge and dims to 60% opacity after 2 seconds of inactivity, unhiding instantly upon touch.
- Android 12+ data extraction rules (`data_extraction_rules.xml`) and Android 11 backup content rules (`backup_rules.xml`) enforcing zero cloud-backup and zero device-transfer data leakage across all supported API levels.
- Monochrome adaptive icon layer (`ic_launcher.xml`) enabling Material You dynamic wallpaper theming on Android 13+.
- Predictive back gesture support via `OnBackInvokedCallback` on Android 13+ (API 33+) for canceling selection.
- Dynamic display cutout and status bar inset handling to safely position the cancel pill around camera notches and hole-punches.
- Comprehensive unit test suite (`SettingsStateTest`) covering edge-docking math, bounds clamping, auto-hide offsets, division-by-zero protection, and jitter rejection.
- Local release signing template (`signing.properties.example`).
- Explanatory user feedback toast when Google app is present but unable to handle the share intent before launching the chooser fallback.

### Performance
- Eliminated all heap object allocations during lasso drawing by replacing dynamic `PointF` lists with primitive float tracking for 120Hz smooth touch drawing.
- Offloaded full-screen bitmap copying and pixel sampling from the main UI thread to `Dispatchers.Default` background worker thread, preventing frame drops.
- Replaced boxed `mutableStateOf` with primitive `mutableFloatStateOf` for ball size slider in settings, eliminating autoboxing memory allocations during dragging.
- Cached text width measurements and `touchSlop` lookups to eliminate redundant per-frame calculations in render and drag loops.

### Fixed
- Fixed fatal `NoSuchMethodError` startup crash on Android 11 & 12 (API 30–32) by guarding 3-parameter `registerReceiver` with an API 33 (`TIRAMISU`) check.
- Fixed `assembleRelease` build failure when compiling without local signing credentials by making release signing configuration conditional on key presence.
- Fixed floating ball detaching from screen edge during real-time size adjustments by preserving edge-snap anchors on resize.
- Fixed floating ball initial placement on first enable by defaulting X coordinate to screen edge (1.0f) instead of an inward offset (0.92f).
- Prevented `Canvas: trying to use a recycled bitmap` race condition when touch completion races with queued frame drawing in `CircleSelectionView`.
- Prevented double-recycle crash by adding a safe copy fallback when a full-screen crop returns the source bitmap instance.
- Added `IOException` recovery to Preferences DataStore stream in `SettingsRepository` to prevent permanent disconnection of settings observation.
- Added 5-second capture watchdog timer in `BallAccessibilityService` to restore floating ball visibility if OS screenshot IPC hangs.
- Removed redundant Android 10 (`Q`) version check in `BallAccessibilityService` for system gesture exclusions on API 30+ codebase.
- Modernized screen dimension calculation using `WindowManager.maximumWindowMetrics` replacing deprecated display metrics.
- Resolved Android 13+ (API 33+) deprecations for package and activity resolution using `ApplicationInfoFlags` and `ResolveInfoFlags`.
- Added accessibility `performClick()` support on `CircleSelectionView` to ensure full compatibility with screen readers and TalkBack.
- Deduplicated `"Circle an item  ·  Back to cancel"` hint string in `CircleSelectionView`.
- Fixed `ci.yml` workflow trigger by adding the `push` event so automated tests and lint checks execute on every push as well as pull requests.

### Changed
- Migrated source code tree structure from `src/main/java` and `src/test/java` to `src/main/kotlin` and `src/test/kotlin`.
- Synchronized min/max ball size bounds (`32dp` to `96dp`) between settings repository and UI sliders.
- Enabled Gradle wrapper distribution URL validation (`validateDistributionUrl=true`).
- Bumped Gradle wrapper to 9.8.2 and updated AndroidX core and Compose dependencies.
- Configured CI/CD workflows for automated PR validation, debug artifact generation, and signed release APK publishing.
- Redesigned architecture diagram in README with modern gradient theme.

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
