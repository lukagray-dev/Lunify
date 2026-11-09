package com.lunify.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * ==============================================================================
 * Lunify - Player Cache Manager (PlayerCacheManager.kt)
 * ==============================================================================
 *
 * Hey friend! This singleton manages the persistent disk cache for ExoPlayer.
 *
 * Why is this critical?
 * 1. Bandwidth Saving: When a user listens to a song, chunks are saved to disk.
 *    If they replay it or scrub back and forth, zero new network requests are made.
 * 2. Instant Replay: Songs start playing immediately without buffering delays.
 * 3. Media3 SingleCache Rule: In Android Media3, instantiating multiple [SimpleCache]
 *    instances targeting the same directory causes a fatal crash. Managing it as a
 *    thread-safe singleton guarantees safe sharing across the app.
 *
 * Cache Configuration:
 * - Location: `context.cacheDir/media_cache`
 * - Max Size: 250 MB using Least-Recently-Used (LRU) automatic eviction.
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
object PlayerCacheManager {

    private const val MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L // 250 MB

    @Volatile
    private var instance: SimpleCache? = null

    private val lock = Any()

    /**
     * Retrieves or creates the shared [SimpleCache] instance for audio streaming.
     */
    fun getInstance(context: Context): SimpleCache {
        return instance ?: synchronized(lock) {
            instance ?: run {
                val cacheFolder = File(context.cacheDir, "media_cache").apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
                SimpleCache(cacheFolder, evictor, databaseProvider).also {
                    instance = it
                }
            }
        }
    }
}
