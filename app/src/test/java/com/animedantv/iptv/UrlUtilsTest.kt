package com.animedantv.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun convertsDrive() {
        assertEquals(
            "https://drive.google.com/uc?export=download&id=ABC123",
            UrlUtils.convertShareUrl("https://drive.google.com/file/d/ABC123/view"),
        )
    }

    @Test
    fun convertsGithubBlob() {
        assertEquals(
            "https://raw.githubusercontent.com/o/r/main/playlist.m3u8",
            UrlUtils.convertShareUrl("https://github.com/o/r/blob/main/playlist.m3u8"),
        )
    }

    @Test
    fun convertsDropbox() {
        val out = UrlUtils.convertShareUrl("https://www.dropbox.com/s/abc/x.m3u8?dl=0")
        assertEquals("https://dl.dropboxusercontent.com/s/abc/x.m3u8?raw=1", out)
    }

    @Test
    fun unknownHostPassesThrough() {
        val u = "https://random.example.com/playlist.m3u8"
        assertEquals(u, UrlUtils.convertShareUrl(u))
    }

    @Test
    fun detectsMimeTypeFromUrl() {
        assertEquals("application/dash+xml", UrlUtils.detectMimeType("https://x.com/stream.mpd"))
        assertEquals("application/x-mpegURL", UrlUtils.detectMimeType("https://x.com/stream.m3u8"))
        assertEquals("application/x-mpegURL", UrlUtils.detectMimeType("https://x.com/api/stream?format=m3u8"))
    }
}
