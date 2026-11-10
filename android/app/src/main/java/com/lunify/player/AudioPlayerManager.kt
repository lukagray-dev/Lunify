package com.lunify.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lunify.data.local.DownloadQuality
import com.lunify.data.local.LibraryStorage
import com.lunify.data.local.SettingsStorage
import com.lunify.data.local.StreamingQuality
import com.lunify.data.repository.OnlineLyricsRepository
import com.lunify.data.repository.OnlineMusicRepository
import com.lunify.domain.model.DuoAction
import com.lunify.domain.model.PlaybackProgress
import com.lunify.domain.model.PlaybackSource
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.PlayerStatus
import com.lunify.domain.model.RepeatMode
import com.lunify.domain.model.Track
import com.lunify.player.download.ChunkedAudioDownloader
import com.lunify.player.download.DownloadNotificationManager
import com.lunify.player.service.LunifyMediaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ==============================================================================
 * Lunify - Audio Player Manager (AudioPlayerManager.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the heartbeat of audio playback in Lunify!
 *
 * WHAT DOES THIS CLASS DO?
 * ------------------------
 * This manager is the single source of truth for audio playback throughout the
 * entire application. It wraps Google's production-grade `androidx.media3.exoplayer`,
 * configuring it strictly for lightweight, highly reliable audio playback
 * across both offline local tracks and online YouTube Music streaming.
 *
 * KEY FEATURES IMPLEMENTED HERE:
 * 1. ExoPlayer Lifecycle & Audio Focus: Configured with `AUDIO_CONTENT_TYPE_MUSIC`
 *    and automatic audio focus handling (so if a phone call comes in, music pauses!).
 * 2. Media3 Caching: Backed by [PlayerCacheManager]'s [SimpleCache] and
 *    [CacheDataSource.Factory] so online streams are buffered to disk with LRU
 *    eviction, enabling instant replay and zero repeat bandwidth usage.
 * 3. Dynamic Stream Resolution: For online tracks, resolves high-bitrate audio URLs
 *    using [OnlineMusicRepository] and seamlessly begins playback.
 * 4. Infinite Radio Auto-Queue: When playing an online song with a short queue,
 *    automatically queries YouTube Music for related songs and appends them.
 * 5. Smart Transport Controls:
 *    - Play / Pause / Seek.
 *    - Next track: advances queue or loops based on repeat mode.
 *    - Previous track logic:
 *      - If song has played for > 3 seconds (> 3000ms), restart from the beginning (0ms).
 *      - If song has played <= 3 seconds, jump to the previous song in queue.
 * 6. Offline Downloading: Allows downloading any online track to device storage,
 *    registering it into [LibraryStorage] for offline listening.
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
class AudioPlayerManager(
    private val context: Context,
    val libraryStorage: LibraryStorage,
    val settingsStorage: SettingsStorage = SettingsStorage(context),
    private val onlineRepository: OnlineMusicRepository = OnlineMusicRepository(),
    private val downloadNotificationManager: DownloadNotificationManager = DownloadNotificationManager(context),
    private val chunkedDownloader: ChunkedAudioDownloader = ChunkedAudioDownloader(),
    private val onlineLyricsRepo: OnlineLyricsRepository = OnlineLyricsRepository(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressTickerJob: Job? = null
    private var streamResolutionJob: Job? = null
    private var lastNextClickTime = 0L
    private var lastPrevClickTime = 0L

    companion object {
        private const val TAG = "AudioPlayerManager"
    }

    // Configure ExoPlayer purely for audio playback with audio focus and noisy-intent handling
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // Upstream HTTP data source with standard browser headers and timeouts
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setAllowCrossProtocolRedirects(true)

    // Universal data source handling file://, content://, rawresource://, asset://, and HTTP
    private val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    // Cached data source factory using our singleton SimpleCache
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(PlayerCacheManager.getInstance(context))
        .setUpstreamDataSourceFactory(defaultDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // Dynamic data source factory respecting user's Stream Caching toggle in Settings
    private val dynamicDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
        if (settingsStorage.streamCachingEnabled.value) {
            cacheDataSourceFactory.createDataSource()
        } else {
            defaultDataSourceFactory.createDataSource()
        }
    }

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dynamicDataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(settingsStorage.pauseOnDisconnect.value)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    // Internal mutable state flow driving the UI
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    /**
     * Dispatcher callback for Lunify Duo synchronization.
     * When local user plays, pauses, or seeks, this transmits the event to the connected peer.
     */
    var duoSyncDispatcher: ((action: String, track: Track?, positionMs: Long) -> Unit)? = null

    init {
        setupPlayerListener()
        observeSettings()
    }

    /**
     * Reactively synchronizes ExoPlayer parameters with [SettingsStorage] updates.
     */
    private fun observeSettings() {
        scope.launch {
            settingsStorage.skipSilence.collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }
        scope.launch {
            settingsStorage.pauseOnDisconnect.collect { enabled ->
                exoPlayer.setHandleAudioBecomingNoisy(enabled)
            }
        }
    }

    /**
     * Connects Media3 ExoPlayer callbacks to our reactive [PlayerState].
     */
    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val status = when (playbackState) {
                    Player.STATE_IDLE -> PlayerStatus.IDLE
                    Player.STATE_BUFFERING -> PlayerStatus.BUFFERING
                    Player.STATE_READY -> if (exoPlayer.isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    Player.STATE_ENDED -> {
                        // Song completed: automatically move to next song in queue
                        playNext()
                        PlayerStatus.PAUSED
                    }
                    else -> PlayerStatus.IDLE
                }

                _playerState.update { it.copy(status = status) }
                syncProgress()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val status = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                _playerState.update { it.copy(status = status) }

                if (isPlaying) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                    syncProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                _playerState.update { it.copy(status = PlayerStatus.ERROR) }
                stopProgressTicker()
            }
        })
    }

    /**
     * Starts continuous polling of playback position while playing (every 250ms).
     * This provides butter-smooth seekbar movement in both MiniPlayer and FullscreenPlayer.
     */
    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                syncProgress()
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    /**
     * Synchronizes current ExoPlayer position, duration, and buffer into PlayerState.
     */
    private fun syncProgress() {
        val currentMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val durationMs = if (exoPlayer.duration > 0) {
            exoPlayer.duration
        } else {
            _playerState.value.currentTrack?.durationMs ?: 0L
        }
        val bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)

        _playerState.update { state ->
            state.copy(
                progress = PlaybackProgress(
                    currentPositionMs = currentMs,
                    totalDurationMs = durationMs,
                    bufferedPositionMs = bufferedMs,
                ),
            )
        }
    }

    // ==========================================================================
    // PLAYBACK CONTROLS
    // ==========================================================================

    /**
     * Plays a specific track, optionally replacing the current active playlist queue.
     *
     * Handles both:
     * 1. Offline local audio tracks (via contentUri or file:// URI).
     * 2. Online streaming tracks (dynamically resolves stream URL via PipePipe extractor).
     *
     * @param track The song to play.
     * @param newQueue Optional list of tracks forming the active queue/playlist.
     * @param isRemoteSync Whether this command was received from a remote Duo peer (anti-echo flag).
     * @param startPositionMs Optional starting position in milliseconds (for drift compensation).
     */
    fun playTrack(
        track: Track,
        newQueue: List<Track>? = null,
        isRemoteSync: Boolean = false,
        startPositionMs: Long = 0L,
    ) {
        val updatedQueue = if (newQueue != null && newQueue.isNotEmpty()) {
            newQueue
        } else if (_playerState.value.queue.any { it.id == track.id }) {
            _playerState.value.queue
        } else {
            listOf(track)
        }
        val index = updatedQueue.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }

        // Update favorite flag from persistent storage
        val trackWithFavorite = track.copy(isFavorite = libraryStorage.isFavorite(track.id))

        _playerState.update { state ->
            state.copy(
                currentTrack = trackWithFavorite,
                queue = updatedQueue,
                currentIndex = index,
                status = PlayerStatus.BUFFERING,
            )
        }

        // Anti-echo protection: notify Duo sync only if initiated locally
        if (!isRemoteSync) {
            duoSyncDispatcher?.invoke(DuoAction.PLAY, trackWithFavorite, startPositionMs)
        }

        // Cancel any pending stream resolution from previous track taps
        streamResolutionJob?.cancel()

        // Check if track is a local file or already has a direct HTTP stream URL
        if (track.isDownloaded && track.contentUri.isNotBlank()) {
            playDirectUri(Uri.parse(track.contentUri), trackWithFavorite, startPositionMs)
        } else if (track.contentUri.startsWith("http://") || track.contentUri.startsWith("https://")) {
            playDirectUri(Uri.parse(track.contentUri), trackWithFavorite, startPositionMs)
        } else if (track.id.startsWith("content://") || track.id.startsWith("file://")) {
            playDirectUri(Uri.parse(track.id), trackWithFavorite, startPositionMs)
        } else {
            // Online YouTube Music track: resolve audio stream URL asynchronously
            streamResolutionJob = scope.launch(Dispatchers.IO) {
                Log.d(TAG, "Resolving online audio stream for: ${track.title} (${track.id})")
                val streamResult = onlineRepository.getAudioStreamUrl(track.id, settingsStorage.streamingQuality.value)

                withContext(Dispatchers.Main) {
                    streamResult.onSuccess { streamUrl ->
                        Log.d(TAG, "Stream resolved successfully for ${track.id}")
                        val resolvedTrack = trackWithFavorite.copy(contentUri = streamUrl)
                        playDirectUri(Uri.parse(streamUrl), resolvedTrack, startPositionMs)

                        // Trigger background queue expansion if the current queue is small
                        maybeExpandQueue(track.id)
                    }.onFailure { err ->
                        Log.e(TAG, "Failed to resolve stream for ${track.id}: ${err.message}", err)
                        _playerState.update { it.copy(status = PlayerStatus.ERROR) }
                    }
                }
            }
        }

        // Record into persistent history
        val source = if (track.isDownloaded) PlaybackSource.OFFLINE else PlaybackSource.ONLINE
        libraryStorage.addToHistory(trackWithFavorite, source)
    }

    /**
     * Prepares and starts ExoPlayer playback with a direct media URI and metadata.
     * Also synchronizes song title, artist, album, and artwork URI into MediaMetadata
     * so Android SystemUI notifications and lock screen media players show rich artwork.
     */
    private fun playDirectUri(mediaUri: Uri, track: Track, startPositionMs: Long = 0L) {
        val artworkUri = if (track.thumbnailUrl.isNotBlank()) {
            try {
                Uri.parse(track.thumbnailUrl)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album.ifBlank { "Lunify" })
            .apply {
                if (artworkUri != null) {
                    setArtworkUri(artworkUri)
                }
            }
            .build()

        val exoItem = ExoMediaItem.Builder()
            .setMediaId(track.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()

        exoPlayer.setMediaItem(exoItem)
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.play()

        // Ensure background playback service and media notification are active
        ensureServiceStarted()
    }

    /**
     * Ensures our [LunifyMediaService] service is running, hosting
     * the active MediaSession and system media notification shade.
     * Uses startService() first when the app is in the foreground, falling back
     * to startForegroundService() only when required by background restrictions.
     */
    fun ensureServiceStarted() {
        try {
            val intent = Intent(context, LunifyMediaService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(context, LunifyMediaService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e2: Exception) {
                Log.w(TAG, "Could not start LunifyMediaService: ${e2.message}")
            }
        }
    }

    /**
     * If the queue is near the end or only has 1-3 tracks, fetches related radio tracks
     * from YouTube Music and seamlessly appends them to the queue for uninterrupted music listening.
     */
    private fun maybeExpandQueue(videoId: String) {
        val state = _playerState.value
        val shouldExpand = state.queue.size <= 3 || state.currentIndex >= state.queue.size - 2
        if (shouldExpand) {
            scope.launch(Dispatchers.IO) {
                val relatedResult = onlineRepository.getRelatedQueue(videoId)
                val relatedTracks = relatedResult.getOrNull()
                if (!relatedTracks.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        _playerState.update { current ->
                            val existingIds = current.queue.map { it.id }.toSet()
                            val newTracks = relatedTracks.filter { it.id !in existingIds }
                            current.copy(queue = current.queue + newTracks)
                        }
                    }
                }
            }
        }
    }

    /**
     * Downloads an online track to the device for offline playback.
     * Writes audio bytes to the app's external music directory and updates [LibraryStorage].
     */
    /**
     * Downloads an online track to the device for offline playback.
     * Writes audio bytes with standard browser headers to avoid CDN 403 blocks,
     * updates [LibraryStorage], posts progress & status notifications, and registers
     * the song in Android's MediaStore.
     */
    fun downloadTrack(track: Track, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting download for track: ${track.title} (${track.id})")

                // Step A: Immediately show indeterminate notification to acknowledge user action
                downloadNotificationManager.showDownloadProgress(
                    track = track,
                    progressPercent = -1,
                    downloadedBytes = 0L,
                    totalBytes = -1L,
                )

                // 1. Resolve direct audio stream URL if not already present
                val streamUrl = if (track.contentUri.startsWith("http://") || track.contentUri.startsWith("https://")) {
                    track.contentUri
                } else {
                    val streamQuality = when (settingsStorage.downloadQuality.value) {
                        DownloadQuality.HIGH -> StreamingQuality.HIGH
                        DownloadQuality.STANDARD -> StreamingQuality.BALANCED
                    }
                    onlineRepository.getAudioStreamUrl(track.id, streamQuality).getOrThrow()
                }

                // 2. Prepare staging file in app's internal cache directory
                // We download chunks to cache first for unthrottled streaming and safe I/O
                val safeTitle = track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                val safeFileName = "${safeTitle}_${track.id}.m4a"
                val tempFile = File(context.cacheDir, "download_${track.id}.tmp")

                // 3. High-speed chunked download with YouTube CDN throttle avoidance
                var lastNotifiedTime = System.currentTimeMillis()
                var lastNotifiedPercent = -1

                chunkedDownloader.downloadToFile(
                    streamUrl = streamUrl,
                    destinationFile = tempFile,
                ) { downloadedBytes, totalBytes ->
                    val currentTime = System.currentTimeMillis()
                    if (totalBytes > 0L) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)

                        // Hey friend! Here is why we use shouldDispatchProgressUpdate:
                        // Only percentages in 0..99 are dispatched as ongoing progress notifications.
                        // When bytes reach 100%, we intentionally skip showDownloadProgress() to avoid
                        // race conditions where Android's NotificationManager gets flooded and freezes
                        // at 100% ongoing. Step 7 below transitions directly to showDownloadComplete!
                        if (DownloadNotificationManager.shouldDispatchProgressUpdate(
                                currentPercent = percent,
                                lastNotifiedPercent = lastNotifiedPercent,
                                currentTimeMs = currentTime,
                                lastNotifiedTimeMs = lastNotifiedTime,
                            )
                        ) {
                            lastNotifiedPercent = percent
                            lastNotifiedTime = currentTime
                            downloadNotificationManager.showDownloadProgress(
                                track = track,
                                progressPercent = percent,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                        }
                    } else {
                        // For indeterminate streams without Content-Length, update byte counters every 1000ms
                        if (currentTime - lastNotifiedTime >= 1000L) {
                            lastNotifiedTime = currentTime
                            downloadNotificationManager.showDownloadProgress(
                                track = track,
                                progressPercent = -1,
                                downloadedBytes = downloadedBytes,
                                totalBytes = -1L,
                            )
                        }
                    }
                }

                Log.d(TAG, "Audio chunks saved to staging: ${tempFile.absolutePath} (${tempFile.length()} bytes)")

                // 4. Publish audio file to device's public Music storage (/storage/emulated/0/Music/Lunify)
                // This makes the file visible to user file managers, system media players, and Lunify library.
                var finalUri: Uri? = null
                var finalFilePath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Lunify/$safeFileName",
                ).absolutePath

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Remove any prior duplicate entry with the same display name
                        try {
                            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                            context.contentResolver.delete(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                selection,
                                arrayOf(safeFileName),
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not delete old MediaStore entry: ${e.message}")
                        }

                        val contentValues = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, safeFileName)
                            put(MediaStore.Audio.Media.TITLE, track.title)
                            put(MediaStore.Audio.Media.ARTIST, track.artist)
                            put(MediaStore.Audio.Media.ALBUM, track.album.ifBlank { "Lunify" })
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                            put(MediaStore.Audio.Media.IS_MUSIC, 1)
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Lunify")
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }

                        val mediaStoreUri = context.contentResolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            contentValues,
                        )

                        if (mediaStoreUri != null) {
                            finalUri = mediaStoreUri
                            context.contentResolver.openOutputStream(mediaStoreUri)?.use { out ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                            }

                            // Publish file by releasing IS_PENDING
                            val publishValues = ContentValues().apply {
                                put(MediaStore.Audio.Media.IS_PENDING, 0)
                            }
                            context.contentResolver.update(mediaStoreUri, publishValues, null, null)

                            // Resolve the exact disk path assigned by MediaStore
                            try {
                                context.contentResolver.query(
                                    mediaStoreUri,
                                    arrayOf(MediaStore.Audio.Media.DATA),
                                    null,
                                    null,
                                    null,
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                                        if (dataIdx >= 0) {
                                            val resolved = cursor.getString(dataIdx)
                                            if (!resolved.isNullOrBlank()) {
                                                finalFilePath = resolved
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not read DATA column: ${e.message}")
                            }
                        }
                    } else {
                        // Pre-Android 10 fallback: copy directly to public Music directory
                        val publicMusicDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            "Lunify",
                        ).apply { mkdirs() }
                        val publicDestFile = File(publicMusicDir, safeFileName)
                        tempFile.copyTo(publicDestFile, overwrite = true)
                        finalFilePath = publicDestFile.absolutePath
                        finalUri = Uri.fromFile(publicDestFile)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(publicDestFile.absolutePath),
                            arrayOf("audio/mp4"),
                            null,
                        )
                    }
                } finally {
                    // Clean up temporary cache file so internal storage is not wasted
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }

                val finalFile = File(finalFilePath)
                Log.d(TAG, "Audio file published successfully to disk: $finalFilePath (${finalFile.length()} bytes)")

                // 5. Update track metadata as downloaded and persist in LibraryStorage
                val downloadedTrack = track.copy(
                    isDownloaded = true,
                    filePath = finalFilePath,
                    contentUri = finalUri?.toString() ?: Uri.fromFile(finalFile).toString(),
                    sizeBytes = if (finalFile.exists()) finalFile.length() else tempFile.length(),
                    folderName = "Lunify",
                )

                libraryStorage.saveDownloadedTrack(downloadedTrack)
                libraryStorage.recordPlay(downloadedTrack.id)

                // 6. Notify MediaScanner so all apps and system pick up the newly saved track immediately
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFilePath),
                        arrayOf("audio/mp4"),
                        null,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "MediaScanner registration notice: ${e.message}")
                }

                // 6b. Auto-fetch companion synchronized .lrc lyrics if enabled in Settings
                if (settingsStorage.autoDownloadLyrics.value) {
                    try {
                        val durationSec = (track.durationMs / 1000).toInt().coerceAtLeast(0)
                        val rawLrc = onlineLyricsRepo.fetchRawLrc(track.title, track.artist, durationSec)
                        if (!rawLrc.isNullOrBlank()) {
                            val lrcPath = finalFilePath.substringBeforeLast(".") + ".lrc"
                            val lrcFile = File(lrcPath)
                            lrcFile.writeText(rawLrc)
                            Log.i(TAG, "Companion .lrc lyrics written: $lrcPath")
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(lrcPath),
                                null,
                                null,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to auto-fetch companion lyrics: ${e.message}")
                    }
                }

                // 7. Transition notification cleanly to completed state
                downloadNotificationManager.showDownloadComplete(track)

                withContext(Dispatchers.Main) {
                    // If current track was the one downloaded, update state
                    if (_playerState.value.currentTrack?.id == track.id) {
                        _playerState.update { it.copy(currentTrack = downloadedTrack) }
                    }
                    onComplete?.invoke(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for track '${track.title}': ${e.message}", e)
                downloadNotificationManager.showDownloadFailed(track)
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    /**
     * Pauses playback.
     *
     * @param isRemoteSync Whether this command was received from a remote Duo peer (anti-echo flag).
     */
    fun pause(isRemoteSync: Boolean = false) {
        exoPlayer.pause()
        if (!isRemoteSync) {
            duoSyncDispatcher?.invoke(DuoAction.PAUSE, _playerState.value.currentTrack, exoPlayer.currentPosition)
        }
    }

    /**
     * Resumes playback.
     *
     * @param isRemoteSync Whether this command was received from a remote Duo peer (anti-echo flag).
     */
    fun resume(isRemoteSync: Boolean = false) {
        exoPlayer.play()
        if (!isRemoteSync) {
            duoSyncDispatcher?.invoke(DuoAction.PLAY, _playerState.value.currentTrack, exoPlayer.currentPosition)
        }
    }

    /**
     * Toggles play/pause state.
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE && _playerState.value.currentTrack != null) {
                playTrack(_playerState.value.currentTrack!!)
            } else {
                resume()
            }
        }
    }

    /**
     * Moves to the next track in the queue.
     * Includes a 350ms debounce threshold to prevent duplicate triggers from notification / headset intents.
     */
    fun playNext() {
        val now = System.currentTimeMillis()
        if (now - lastNextClickTime < 350L) {
            Log.d(TAG, "Ignoring duplicate playNext event within 350ms")
            return
        }
        lastNextClickTime = now

        val currentState = _playerState.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        val nextIndex = currentState.currentIndex + 1
        if (nextIndex < queue.size) {
            playTrack(queue[nextIndex])
        } else if (currentState.repeatMode == RepeatMode.ALL) {
            playTrack(queue[0])
        } else {
            // Queue ended
            exoPlayer.pause()
            exoPlayer.seekTo(0L)
            syncProgress()
        }
    }

    /**
     * Inserts a track immediately after the currently playing song in the queue,
     * so it will be the very next track played!
     *
     * @param track The song to play next.
     */
    fun insertNext(track: Track) {
        val currentQueue = _playerState.value.queue.toMutableList()
        val insertIndex = (_playerState.value.currentIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIndex, track)
        _playerState.update { it.copy(queue = currentQueue) }
    }

    /**
     * Appends a track to the end of the active queue.
     *
     * @param track The song to append.
     */
    fun addToQueue(track: Track) {
        val currentQueue = _playerState.value.queue.toMutableList()
        currentQueue.add(track)
        _playerState.update { it.copy(queue = currentQueue) }
    }

    /**
     * Handles the previous button action according to product requirements:
     *
     * RULE:
     * - If the currently playing track has played for MORE THAN 3 SECONDS (> 3000ms),
     *   restart the track from the beginning (0ms).
     * - If the track has played 3 SECONDS OR LESS (<= 3000ms), navigate to the
     *   previous track in the queue.
     * Includes a 350ms debounce threshold to prevent duplicate triggers from notification / headset intents.
     */
    fun playPrevious() {
        val now = System.currentTimeMillis()
        if (now - lastPrevClickTime < 350L) {
            Log.d(TAG, "Ignoring duplicate playPrevious event within 350ms")
            return
        }
        lastPrevClickTime = now

        val currentPositionMs = exoPlayer.currentPosition
        val currentState = _playerState.value

        if (currentPositionMs > 3000L) {
            // Track played > 3 seconds: Seek back to start
            exoPlayer.seekTo(0L)
            syncProgress()
        } else {
            // Track played <= 3 seconds: Jump to previous track
            val prevIndex = currentState.currentIndex - 1
            if (prevIndex >= 0 && prevIndex < currentState.queue.size) {
                playTrack(currentState.queue[prevIndex])
            } else {
                // Already at first track: just restart
                exoPlayer.seekTo(0L)
                syncProgress()
            }
        }
    }

    /**
     * Seeks playback position to a target timestamp in milliseconds.
     *
     * @param positionMs Target playback timestamp.
     * @param isRemoteSync Whether this seek was triggered by a remote Duo peer (anti-echo flag).
     */
    fun seekTo(positionMs: Long, isRemoteSync: Boolean = false) {
        val safePosition = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safePosition)
        syncProgress()
        if (!isRemoteSync) {
            duoSyncDispatcher?.invoke(DuoAction.SEEK, _playerState.value.currentTrack, safePosition)
        }
    }

    /**
     * Toggles repeat mode: OFF -> ALL -> ONE -> OFF.
     */
    fun toggleRepeat() {
        val currentMode = _playerState.value.repeatMode
        val nextMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        setRepeatMode(nextMode)
    }

    /**
     * Explicitly sets repeat mode (OFF, ALL, ONE) and updates ExoPlayer and reactive state.
     */
    fun setRepeatMode(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        }
        _playerState.update { it.copy(repeatMode = mode) }
    }

    /**
     * Toggles shuffle mode on/off.
     */
    fun toggleShuffle() {
        val nextShuffle = !_playerState.value.isShuffle
        setShuffleMode(nextShuffle)
    }

    /**
     * Explicitly sets shuffle mode and updates ExoPlayer and reactive state.
     */
    fun setShuffleMode(enabled: Boolean) {
        exoPlayer.shuffleModeEnabled = enabled
        _playerState.update { it.copy(isShuffle = enabled) }
    }

    /**
     * Toggles favorite status for a song in persistent storage and updates state.
     */
    fun toggleFavorite(track: Track) {
        val isNowFavorite = libraryStorage.toggleFavorite(track)

        // If current track is the one being toggled, update playerState
        if (_playerState.value.currentTrack?.id == track.id) {
            _playerState.update { state ->
                state.copy(
                    currentTrack = state.currentTrack?.copy(isFavorite = isNowFavorite),
                )
            }
        }
    }

    /**
     * Sets queue without immediately changing current playing track.
     */
    fun setQueue(newQueue: List<Track>, activeIndex: Int = 0) {
        _playerState.update {
            it.copy(
                queue = newQueue,
                currentIndex = activeIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0)),
            )
        }
    }

    /**
     * Launches the native Android system equalizer (Dolby Atmos, AudioFX, Wavelet, etc.)
     * passing our ExoPlayer's active audioSessionId so system-wide audio effects
     * are bound directly to Lunify's playback output.
     */
    fun launchSystemEqualizer(context: Context) {
        try {
            val sessionId = exoPlayer.audioSessionId
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                }
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No system equalizer installed on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch system equalizer: ${e.message}", e)
            Toast.makeText(context, "Could not open equalizer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cleanly releases ExoPlayer resources when the app or service is destroyed.
     */
    fun release() {
        stopProgressTicker()
        streamResolutionJob?.cancel()
        scope.cancel()
        exoPlayer.release()
    }
}
