package com.android.lunify.player.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.lunify.data.model.Song
import com.android.lunify.player.service.MusicService

/**
 * ViewModel responsible for media playback controls, queue management, progress tracking and stream resolving state.
 */
class PlayerViewModel : ViewModel() {

    private var currentPlaylist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _playSongEvent = MutableLiveData<Song?>()
    val playSongEvent: LiveData<Song?> = _playSongEvent

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData(0)
    val duration: LiveData<Int> = _duration

    private val _showPlayerBar = MutableLiveData(false)
    val showPlayerBar: LiveData<Boolean> = _showPlayerBar

    private val _isResolvingStream = MutableLiveData(false)
    val isResolvingStream: LiveData<Boolean> = _isResolvingStream

    fun setPlaylist(songs: List<Song>) {
        currentPlaylist = songs
    }

    fun getCurrentPlaylist(): List<Song> = currentPlaylist

    fun playSong(song: Song) {
        _currentSong.value = song
        _playSongEvent.value = song
        _isPlaying.value = true
        _showPlayerBar.value = true
        _isResolvingStream.value = false
        currentIndex = currentPlaylist.indexOfFirst { it.id == song.id }
        if (currentIndex == -1) {
            currentPlaylist = listOf(song)
            currentIndex = 0
        }
    }

    fun clearPlaySongEvent() {
        _playSongEvent.value = null
    }

    fun prepareOnlinePlayback(song: Song) {
        _currentSong.value = song
        _showPlayerBar.value = true
        _isResolvingStream.value = true
    }

    fun setResolvingStream(resolving: Boolean) {
        _isResolvingStream.value = resolving
    }

    fun updatePlaybackState(isPlaying: Boolean, song: Song?) {
        _isPlaying.value = isPlaying
        song?.let { 
            _currentSong.value = it
            _showPlayerBar.value = true
            _isResolvingStream.value = false
            val index = currentPlaylist.indexOfFirst { s -> s.id == it.id }
            if (index != -1) {
                currentIndex = index
            }
        }
    }

    fun updateProgress(position: Int, duration: Int) {
        _progress.value = position
        _duration.value = duration
    }

    fun playNext() {
        if (currentPlaylist.isNotEmpty() && currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            val nextSong = currentPlaylist[currentIndex]
            _currentSong.value = nextSong
            _showPlayerBar.value = true
        }
    }

    fun playPrevious() {
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            val prevSong = currentPlaylist[currentIndex]
            _currentSong.value = prevSong
            _showPlayerBar.value = true
        }
    }

    fun stopPlayback() {
        _currentSong.value = null
        _isPlaying.value = false
        _showPlayerBar.value = false
        _progress.value = 0
        _duration.value = 0
    }

    fun shufflePlay(allSongs: List<Song>) {
        val shuffled = allSongs.shuffled()
        if (shuffled.isNotEmpty()) {
            currentPlaylist = shuffled
            currentIndex = 0
            playSong(shuffled.first())
        }
    }

    fun addToQueue(context: Context, song: Song) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_ADD_TO_QUEUE
            putExtra(MusicService.EXTRA_SONG, song)
        }
        context.startService(intent)
    }
}
