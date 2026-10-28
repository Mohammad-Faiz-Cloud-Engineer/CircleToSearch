# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
