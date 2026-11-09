package com.lunify.player.service

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lunify.R
import com.lunify.domain.model.PlayerState
import com.lunify.domain.model.RepeatMode
import com.lunify.player.AudioPlayerManager

/**
 * ==============================================================================
 * Lunify - Media Session Callback (LunifyMediaSessionCallback.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the bridge between Android's media ecosystem and Lunify!
 *
 * WHAT DOES THIS CALLBACK DO?
 * ---------------------------
 * When a user interacts with playback outside of our app's visual Compose screens:
 * 1. Pulls down the Android notification shade and taps Next/Previous/Play/Pause/Shuffle/Repeat.
 * 2. Drags the waveform scrubber bar on Android 13+ (API 33+) lock screen or Quick Settings.
 * 3. Taps the physical button on a Bluetooth headset or car stereo steering wheel.
 * 4. Issues a Google Assistant voice command ("Hey Google, next song").
 *
 * Android dispatches these requests to our active [MediaSession].
 * This callback intercepts those requests and cleanly routes them to the
 * single source of truth: our [AudioPlayerManager]!
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
class LunifyMediaSessionCallback(
    private val audioPlayerManager: AudioPlayerManager,
) : MediaSession.Callback {

    companion object {
        private const val TAG = "LunifySessionCallback"
    }

    /**
     * Called when a controller (Android SystemUI, Bluetooth service, Android Auto)
     * connects to our [MediaSession].
     *
     * Here we declare which capabilities our session supports:
     * - Standard commands: Play, Pause, Next, Previous, Seek within item, Repeat, Shuffle.
     * - Custom session commands: [LunifyMediaService.ACTION_SHUFFLE] and [LunifyMediaService.ACTION_REPEAT].
     * - Custom layout: Pre-populates the initial Shuffle and Repeat command buttons.
     */
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        Log.d(TAG, "Media controller connected: ${controller.packageName}")

        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(SessionCommand(LunifyMediaService.ACTION_SHUFFLE, Bundle()))
            .add(SessionCommand(LunifyMediaService.ACTION_REPEAT, Bundle()))
            .build()

        val playerCommands = Player.Commands.Builder()
            .addAllCommands()
            .build()

        val initialButtons = buildCustomLayout(audioPlayerManager.playerState.value)

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .setCustomLayout(initialButtons)
            .build()
    }

    /**
     * Intercepts custom commands dispatched by Android SystemUI notification action buttons.
     */
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        Log.d(TAG, "Received custom command: ${customCommand.customAction}")
        when (customCommand.customAction) {
            LunifyMediaService.ACTION_SHUFFLE -> {
                audioPlayerManager.toggleShuffle()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            LunifyMediaService.ACTION_REPEAT -> {
                audioPlayerManager.toggleRepeat()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    /**
     * Intercepts standard Player commands (Next, Previous, Play/Pause, Shuffle, Repeat).
     */
    @Deprecated("Deprecated in Java in Media3 Callback")
    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        Log.d(TAG, "Received player command request: $playerCommand")
        when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                audioPlayerManager.playNext()
                return SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                audioPlayerManager.playPrevious()
                return SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_PLAY_PAUSE -> {
                audioPlayerManager.togglePlayPause()
                return SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_SET_SHUFFLE_MODE -> {
                audioPlayerManager.toggleShuffle()
                return SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_SET_REPEAT_MODE -> {
                audioPlayerManager.toggleRepeat()
                return SessionResult.RESULT_SUCCESS
            }
        }
        return super.onPlayerCommandRequest(session, controller, playerCommand)
    }

    /**
     * Builds the custom command button list for Shuffle and Repeat to be displayed
     * in the system media notification shade.
     */
    private fun buildCustomLayout(state: PlayerState): List<CommandButton> {
        val shuffleIcon = if (state.isShuffle) {
            CommandButton.ICON_SHUFFLE_ON
        } else {
            CommandButton.ICON_SHUFFLE_OFF
        }

        val shuffleButton = CommandButton.Builder(shuffleIcon)
            .setDisplayName(if (state.isShuffle) "Shuffle On" else "Shuffle Off")
            .setCustomIconResId(R.drawable.ic_lucide_shuffle)
            .setSessionCommand(SessionCommand(LunifyMediaService.ACTION_SHUFFLE, Bundle()))
            .setEnabled(true)
            .build()

        val (repeatIcon, repeatName) = when (state.repeatMode) {
            RepeatMode.ONE -> Pair(CommandButton.ICON_REPEAT_ONE, "Repeat One")
            RepeatMode.ALL -> Pair(CommandButton.ICON_REPEAT_ALL, "Repeat All")
            RepeatMode.OFF -> Pair(CommandButton.ICON_REPEAT_OFF, "Repeat Off")
        }

        val repeatButton = CommandButton.Builder(repeatIcon)
            .setDisplayName(repeatName)
            .setCustomIconResId(R.drawable.ic_lucide_repeat)
            .setSessionCommand(SessionCommand(LunifyMediaService.ACTION_REPEAT, Bundle()))
            .setEnabled(true)
            .build()

        return listOf(shuffleButton, repeatButton)
    }
}
