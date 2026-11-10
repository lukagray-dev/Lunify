package com.lunify.player.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.lunify.domain.model.RepeatMode
import com.lunify.player.AudioPlayerManager

/**
 * ==============================================================================
 * Lunify - Forwarding Player (LunifyForwardingPlayer.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the playlist navigation bridge for Android Media3.
 *
 * WHY DO WE NEED A FORWARDING PLAYER HERE?
 * ---------------------------------------
 * In Lunify, audio playback queue navigation is managed dynamically in
 * [AudioPlayerManager]. ExoPlayer itself only holds the active playing [MediaItem]
 * to keep memory and stream resolution lightweight.
 *
 * However, Android's SystemUI media notification queries `player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)`.
 * By default, if ExoPlayer only holds 1 item in its timeline and repeat mode is
 * not ALL, ExoPlayer reports that "Seek to Next" is unavailable, which causes
 * the Android SystemUI to dynamically HIDE the "Next" button in the notification shade!
 *
 * This [ForwardingPlayer] solves this by:
 * 1. Ensuring [Player.COMMAND_SEEK_TO_NEXT] and [Player.COMMAND_SEEK_TO_PREVIOUS]
 *    are always reported as available, guaranteeing that all 5 controls:
 *    [Shuffle] [Previous] [Play/Pause] [Next] [Repeat]
 *    are always rendered in the system notification shade.
 * 2. Routing [seekToNext] and [seekToPrevious] directly into [AudioPlayerManager.playNext]
 *    and [AudioPlayerManager.playPrevious].
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
class LunifyForwardingPlayer(
    player: Player,
    private val audioPlayerManager: AudioPlayerManager,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        val baseCommands = super.getAvailableCommands()
        return baseCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        audioPlayerManager.playNext()
    }

    override fun seekToNextMediaItem() {
        audioPlayerManager.playNext()
    }

    override fun seekToPrevious() {
        audioPlayerManager.playPrevious()
    }

    override fun seekToPreviousMediaItem() {
        audioPlayerManager.playPrevious()
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        super.setShuffleModeEnabled(shuffleModeEnabled)
        audioPlayerManager.setShuffleMode(shuffleModeEnabled)
    }

    override fun setRepeatMode(repeatMode: Int) {
        super.setRepeatMode(repeatMode)
        val targetMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        audioPlayerManager.setRepeatMode(targetMode)
    }
}
