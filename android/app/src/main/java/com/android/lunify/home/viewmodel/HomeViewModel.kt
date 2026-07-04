package com.android.lunify.home.viewmodel

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.lunify.data.model.Album
import com.android.lunify.data.model.Artist
import com.android.lunify.data.model.Folder
import com.android.lunify.data.model.Song
import com.android.lunify.data.model.SortOption
import com.android.lunify.data.model.Video
import com.android.lunify.data.repository.MusicRepository
import com.android.lunify.player.PlayCountManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel responsible for local media files scanning, sorting, searching and deletion.
 */
class HomeViewModel : ViewModel() {

    private var repository: MusicRepository? = null
    private var playCountManager: PlayCountManager? = null
    private var allSongs: List<Song> = emptyList()
    private var allVideos: List<Video> = emptyList()
    private var currentPlaylist: List<Song> = emptyList()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists

    private val _albums = MutableLiveData<List<Album>>()
    val albums: LiveData<List<Album>> = _albums

    private val _folders = MutableLiveData<List<Folder>>()
    val folders: LiveData<List<Folder>> = _folders

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _navigateToSongsList = MutableLiveData<Pair<String, List<Song>>?>()
    val navigateToSongsList: LiveData<Pair<String, List<Song>>?> = _navigateToSongsList

    private var currentSortOption = SortOption.ADDING_TIME
    private var searchQuery = ""
    
    private val _deleteResult = MutableLiveData<DeleteResult?>()
    val deleteResult: LiveData<DeleteResult?> = _deleteResult
    
    data class DeleteResult(val success: Boolean, val songTitle: String)

    fun initialize(repository: MusicRepository) {
        this.repository = repository
        loadAllMedia()
    }
    
    fun initializePlayCountManager(context: Context) {
        playCountManager = PlayCountManager.getInstance(context)
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository?.let { repo ->
                    allSongs = repo.getAllSongs()
                    allVideos = repo.getAllVideos()
                    
                    applySortAndFilter()
                    _videos.value = allVideos
                    updateArtists()
                    updateAlbums()
                    updateFolders()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortOption(option: SortOption) {
        currentSortOption = option
        applySortAndFilter()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applySortAndFilter()
    }

    private fun applySortAndFilter() {
        repository?.let { repo ->
            var result = allSongs
            
            playCountManager?.let { manager ->
                val playCounts = manager.getAllPlayCounts()
                result = result.map { song ->
                    song.copy(playCount = playCounts[song.id] ?: 0)
                }
            }
            
            if (searchQuery.isNotBlank()) {
                result = repo.searchSongs(result, searchQuery)
            }
            
            result = repo.sortSongs(result, currentSortOption)
            _songs.value = result
            currentPlaylist = result
        }
    }

    private fun updateArtists() {
        viewModelScope.launch {
            repository?.let { repo ->
                _artists.value = repo.getAllArtists(allSongs)
            }
        }
    }

    private fun updateAlbums() {
        viewModelScope.launch {
            repository?.let { repo ->
                _albums.value = repo.getAllAlbums(allSongs)
            }
        }
    }

    private fun updateFolders() {
        viewModelScope.launch {
            repository?.let { repo ->
                _folders.value = repo.getAllFolders(allSongs, allVideos)
            }
        }
    }

    fun selectArtist(artist: Artist) {
        repository?.let { repo ->
            val artistSongs = repo.getSongsForArtist(allSongs, artist.name)
            _navigateToSongsList.value = Pair(artist.name, artistSongs)
        }
    }

    fun selectAlbum(album: Album) {
        repository?.let { repo ->
            val albumSongs = repo.getSongsForAlbum(allSongs, album.title, album.artist)
            _navigateToSongsList.value = Pair(album.title, albumSongs)
        }
    }

    fun clearNavigation() {
        _navigateToSongsList.value = null
    }

    fun getSongsForArtist(artist: Artist): List<Song> {
        return repository?.getSongsForArtist(allSongs, artist.name) ?: emptyList()
    }
    
    fun getSongsForAlbum(album: Album): List<Song> {
        return repository?.getSongsForAlbum(allSongs, album.title, album.artist) ?: emptyList()
    }
    
    fun getSongsForFolder(folder: Folder): List<Song> {
        return repository?.getSongsForFolder(allSongs, folder.path) ?: emptyList()
    }
    
    fun getVideosForFolder(folder: Folder): List<Video> {
        return allVideos.filter { video ->
            File(video.path).parent == folder.path
        }
    }

    fun shareSong(context: Context, song: Song) {
        try {
            val file = File(song.path)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share song"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun shareSongs(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        
        if (songs.size == 1) {
            shareSong(context, songs.first())
            return
        }
        
        try {
            val uris = ArrayList<android.net.Uri>()
            for (song in songs) {
                val file = File(song.path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
            
            if (uris.isEmpty()) {
                Toast.makeText(context, "No files found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} songs"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSong(context: Context, song: Song) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id
                )
                
                val deletedRows = contentResolver.delete(uri, null, null)
                
                if (deletedRows > 0) {
                    allSongs = allSongs.filter { it.id != song.id }
                    applySortAndFilter()
                    updateArtists()
                    updateAlbums()
                    updateFolders()
                    _deleteResult.value = DeleteResult(true, song.title)
                } else {
                    val file = File(song.path)
                    if (file.exists() && file.delete()) {
                        allSongs = allSongs.filter { it.id != song.id }
                        applySortAndFilter()
                        updateArtists()
                        updateAlbums()
                        updateFolders()
                        _deleteResult.value = DeleteResult(true, song.title)
                    } else {
                        _deleteResult.value = DeleteResult(false, song.title)
                    }
                }
            } catch (_: Exception) {
                _deleteResult.value = DeleteResult(false, song.title)
            }
        }
    }
    
    fun clearDeleteResult() {
        _deleteResult.value = null
    }
}
