package com.animedantv.iptv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Probes each [Channel.streamUrl] with a HEAD-like request and classifies it
 * as `WORKING` or `ERROR` based on HTTP status + content-type sniffing.
 */
object HealthChecker {

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13) suzuFiltering/1.0"
    private val HLS_CONTENT_TYPES = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "vnd.apple.mpegurl",
    )
    private val DASH_CONTENT_TYPES = setOf(
        "application/dash+xml",
        "video/vnd.mpeg.dash.mpd",
    )
    private val ERROR_BODY_HINTS = listOf(
        "stream tidak ditemukan",
        "stream not found",
        "channel not found",
        "not found",
        "<!doctype html",
        "<html",
        "expired",
        "forbidden",
        "unauthorized",
        "access denied",
    )

    suspend fun check(channel: Channel, timeoutMs: Int = 8000): ChannelHealth =
        withContext(Dispatchers.IO) { probe(channel, timeoutMs) }

    suspend fun checkAll(
        channels: List<Channel>,
        concurrency: Int = 12,
        timeoutMs: Int = 8000,
        onProgress: suspend (done: Int, total: Int, latest: ChannelHealth) -> Unit = { _, _, _ -> },
    ): List<ChannelHealth> = coroutineScope {
        val sem = Semaphore(concurrency.coerceAtLeast(1))
        val total = channels.size
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val deferreds = channels.map { ch ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val result = probe(ch, timeoutMs)
                    val n = done.incrementAndGet()
                    onProgress(n, total, result)
                    result
                }
            }
        }
        deferreds.map { it.await() }
    }

    private fun probe(channel: Channel, timeoutMs: Int): ChannelHealth {
        val started = System.currentTimeMillis()
        return try {
            val conn = openConnection(channel, "HEAD", timeoutMs)
            val code = conn.responseCode
            val ct = conn.contentType?.lowercase(Locale.ROOT).orEmpty()
            val msg = "HEAD $code"
            conn.disconnect()
            if (code in 200..399 && !looksLikeError(ct)) {
                ChannelHealth(
                    channel = channel,
                    status = HealthStatus.WORKING,
                    httpCode = code,
                    message = msg,
                    responseTimeMs = System.currentTimeMillis() - started,
                )
            } else if (code == HttpURLConnection.HTTP_BAD_METHOD ||
                code == HttpURLConnection.HTTP_NOT_IMPLEMENTED ||
                code == 405 || code == 501
            ) {
                fallbackGet(channel, timeoutMs, started)
            } else {
                ChannelHealth(
                    channel = channel,
                    status = HealthStatus.ERROR,
                    httpCode = code,
                    message = "HTTP $code",
                    responseTimeMs = System.currentTimeMillis() - started,
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            fallbackGet(channel, timeoutMs, started, error = e)
        }
    }

    private fun fallbackGet(
        channel: Channel,
        timeoutMs: Int,
        started: Long,
        error: Throwable? = null,
    ): ChannelHealth = try {
        val conn = openConnection(channel, "GET", timeoutMs)
        val code = conn.responseCode
        val ct = conn.contentType?.lowercase(Locale.ROOT).orEmpty()
        val snippet = if (code in 200..299) {
            conn.inputStream.use { readFirstBytes(it, 4096).toString(Charsets.UTF_8) }
        } else {
            ""
        }
        conn.disconnect()
        val errorHint = looksLikeError(ct) || ERROR_BODY_HINTS.any { snippet.lowercase(Locale.ROOT).contains(it) }
        if (code in 200..399 && !errorHint) {
            ChannelHealth(
                channel = channel,
                status = HealthStatus.WORKING,
                httpCode = code,
                message = "GET $code",
                responseTimeMs = System.currentTimeMillis() - started,
            )
        } else {
            ChannelHealth(
                channel = channel,
                status = HealthStatus.ERROR,
                httpCode = code,
                message = if (errorHint) "HTML/error body" else "HTTP $code",
                responseTimeMs = System.currentTimeMillis() - started,
            )
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Throwable) {
        ChannelHealth(
            channel = channel,
            status = HealthStatus.ERROR,
            httpCode = null,
            message = (error ?: e).message ?: e.javaClass.simpleName,
            responseTimeMs = System.currentTimeMillis() - started,
        )
    }

    private fun openConnection(channel: Channel, method: String, timeoutMs: Int): HttpURLConnection {
        val url = URL(channel.streamUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", channel.userAgent ?: DEFAULT_UA)
            channel.referer?.let { setRequestProperty("Referer", it) }
            setRequestProperty("Accept", "*/*")
        }
        return conn
    }

    private fun looksLikeError(contentType: String): Boolean {
        if (contentType.isBlank()) return false
        val ct = contentType.substringBefore(';').trim()
        return ct.startsWith("text/html") || ct == "text/plain"
    }

    private fun readFirstBytes(stream: java.io.InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var read = 0
        while (read < max) {
            val r = stream.read(buf, read, max - read)
            if (r <= 0) break
            read += r
        }
        return buf.copyOf(read)
    }
}
