package com.android.lunify.videoplayer.ui

import android.content.Context
import android.content.Intent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.lunify.R
import com.android.lunify.databinding.ActivityVideoPlayerBinding
import com.android.lunify.player.service.MusicService
import com.android.lunify.videoplayer.preview.PreviewManager

/**
 * Clean video player activity for streaming video preview.
 * 
 * Features:
 * - Single ExoPlayer instance per activity
 * - Resume from saved position
 * - Saves position on back press
 * - No duplicate players or audio overlap
 */
class VideoPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val CONTROLS_AUTO_HIDE_DELAY_MS = 5000L
        private const val EXTRA_VIDEO_URL = "extra_video_url"
        private const val EXTRA_AUDIO_URL = "extra_audio_url"
        private const val EXTRA_VIDEO_TITLE = "extra_video_title"
        private const val EXTRA_START_POSITION = "extra_start_position"
        
        /**
         * Start video player with video and optional audio URLs.
         */
        fun start(
            context: Context,
            videoUrl: String,
            audioUrl: String? = null,
            title: String = "",
            startPositionMs: Long = 0L
        ) {
            context.startService(Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            })
            LocalVideoPlayerActivity.stopPlayback()

            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_AUDIO_URL, audioUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_START_POSITION, startPositionMs)
                // Ensure single instance - clear any existing
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
    
    private lateinit var binding: ActivityVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    
    private var videoUrl: String = ""
    private var audioUrl: String? = null
    private var videoTitle: String = ""
    private var startPositionMs: Long = 0L
    private var controlsVisible = true
    private var pendingSeekPositionMs: Long = 0L
    
    // Pinch-to-zoom support
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomMode = 0 // 0=fit, 1=fill, 2=zoom
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get extras
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, PreviewManager.getSavedPosition())
        
        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupUI()
        enableImmersiveMode()
        initializePlayer()
        showControls()
    }
    
    private fun setupUI() {
        binding.tvTitle.text = videoTitle
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
            savePositionAndFinish()
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
                
                // Pinch out (zoom in) - scale > 1
                if (scaleFactor > 1.1f) {
                    cycleZoomMode(true)
                    return true
                }
                // Pinch in (zoom out) - scale < 1
                else if (scaleFactor < 0.9f) {
                    cycleZoomMode(false)
                    return true
                }
                return false
            }
        })
        
        // Set touch listener on playerView to detect pinch gestures
        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            // Return false to allow PlayerView to handle other touch events (play/pause, seek, etc.)
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
        Log.d(TAG, "Zoom mode changed to: $modeName")
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        Log.d(TAG, "Initializing player")
        
        // Release any existing player first
        releasePlayer()
        
        // Show loading
        binding.progressLoading.visibility = View.VISIBLE
        binding.errorOverlay.visibility = View.GONE
        
        try {
            // Create ExoPlayer
            val mediaSourceFactory = DefaultMediaSourceFactory(this)
            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            
            // Attach to PlayerView
            binding.playerView.player = exoPlayer
            
            // Setup listener
            exoPlayer?.addListener(object : Player.Listener {
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
                            // Auto-play when ready
                            exoPlayer?.play()
                        }
                        Player.STATE_ENDED -> {
                            // Video finished - reset position
                            PreviewManager.savePosition(0L)
                        }
                        Player.STATE_IDLE -> {
                            // Idle state
                        }
                    }
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")
                    binding.progressLoading.visibility = View.GONE
                    binding.errorOverlay.visibility = View.VISIBLE
                    binding.tvError.text = "Playback error: ${error.message}"
                }
            })
            
            // Load media
            loadMedia()
            showControls()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to initialize player: ${e.message}"
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun loadMedia() {
        val player = exoPlayer ?: return
        
        try {
            if (audioUrl != null) {
                // Merge video and audio streams for highest quality
                Log.d(TAG, "Loading video with separate audio stream")
                
                val mediaSourceFactory = DefaultMediaSourceFactory(this)
                val videoSource = mediaSourceFactory.createMediaSource(
                    MediaItem.fromUri(videoUrl)
                )
                val audioSource = mediaSourceFactory.createMediaSource(
                    MediaItem.fromUri(audioUrl!!)
                )
                
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                player.setMediaSource(mergedSource)
            } else {
                // Single URL with both video and audio
                Log.d(TAG, "Loading video from single URL")
                player.setMediaItem(MediaItem.fromUri(videoUrl))
            }
            
            player.prepare()
            pendingSeekPositionMs = startPositionMs.coerceAtLeast(0L)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load media: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to load video: ${e.message}"
        }
    }
    
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    
    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    

    private fun savePositionAndFinish() {
        // Save current position for resume
        exoPlayer?.let { player ->
            val position = player.currentPosition
            if (position > 0) {
                PreviewManager.savePosition(position)
                Log.d(TAG, "Saved position: ${position}ms")
            }
        }
        finish()
    }
    
    private fun releasePlayer() {
        Log.d(TAG, "Releasing player")
        binding.playerView.player = null
        exoPlayer?.release()
        exoPlayer = null
    }
    
    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        showControls()
    }
    
    override fun onStop() {
        super.onStop()
        // Save position when activity stops
        exoPlayer?.let { player ->
            val position = player.currentPosition
            if (position > 0) {
                PreviewManager.savePosition(position)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        controlsHandler.removeCallbacks(hideControlsRunnable)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        savePositionAndFinish()
    }
}
