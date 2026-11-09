package com.lunify.player.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ==============================================================================
 * Lunify - Coil Bitmap Loader (CoilBitmapLoader.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the artwork loader for our Android Media3 notification!
 *
 * WHAT DOES THIS CLASS DO?
 * ------------------------
 * When a song plays, Android's system notification shade and lock screen player
 * need to render high-resolution artwork for the track (album covers from local
 * ID3 tags or online YouTube Music thumbnail URLs).
 *
 * This class implements Android Media3's [BitmapLoader] interface by delegating
 * image fetching and decoding to [Coil].
 *
 * IMPORTANT PRODUCTION SAFETY RULES:
 * 1. `allowHardware(false)`: Notifications and RemoteViews are displayed across
 *    process boundaries by Android's SystemUI. Android CANNOT share hardware bitmaps
 *    across processes without crashes or memory corruption. We strictly enforce
 *    standard software [Bitmap.Config.ARGB_8888] bitmaps here.
 * 2. Dimension Capping: Certain custom OEM ROMs (e.g. Xiaomi HyperOS/MIUI,
 *    Oppo ColorOS) enforce strict bitmap dimension limits (`config_mediaMetadataBitmapMaxSize`).
 *    If an app passes an overly large artwork bitmap, the system rescales it and recycles
 *    the source bitmap, causing subsequent metadata calls to fail with:
 *    "cannot use a recycled source in createBitmap". We pass the maximum platform
 *    dimension to Coil's `.size()` and decode scaler so bitmaps are safely sized.
 * ==============================================================================
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : BitmapLoader {

    companion object {
        private const val TAG = "CoilBitmapLoader"
        private const val DEFAULT_ARTWORK_LIMIT_DP = 320f
    }

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val maxDim = getMaxArtworkSizePx(context)
                val rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (rawBitmap != null) {
                    if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
                        val targetWidth = (rawBitmap.width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (rawBitmap.height * scale).roundToInt().coerceAtLeast(1)
                        val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                        future.set(scaledBitmap)
                    } else {
                        future.set(rawBitmap)
                    }
                } else {
                    future.setException(IllegalArgumentException("Failed to decode raw byte array into a Bitmap"))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "decodeBitmap error: ${t.message}", t)
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val maxDim = getMaxArtworkSizePx(context)
                // Configure Coil image request strictly with software bitmaps capped at platform size
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(maxDim, maxDim)
                    .allowHardware(false)
                    .build()

                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val bitmap = result.drawable.toBitmap()
                        future.set(bitmap)
                    }
                    is ErrorResult -> {
                        Log.w(TAG, "Failed to load artwork from uri: $uri, error: ${result.throwable.message}")
                        future.setException(result.throwable)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "loadBitmap exception: ${t.message}", t)
                future.setException(t)
            }
        }
        return future
    }

    /**
     * Resolves the system's `config_mediaMetadataBitmapMaxSize` dimension from Android resources,
     * falling back to 320dp standard AOSP media artwork dimension.
     */
    @SuppressLint("DiscouragedApi")
    private fun getMaxArtworkSizePx(context: Context): Int {
        val resources = context.resources
        val id = resources.getIdentifier("config_mediaMetadataBitmapMaxSize", "dimen", "android")
        if (id != 0) {
            val fromRom = resources.getDimensionPixelSize(id)
            if (fromRom > 0) return fromRom
        }
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_ARTWORK_LIMIT_DP,
            resources.displayMetrics,
        ).roundToInt()
    }
}
