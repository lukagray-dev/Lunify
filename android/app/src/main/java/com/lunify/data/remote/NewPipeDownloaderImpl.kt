package com.lunify.data.remote

import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * ==============================================================================
 * Lunify - NewPipe Downloader Implementation (NewPipeDownloaderImpl.kt)
 * ==============================================================================
 *
 * Hey friend! This class bridges the PipePipe / NewPipe extraction engine with
 * OkHttp, our production HTTP networking layer.
 *
 * Why do we need this?
 * The NewPipeExtractor library is designed to be networking-agnostic: it defines
 * an abstract [Downloader] interface, and leaves the actual HTTP calls up to
 * the application. Here, we implement [execute] and [executeAsync] using OkHttp
 * with connection pooling, gzip/deflate support, and realistic browser headers.
 *
 * This allows NewPipe to fetch YouTube player pages, decryption tokens, and
 * stream metadata reliably without getting blocked.
 * ==============================================================================
 */
class NewPipeDownloaderImpl(
    proxy: Proxy? = null,
) : Downloader() {

    // Production OkHttpClient with generous timeouts for audio stream parsing
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .proxy(proxy)
        .build()

    companion object {
        private const val USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    /**
     * Executes a synchronous HTTP request on the caller's thread.
     * NewPipe calls this during stream extraction tasks.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val okHttpRequest = buildOkHttpRequest(request)
        val response = client.newCall(okHttpRequest).execute()

        // YouTube rate limit or bot challenge
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        return response.toNewPipeResponse()
    }

    /**
     * Executes an asynchronous HTTP request with callback handling.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun executeAsync(
        request: Request,
        callback: AsyncCallback?,
    ): CancellableCall {
        val okHttpRequest = buildOkHttpRequest(request)
        val call = client.newCall(okHttpRequest)
        val cancellable = CancellableCall(call)

        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancellable.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    try {
                        if (response.code == 429) {
                            response.close()
                            callback?.onError(
                                ReCaptchaException("reCaptcha Challenge requested", request.url()),
                            )
                            return
                        }
                        callback?.onSuccess(response.toNewPipeResponse())
                    } catch (e: Exception) {
                        callback?.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    /**
     * Converts an OkHttp [okhttp3.Response] into NewPipe's [Response] model,
     * decoding the raw byte body into UTF-8 text while preserving headers and status.
     */
    private fun okhttp3.Response.toNewPipeResponse(): Response {
        val rawBytes = body?.bytes() ?: ByteArray(0)
        return Response(
            code,
            message,
            headers.toMultimap(),
            rawBytes.toString(Charsets.UTF_8),
            rawBytes,
            request.url.toString(),
        )
    }

    /**
     * Translates NewPipe's [Request] into a native OkHttp [okhttp3.Request].
     * Injects standard browser headers like User-Agent to mirror official YouTube clients.
     */
    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT_WEB)

        // Transfer all headers requested by NewPipeExtractor
        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}
