package com.android.lunify.browse.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.lunify.download.data.model.ExtractedContent
import com.android.lunify.download.engine.ytdlp.YtDlpAndroidEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Supported categories for online media discovery
 */
enum class BrowseCategory {
    MUSIC, VIDEOS
}

/**
 * Represents a section of tracks/videos in the home feed
 */
data class FeedSection(
    val id: String,
    val title: String,
    val query: String,
    val hero: Boolean,
    val tracks: List<ExtractedContent>
)

/**
 * ViewModel for the Browse feature.
 * Coordinates category selection, lazy-loads feed sections with random queries,
 * caches feeds in-memory for instant switching, and handles yt-dlp search.
 */
class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BrowseViewModel"
    }

    private val context = application.applicationContext
    private var ytDlpEngine: YtDlpAndroidEngine? = null

    // Active category state
    private val _currentCategory = MutableLiveData(BrowseCategory.MUSIC)
    val currentCategory: LiveData<BrowseCategory> = _currentCategory

    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Search query and results
    private val _searchResults = MutableLiveData<List<ExtractedContent>>(emptyList())
    val searchResults: LiveData<List<ExtractedContent>> = _searchResults

    // Playback state of loading URL
    private val _isResolvingStream = MutableLiveData<Boolean>(false)
    val isResolvingStream: LiveData<Boolean> = _isResolvingStream

    // Home feeds cache
    private val _musicHomeFeed = MutableLiveData<List<FeedSection>>(emptyList())
    val musicHomeFeed: LiveData<List<FeedSection>> = _musicHomeFeed

    private val _videoHomeFeed = MutableLiveData<List<FeedSection>>(emptyList())
    val videoHomeFeed: LiveData<List<FeedSection>> = _videoHomeFeed

    private val _videoMoviesFeed = MutableLiveData<List<FeedSection>>(emptyList())
    val videoMoviesFeed: LiveData<List<FeedSection>> = _videoMoviesFeed

    private val _videoTvShowsFeed = MutableLiveData<List<FeedSection>>(emptyList())
    val videoTvShowsFeed: LiveData<List<FeedSection>> = _videoTvShowsFeed

    init {
        // Initialize the yt-dlp engine on a background thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pre-initializing YtDlpAndroidEngine...")
                val engine = YtDlpAndroidEngine(context)
                val initResult = engine.initialize()
                if (initResult.isSuccess) {
                    ytDlpEngine = engine
                    Log.d(TAG, "YtDlpAndroidEngine initialized successfully")
                    // Load the default feed eagerly
                    loadCategoryFeed(BrowseCategory.MUSIC, "home", forceRefresh = false)
                } else {
                    Log.e(TAG, "Failed to initialize YtDlpAndroidEngine: ${initResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing yt-dlp engine", e)
            }
        }
    }

    /**
     * Switch category (Music or Videos)
     */
    fun setCategory(category: BrowseCategory) {
        if (_currentCategory.value == category) return
        _currentCategory.value = category
        // Reset search results when switching category
        _searchResults.value = emptyList()
        // Eagerly load the category home feed if not loaded
        loadCategoryFeed(category, "home", forceRefresh = false)
    }

    /**
     * Load feed sections for a specific category and tab
     */
    fun loadCategoryFeed(category: BrowseCategory, tabId: String, forceRefresh: Boolean = false) {
        val targetLiveData = when (category) {
            BrowseCategory.MUSIC -> _musicHomeFeed
            BrowseCategory.VIDEOS -> when (tabId) {
                "home" -> _videoHomeFeed
                "movies" -> _videoMoviesFeed
                "tv_shows" -> _videoTvShowsFeed
                else -> _videoHomeFeed
            }
        }

        // Return from cache immediately if present and refresh not forced
        if (!forceRefresh && !targetLiveData.value.isNullOrEmpty()) {
            Log.d(TAG, "Returning cached feed sections for $category - $tabId")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                // Ensure engine is initialized
                val engine = getOrInitEngine() ?: throw Exception("yt-dlp engine not available")

                // Parse config list from assets
                val filename = when (category) {
                    BrowseCategory.MUSIC -> "browse/music_home.json"
                    BrowseCategory.VIDEOS -> when (tabId) {
                        "home" -> "browse/video_home.json"
                        "movies" -> "browse/video_movies.json"
                        "tv_shows" -> "browse/video_tvshows.json"
                        else -> "browse/video_home.json"
                    }
                }

                val jsonContent = loadJsonFromAssets(filename) ?: "[]"
                val jsonArray = JSONArray(jsonContent)
                val configs = mutableListOf<QueryConfig>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    configs.add(
                        QueryConfig(
                            title = obj.getString("title"),
                            query = obj.getString("query"),
                            hero = obj.optBoolean("hero", false)
                        )
                    )
                }

                if (configs.isEmpty()) {
                    targetLiveData.postValue(emptyList())
                    return@launch
                }

                // Randomly select 6 sections to display (matching desktop strategy)
                val shuffled = configs.shuffled()
                val selectedConfigs = shuffled.take(minOf(6, shuffled.size))

                val sections = mutableListOf<FeedSection>()
                // Eagerly publish empty skeletons or placeholders to allow adapter updates
                targetLiveData.postValue(emptyList())

                // Load each section sequentially with a short delay (e.g. 500ms) to ensure responsiveness
                for ((idx, cfg) in selectedConfigs.withIndex()) {
                    try {
                        Log.d(TAG, "Loading section [${cfg.title}] with query: ${cfg.query}")
                        // Use flat-playlist searching
                        val count = if (cfg.hero) 5 else 6
                        val searchQuery = if (category == BrowseCategory.MUSIC) "${cfg.query} song" else cfg.query
                        val searchPrefix = "ytsearch$count:$searchQuery"

                        val result = engine.extractContent(searchPrefix)
                        if (result.isSuccess) {
                            val playlistContent = result.getOrNull()
                            val tracks = playlistContent?.playlistItems ?: emptyList()
                            if (tracks.isNotEmpty()) {
                                sections.add(
                                    FeedSection(
                                        id = "${category.name.lowercase()}_sec_$idx",
                                        title = cfg.title,
                                        query = cfg.query,
                                        hero = cfg.hero,
                                        tracks = tracks
                                    )
                                )
                                // Post partial updates to UI for progressive rendering
                                targetLiveData.postValue(ArrayList(sections))
                            }
                        }
                        // Short pause between yt-dlp invocations to be gentle on CPU and avoid network limits
                        delay(600)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load section: ${cfg.title}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load category feed for $category - $tabId", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Search online content
     */
    fun performSearch(query: String) {
        if (query.trim().isEmpty()) {
            _searchResults.postValue(emptyList())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                val engine = getOrInitEngine() ?: throw Exception("yt-dlp engine not available")
                
                val count = 25
                val category = _currentCategory.value ?: BrowseCategory.MUSIC
                val searchQuery = if (category == BrowseCategory.MUSIC) "$query song" else query
                val searchPrefix = "ytsearch$count:$searchQuery"

                Log.d(TAG, "Searching: $searchPrefix")
                val result = engine.extractContent(searchPrefix)
                if (result.isSuccess) {
                    val tracks = result.getOrNull()?.playlistItems ?: emptyList()
                    _searchResults.postValue(tracks)
                } else {
                    Log.e(TAG, "Search failed: ${result.exceptionOrNull()?.message}")
                    _searchResults.postValue(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search exception", e)
                _searchResults.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Resolves the streaming URLs for a track
     */
    fun resolveStreamingUrls(url: String, callback: (videoUrl: String, audioUrl: String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isResolvingStream.postValue(true)
            try {
                val engine = getOrInitEngine() ?: throw Exception("yt-dlp engine not available")
                Log.d(TAG, "Resolving streaming URL for: $url")
                val result = engine.getStreamingUrls(url)
                if (result.isSuccess) {
                    val streamingUrls = result.getOrNull()
                    if (streamingUrls != null) {
                        withContext(Dispatchers.Main) {
                            callback(streamingUrls.videoUrl, streamingUrls.audioUrl)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to resolve stream URL: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving streaming URL", e)
            } finally {
                _isResolvingStream.postValue(false)
            }
        }
    }

    private suspend fun getOrInitEngine(): YtDlpAndroidEngine? {
        if (ytDlpEngine != null) return ytDlpEngine
        
        return try {
            val engine = YtDlpAndroidEngine(context)
            val result = engine.initialize()
            if (result.isSuccess) {
                ytDlpEngine = engine
                engine
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadJsonFromAssets(path: String): String? {
        return try {
            val stream: InputStream = context.assets.open(path)
            val size: Int = stream.available()
            val buffer = ByteArray(size)
            stream.read(buffer)
            stream.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load JSON asset: $path", e)
            null
        }
    }

    private data class QueryConfig(
        val title: String,
        val query: String,
        val hero: Boolean
    )
}
