package com.android.lunify.download.engine.manager

import android.content.Context
import android.util.Log
import com.android.lunify.download.engine.core.DownloadEngine
import com.android.lunify.download.engine.core.EngineInfo
import com.android.lunify.download.engine.ytdlp.YtDlpAndroidEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Lightweight manager for the bundled yt-dlp runtime.
 *
 * The app no longer supports user-managed installs or swaps. This class is now
 * just a small lifecycle wrapper that keeps the embedded engine initialized and
 * exposes a stable status snapshot to the rest of the app.
 */
class YtDlpAndroidEngineManager(
    private val context: Context
) : EngineManager {

    private var engine: YtDlpAndroidEngine? = null

    private val _engineInfo = MutableStateFlow(EngineInfo.unavailable(ENGINE_NAME))
    override val engineInfo: StateFlow<EngineInfo> = _engineInfo.asStateFlow()

    companion object {
        private const val TAG = "YtDlpAndroidEngineMgr"
        private const val ENGINE_NAME = "yt-dlp"
        private const val DEFAULT_VERSION = "2024.12.16"
    }

    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing bundled yt-dlp engine...")

                val currentEngine = engine ?: YtDlpAndroidEngine(context).also { engine = it }
                val initResult = currentEngine.initialize()

                if (initResult.isSuccess) {
                    val version = currentEngine.getInstalledVersion() ?: DEFAULT_VERSION
                    _engineInfo.value = EngineInfo.installed(
                        name = ENGINE_NAME,
                        installedVersion = version,
                        lastChecked = System.currentTimeMillis(),
                        binaryPath = null
                    )
                    Log.d(TAG, "Bundled yt-dlp engine initialized successfully. Version: $version")
                } else {
                    engine = null
                    _engineInfo.value = EngineInfo.unavailable(ENGINE_NAME)
                    Log.e(
                        TAG,
                        "Bundled yt-dlp engine failed to initialize: ${initResult.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                engine = null
                _engineInfo.value = EngineInfo.unavailable(ENGINE_NAME)
                Log.e(TAG, "Failed to initialize bundled yt-dlp engine", e)
            }
        }
    }

    override fun getEngine(): DownloadEngine? = engine

    override fun getEnginePath(): String = context.filesDir.absolutePath

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("youtubedl")) {
                        file.deleteRecursively()
                    }
                }
                Log.d(TAG, "Bundled yt-dlp cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear bundled yt-dlp cache", e)
            }
        }
    }
}
