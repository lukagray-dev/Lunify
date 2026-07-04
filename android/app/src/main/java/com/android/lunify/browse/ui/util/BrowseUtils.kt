package com.android.lunify.browse.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.lunify.browse.ui.viewmodel.BrowseCategory
import com.android.lunify.browse.ui.viewmodel.BrowseViewModel
import com.android.lunify.data.model.Song
import com.android.lunify.download.data.model.ExtractedContent
import com.android.lunify.service.MusicService
import com.android.lunify.videoplayer.ui.VideoPlayerActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages local search and play history persistence using JSON
 */
object BrowseHistoryManager {
    
    private const val TAG = "BrowseHistoryManager"
    private const val FILENAME = "browse_history.json"
    
    /**
     * Get recently played online tracks
     */
    @Synchronized
    fun getHistory(context: Context): List<ExtractedContent> {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) return emptyList()
        
        val list = mutableListOf<ExtractedContent>()
        try {
            val content = file.readText()
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ExtractedContent(
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotEmpty() },
                        duration = obj.optString("duration").takeIf { it.isNotEmpty() },
                        author = obj.optString("author").takeIf { it.isNotEmpty() },
                        platform = obj.optString("platform", "YouTube")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history list", e)
        }
        return list
    }
    
    /**
     * Add an item to history
     */
    @Synchronized
    fun recordPlay(context: Context, item: ExtractedContent) {
        try {
            val currentList = getHistory(context).toMutableList()
            // Remove duplicates
            currentList.removeAll { it.url == item.url }
            // Add to front
            currentList.add(0, item)
            // Limit size to 50
            val limitedList = currentList.take(50)
            
            // Serialize
            val array = JSONArray()
            for (track in limitedList) {
                val obj = JSONObject().apply {
                    put("url", track.url)
                    put("title", track.title)
                    put("thumbnailUrl", track.thumbnailUrl ?: "")
                    put("duration", track.duration ?: "")
                    put("author", track.author ?: "")
                    put("platform", track.platform)
                }
                array.put(obj)
            }
            
            val file = File(context.filesDir, FILENAME)
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save track play in history", e)
        }
    }
}

/**
 * Parse standard duration string (H:M:S or M:S) to milliseconds
 */
fun parseDurationToMs(durationStr: String?): Long {
    if (durationStr.isNullOrEmpty()) return 0L
    val parts = durationStr.split(":")
    var seconds = 0L
    try {
        if (parts.size == 2) {
            seconds = parts[0].toLong() * 60 + parts[1].toLong()
        } else if (parts.size == 3) {
            seconds = parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
        }
    } catch (_: Exception) {}
    return seconds * 1000L
}

/**
 * Handle playback resolution and trigger appropriate player Activity/Service
 */
fun playOnlineTrack(fragment: Fragment, viewModel: BrowseViewModel, item: ExtractedContent, category: BrowseCategory) {
    val context = fragment.requireContext()
    Toast.makeText(context, "Extracting audio/video stream links...", Toast.LENGTH_SHORT).show()
    
    viewModel.resolveStreamingUrls(item.url) { videoUrl, audioUrl ->
        if (videoUrl.isNotEmpty()) {
            // Save to local play history
            BrowseHistoryManager.recordPlay(context, item)
            
            if (category == BrowseCategory.MUSIC) {
                // Play music track via MusicService (MediaPlayer)
                val directAudioUrl = audioUrl ?: videoUrl
                val song = Song(
                    id = item.url.hashCode().toLong(),
                    title = item.title,
                    artist = item.author ?: "YouTube Music",
                    album = "Lunify Music",
                    duration = parseDurationToMs(item.duration),
                    path = directAudioUrl,
                    albumArtUri = if (!item.thumbnailUrl.isNullOrEmpty()) Uri.parse(item.thumbnailUrl) else null
                )
                
                val intent = Intent(context, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY
                    putExtra(MusicService.EXTRA_SONG, song)
                }
                context.startService(intent)
            } else {
                // Play video track via VideoPlayerActivity (ExoPlayer)
                VideoPlayerActivity.start(
                    context = context,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    title = item.title
                )
            }
        } else {
            Toast.makeText(context, "Failed to resolve streaming link from yt-dlp", Toast.LENGTH_SHORT).show()
        }
    }
}
