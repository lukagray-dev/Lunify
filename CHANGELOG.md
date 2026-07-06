# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to Semantic Versioning.

## [0.1.0-alpha.1] - 2026-07-06

### Added
- Rebranded application to **Lunify** with updated assets, package name structure (`com.android.lunify`), and a global Google Sans typography layout.
- Implemented offline audio and video playback service (`MusicService`) powered by ExoPlayer (Media3), supporting play, pause, next/prev, seek, and background controls.
- Added playback progress, metadata resolution, and custom player bottom sheet sheet controls.
- Added play count tracking for all songs using a persistent `PlayCountManager` model.
- Developed an online **Browse** feed page using category switchers, horizontal carousels, and quick pick grid card systems to view YouTube streams.
- Integrated a WiFi Direct and WebRTC-based **Duo** playback sync system to synchronize song lists, video lists, and player states across two partner devices.
- Refactored Duo Chat UI using native XML layouts with custom bubble layouts and native base64 voice message player compatibility.
- Added a full **Download** page with link input/extraction capabilities utilizing `youtubedl-android` (yt-dlp).

### Fixed
- Unified all top search bar alignment structures, padding horizontal (12dp) / vertical (8dp), left header heights (48dp), and search bar heights (44dp) across My Music, Browse, Duo, and Downloads tabs for symmetry.
- Standardized TabLayout heights to 48dp with transparent backgrounds and scrollable modes.
- Added vertical centering gravity to all search/input edit text styles.
- Fixed horizontal/vertical centering and disabled font padding on the Browse platform name text and My Music shuffle buttons to align texts with icons.
- Redefined list items (songs, videos, folders, artists, albums) to use fixed 72dp height, vertically centered details, and dividers aligned cleanly at the bottom.
