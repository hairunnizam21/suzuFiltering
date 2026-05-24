package com.animedantv.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesStandardExtinfPlaylist() {
        val txt = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tv1" tvg-name="TV1" tvg-logo="https://example.com/tv1.png" group-title="MY",TV1
            https://example.com/tv1.m3u8
            #EXTINF:-1,TV2
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=abc:def
            https://example.com/tv2.mpd
        """.trimIndent()
        val out = M3uParser.parse(txt)
        assertEquals(2, out.size)
        assertEquals("TV1", out[0].name)
        assertEquals("https://example.com/tv1.m3u8", out[0].streamUrl)
        assertEquals("https://example.com/tv1.png", out[0].logoUrl)
        assertEquals("MY", out[0].group)
        assertEquals("https://example.com/tv2.mpd", out[1].streamUrl)
        assertEquals("clearkey", out[1].licenseType)
        assertEquals("abc:def", out[1].drmKey)
    }

    @Test
    fun parsesUserCustomSectionFormat() {
        val txt = """
            === TV Malaysia ===

            TV1
            https://example.com/tv1.png
            https://example.com/tv1.mpd
            912760c409eb5aff3e060422c502f410:bea2d0f89fb3fbafa1fc9f34ba8734a6


            TV2
            https://example.com/tv2.mpd


            === TV Indonesia ===

            GTV
            https://example.com/gtv.png
            https://example.com/gtv.mpd
            036e85de0bb448eeb21d39ab300da48e:4c6f9b15dfab2a169b2b78a498c4d77d

        """.trimIndent()
        val out = M3uParser.parse(txt)
        assertEquals(3, out.size)
        assertEquals("TV1", out[0].name)
        assertEquals("https://example.com/tv1.png", out[0].logoUrl)
        assertEquals("https://example.com/tv1.mpd", out[0].streamUrl)
        assertEquals("912760c409eb5aff3e060422c502f410:bea2d0f89fb3fbafa1fc9f34ba8734a6", out[0].drmKey)
        assertEquals("TV Malaysia", out[0].group)
        assertEquals("TV2", out[1].name)
        assertNull(out[1].logoUrl)
        assertEquals("https://example.com/tv2.mpd", out[1].streamUrl)
        assertNull(out[1].drmKey)
        assertEquals("TV Indonesia", out[2].group)
        assertEquals("GTV", out[2].name)
    }

    @Test
    fun handlesEmptyInput() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("\n\n\n").isEmpty())
    }
}
