<div align="center">

<img src="assets/logo.svg" alt="Lunify Logo" width="120" height="120" />

# **Lunify**

### *A modern, ad-free YouTube Music client & offline audio player for Android.*

Inspired by **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)**.

</div>

---

## **What is Lunify?**

**Lunify** is an unofficial YouTube Music client and offline music player built with Jetpack Compose and Android Media3 (ExoPlayer). It allows you to discover and stream high-quality audio directly from YouTube Music without advertisements or subscription limits, manage and play your local audio library, download songs for offline playback, and listen in real-time sync with someone special via **Duo**.

> ***Pure music streaming. No ads. No subscriptions. No distractions.***

---

## Features

### Ad-Free YouTube Music Streaming
- **Native InnerTube Integration**: Fast, ad-free music search, query suggestions, and browsing for songs, albums, and artists.
- **Dynamic Home Discovery**: Instant Quick Picks, trending releases, community playlists, and top artist discographies.
- **Infinite Radio & Auto-Queue**: Automatically fetches and appends related radio tracks when your current queue finishes.

### High-Fidelity Audio Extraction & Smart Caching
- **Fast Stream Resolution**: Ultra-fast stream extraction powered by PipePipeExtractor, selecting the highest-bitrate audio streams available.
- **Media3 LRU Cache**: 250MB local disk cache for seamless instant re-playback and reduced bandwidth consumption.

### Synchronized Lyrics
- **Real-Time Word/Line Sync**: Synchronized time-stamped lyrics sourced online via [LRCLIB](https://lrclib.net).
- **Offline Companion Support**: Automatic detection and loading of companion `.lrc` files located alongside local tracks.

### Offline Music Player
- **MediaStore Audio Discovery**: Automatically discovers and indexes all audio tracks stored on your device (MP3, FLAC, M4A, AAC, WAV, OGG).
- **Folder Playlists**: Automatically detects music folders as playlists for backward compatibility with manual music libraries.
- **Smart Sorting**: Sort downloaded songs by Name, Date Added, Times Played, or Duration.
- **Real-Time Storage Monitor**: Displays exact storage space consumed by your downloaded music files.

### Direct Music Downloader
- **One-Tap Offline Download**: Download any online music track directly to your device's music storage for offline listening anytime.

### Duo — Listen Together
- **The marquee differentiator**: Listen to the same playlist in perfect real-time synchronization with another person, no matter where you are.
- **Long-Distance Sync (Online)**: Low-latency P2P playback sync over WebRTC DataChannels.
- **Offline / Nearby Sync**: Local connection via Wi-Fi Direct with zero internet or mobile data required.

### Liquid Glass Design System
- Modern frosted glass interface built with pure Jetpack Compose.
- Dynamic ambient glow reflecting album artwork colors.
- Floating MiniPlayer with pull-down dismissal gesture.
- Immersive Fullscreen Player with synced seek bar, up next queue sheet, and smooth screen transitions.

---

## Getting Started

Pre-release binaries are available for testing. You can download the latest signed APK directly from the [GitHub Releases](https://github.com/lukagray-dev/Lunify/releases) page:

1. Navigate to the [Releases](https://github.com/lukagray-dev/Lunify/releases) section.
2. Download the `lunify-v0.1.0-alpha.1.apk` asset.
3. Open and install the APK on your Android device (ensure installation from unknown sources is allowed in your device settings).

---

## Tech Stack & Open-Source Acknowledgments

Lunify is built with modern Android architecture and is deeply inspired by:

- **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** — For architectural inspiration and demonstration of unofficial YouTube Music streaming.
- **[PipePipeExtractor](https://github.com/maxrave-dev/PipePipeExtractor)** — High-performance YouTube audio stream extraction.
- **[LRCLIB](https://lrclib.net)** — Synchronized lyrics API provider.
- **[AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3)** — Reliable audio playback engine and stream caching.
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Declarative Android UI and custom Liquid Glass canvas rendering.

---

## Contributing

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push and open a Pull Request

---

## License

Lunify is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**. See [LICENSE](./LICENSE) for full terms.

---

<div align="center">

***Made with passion for music lovers and the people they miss.***

</div>
