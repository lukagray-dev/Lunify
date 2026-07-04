package com.android.lunify.download.engine.manager

import android.content.Context
import com.android.lunify.download.engine.core.DownloadEngine
import com.android.lunify.download.engine.core.EngineInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the bundled download engine lifecycle.
 *
 * The app initializes yt-dlp at startup and keeps it available for the rest of
 * the session. There is no user-facing install, replace, or update flow here.
 */
interface EngineManager {
    
    /**
     * Current engine information as a StateFlow
     */
    val engineInfo: StateFlow<EngineInfo>
    
    /**
     * Initialize the bundled engine.
     * Should be called when the app starts or before the first extraction.
     */
    suspend fun initialize()
    
    /**
     * Get the current download engine instance
     * Returns null if the bundled runtime failed to initialize
     */
    fun getEngine(): DownloadEngine?
    
    /**
     * Get the path where engine binaries are stored
     */
    fun getEnginePath(): String
    
    /**
     * Clear engine cache and temporary files
     */
    suspend fun clearCache()
}

/**
 * Factory for creating EngineManager instances
 */
object EngineManagerFactory {
    
    @Volatile
    private var instance: EngineManager? = null
    
    fun getInstance(context: Context): EngineManager {
        return instance ?: synchronized(this) {
            instance ?: createEngineManager(context).also { instance = it }
        }
    }
    
    private fun createEngineManager(context: Context): EngineManager {
        return YtDlpAndroidEngineManager(context.applicationContext)
    }
}
