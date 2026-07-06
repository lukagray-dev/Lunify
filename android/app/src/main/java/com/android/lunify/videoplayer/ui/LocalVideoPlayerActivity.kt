package com.android.lunify.videoplayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.lunify.R
import com.android.lunify.data.model.Video
import com.android.lunify.databinding.ActivityLocalVideoPlayerBinding
import com.android.lunify.player.service.MusicService

/**
 * Local video player activity for playing device videos using ExoPlayer.
 * Supports minimizing to a player bar while continuing playback.
 */
class LocalVideoPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LocalVideoPlayer"
        private const val CONTROLS_AUTO_HIDE_DELAY_MS = 5000L
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 7202
        const val EXTRA_VIDEO = "extra_video"
        const val EXTRA_VIDEO_LIST = "extra_video_list"
        const val EXTRA_START_POSITION = "extra_start_position"
        
        // Broadcast actions
        const val BROADCAST_VIDEO_STATE = "com.android.lunify.VIDEO_STATE"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        
        // Singleton player state for background playback
        private var sharedPlayer: ExoPlayer? = null
        private var currentVideo: Video? = null
        private var videoList: List<Video> = emptyList()
        private var currentIndex: Int = 0
        private var appContext: Context? = null
        private var lastPlaybackVideoId: Long? = null
        private var lastPlaybackPositionMs: Long = 0L
        
        fun start(context: Context, video: Video, videos: List<Video> = listOf(video)) {
            appContext = context.applicationContext

            val resumePositionMs = when {
                sharedPlayer != null && currentVideo?.id == video.id -> {
                    sharedPlayer?.currentPosition ?: 0L
                }
                lastPlaybackVideoId == video.id -> lastPlaybackPositionMs
                else -> 0L
            }

            ensureNotificationPermission(context)
            stopAudioPlayback(context)

            val intent = Intent(context, LocalVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO, video)
                putParcelableArrayListExtra(EXTRA_VIDEO_LIST, ArrayList(videos))
                putExtra(EXTRA_START_POSITION, resumePositionMs)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
        
        fun getCurrentVideo(): Video? = currentVideo
        fun getCurrentVideoList(): List<Video> = videoList.toList()
        fun isPlaying(): Boolean = sharedPlayer?.isPlaying == true

        fun togglePlayPause() {
            sharedPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
        
        fun stopPlayback() {
            sharedPlayer?.let { player ->
                lastPlaybackVideoId = currentVideo?.id
                lastPlaybackPositionMs = player.currentPosition.coerceAtLeast(0L)

                runCatching { player.stop() }
                runCatching { player.release() }
            }
            sharedPlayer = null
            currentVideo = null
            videoList = emptyList()
            currentIndex = 0
            appContext?.let { context ->
                LocalVideoPlaybackNotificationManager.cancel(context)
            }

            appContext?.let { context ->
                val intent = Intent(BROADCAST_VIDEO_STATE).apply {
                    putExtra(EXTRA_IS_PLAYING, false)
                    putExtra(EXTRA_POSITION, 0L)
                    putExtra(EXTRA_DURATION, 0L)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            }
        }

        private fun stopAudioPlayback(context: Context) {
            context.startService(Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            })
        }

        private fun ensureNotificationPermission(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            if (context is AppCompatActivity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    POST_NOTIFICATIONS_REQUEST_CODE
                )
            }
        }

        private fun resolveStartPosition(video: Video): Long {
            return LocalVideoPlaybackResumeResolver.resolve(
                requestedVideoId = video.id,
                currentSessionVideoId = currentVideo?.id,
                currentSessionPositionMs = sharedPlayer?.currentPosition,
                lastStoppedVideoId = lastPlaybackVideoId,
                lastStoppedPositionMs = lastPlaybackPositionMs
            )
        }

        private fun broadcastVideoState(
            context: Context,
            isPlaying: Boolean,
            title: String?,
            position: Long,
            duration: Long
        ) {
            val intent = Intent(BROADCAST_VIDEO_STATE).apply {
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                if (title != null) {
                    putExtra(EXTRA_VIDEO_TITLE, title)
                }
                putExtra(EXTRA_VIDEO_PATH, "")
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_DURATION, duration)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
    
    private lateinit var binding: ActivityLocalVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer ?: return
            if (player.isPlaying) {
                broadcastState()
                controlsHandler.postDelayed(this, 1000L)
            }
        }
    }
    
    private var video: Video? = null
    private var startPositionMs: Long = 0L
    private var pendingSeekPositionMs: Long = 0L
    private var controlsVisible = true
    
    // Pinch-to-zoom support
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomMode = 0 // 0=fit, 1=fill, 2=zoom

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    binding.progressLoading.visibility = View.VISIBLE
                }
                Player.STATE_READY -> {
                    binding.progressLoading.visibility = View.GONE
                    if (pendingSeekPositionMs > 0L) {
                        exoPlayer?.seekTo(pendingSeekPositionMs)
                        pendingSeekPositionMs = 0L
                    }
                    exoPlayer?.play()
                    scheduleProgressUpdates()
                    broadcastState()
                }
                Player.STATE_ENDED -> {
                    if (currentIndex < videoList.size - 1) {
                        playNextVideo()
                    } else {
                        stopProgressUpdates()
                        broadcastState(showNotification = false)
                        cancelPlaybackNotification()
                    }
                }
                Player.STATE_IDLE -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                scheduleProgressUpdates()
            } else {
                stopProgressUpdates()
            }
            broadcastState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}")
            stopProgressUpdates()
            cancelPlaybackNotification()
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Playback error: ${error.message}"
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityLocalVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get video from intent
        video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO)
        }
        
        val videos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST)
        }
        
        if (video == null) {
            Toast.makeText(this, "No video provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val selectedVideo = video ?: return
        videos?.let { list ->
            videoList = list
            currentIndex = list.indexOfFirst { v -> v.id == selectedVideo.id }.coerceAtLeast(0)
        }
        currentVideo = selectedVideo
        startPositionMs = intent.getLongExtra(
            EXTRA_START_POSITION,
            resolveStartPosition(selectedVideo)
        )
        
        setupUI()
        enableImmersiveMode()
        initializePlayer()
        showControls()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Get new video
        val newVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO)
        }
        val newVideos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST)
        }
        
        if (newVideo != null && newVideo.id != video?.id) {
            video = newVideo
            currentVideo = newVideo
            newVideos?.let { list ->
                videoList = list
                currentIndex = list.indexOfFirst { v -> v.id == newVideo.id }.coerceAtLeast(0)
            }
            startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, resolveStartPosition(newVideo))
            loadMedia()
        }
    }
    
    private fun setupUI() {
        binding.tvTitle.text = video?.title ?: "Video"
        binding.playerView.controllerShowTimeoutMs = CONTROLS_AUTO_HIDE_DELAY_MS.toInt()
        binding.playerView.setControllerVisibilityListener(
            object : androidx.media3.ui.PlayerView.ControllerVisibilityListener {
                override fun onVisibilityChanged(visibility: Int) {
                    if (visibility == View.VISIBLE) {
                        showControls()
                    } else {
                        hideControls()
                    }
                }
            }
        )
        
        binding.btnClose.setOnClickListener {
            minimizeToBar()
        }
        
        binding.btnRetry.setOnClickListener {
            binding.errorOverlay.visibility = View.GONE
            initializePlayer()
        }
        
        binding.btnFullscreen.setOnClickListener {
            toggleOrientation()
        }
        
        setupPinchToZoom()
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                
                if (scaleFactor > 1.1f) {
                    cycleZoomMode(true)
                    return true
                } else if (scaleFactor < 0.9f) {
                    cycleZoomMode(false)
                    return true
                }
                return false
            }
        })
        
        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun cycleZoomMode(zoomIn: Boolean) {
        currentZoomMode = if (zoomIn) {
            (currentZoomMode + 1).coerceAtMost(2)
        } else {
            (currentZoomMode - 1).coerceAtLeast(0)
        }
        
        val resizeMode = when (currentZoomMode) {
            0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        binding.playerView.resizeMode = resizeMode
        
        val modeName = when (currentZoomMode) {
            0 -> "Fit"
            1 -> "Fill"
            2 -> "Zoom"
            else -> "Fit"
        }
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        Log.d(TAG, "Initializing player")
        
        binding.progressLoading.visibility = View.VISIBLE
        binding.errorOverlay.visibility = View.GONE
        
        try {
            // Reuse shared player if exists, otherwise create new
            if (sharedPlayer == null) {
                sharedPlayer = ExoPlayer.Builder(this).build()
            }
            exoPlayer = sharedPlayer
            
            binding.playerView.player = exoPlayer
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.addListener(playerListener)
            
            loadMedia()
            showControls()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to initialize player: ${e.message}"
        }
    }
    
    private fun loadMedia() {
        val player = exoPlayer ?: return
        val videoPath = video?.path ?: return
        
        try {
            Log.d(TAG, "Loading video from: $videoPath")
            binding.tvTitle.text = video?.title ?: "Video"
            
            val mediaItem = MediaItem.fromUri(videoPath)
            player.setMediaItem(mediaItem)
            player.prepare()
            pendingSeekPositionMs = startPositionMs.coerceAtLeast(0L)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load media: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to load video: ${e.message}"
        }
    }

    private fun broadcastState(showNotification: Boolean = true) {
        val player = exoPlayer ?: return
        val context = appContext ?: return
        val intent = Intent(LocalVideoPlayerActivity.BROADCAST_VIDEO_STATE).apply {
            putExtra(EXTRA_IS_PLAYING, player.isPlaying)
            putExtra(EXTRA_VIDEO_TITLE, video?.title)
            putExtra(EXTRA_POSITION, player.currentPosition)
            putExtra(EXTRA_DURATION, player.duration)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        if (showNotification) {
            updatePlaybackNotification()
        }
    }

    private fun updatePlaybackNotification() {
        val context = appContext ?: return
        val currentVideoSnapshot = video ?: return
        val player = exoPlayer ?: return

        LocalVideoPlaybackNotificationManager.show(
            context = context,
            video = currentVideoSnapshot,
            playlist = videoList.ifEmpty { listOf(currentVideoSnapshot) },
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            isPlaying = player.isPlaying
        )
    }

    private fun cancelPlaybackNotification() {
        val context = appContext ?: return
        LocalVideoPlaybackNotificationManager.cancel(context)
    }

    private fun scheduleProgressUpdates() {
        controlsHandler.removeCallbacks(progressRunnable)
        controlsHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        controlsHandler.removeCallbacks(progressRunnable)
    }

    private fun showControls() {
        controlsVisible = true
        binding.topBar.animate().cancel()
        binding.topBar.visibility = View.VISIBLE
        binding.topBar.alpha = 1f
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY_MS)
    }

    private fun hideControls() {
        if (!controlsVisible || isFinishing || isDestroyed) return
        controlsVisible = false
        binding.topBar.animate().cancel()
        binding.topBar.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                if (!controlsVisible) {
                    binding.topBar.visibility = View.GONE
                }
            }
            .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            showControls()
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun playNextVideo() {
        if (currentIndex < videoList.size - 1) {
            currentIndex++
            video = videoList[currentIndex]
            currentVideo = video
            startPositionMs = 0L
            loadMedia()
        }
    }
    
    private fun minimizeToBar() {
        // Save current position and finish activity
        // Player continues in background via sharedPlayer
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.slide_down)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_down)
        }
    }
    
    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    override fun onResume() {
        super.onResume()
        showControls()
        exoPlayer?.play()
    }
    
    override fun onPause() {
        super.onPause()
        // Don't pause - let it continue for background playback
    }
    
    override fun onStop() {
        super.onStop()
        // Player continues in background
        exoPlayer?.let { player ->
            if (player.currentPosition > 0) {
                lastPlaybackVideoId = video?.id
                lastPlaybackPositionMs = player.currentPosition
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't release player here - it's shared for background playback
        // Only detach from view
        stopProgressUpdates()
        controlsHandler.removeCallbacks(hideControlsRunnable)
        exoPlayer?.removeListener(playerListener)
        binding.playerView.player = null
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        minimizeToBar()
    }
}
