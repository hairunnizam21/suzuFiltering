package com.animedantv.iptv

import java.util.Locale

/**
 * Parses both the standard `#EXTM3U` / `#EXTINF` syntax and the custom "section"
 * format that suzuFiltering downloads emit:
 *
 * ```
 * === TV Malaysia ===
 *
 * TV1
 * https://example.com/tv1.png         <- optional logo
 * https://example.com/tv1.mpd          <- stream URL
 * 912760c4...:bea2d0f8...               <- optional ClearKey "KID:KEY"
 * ```
 *
 * Channel blocks in custom mode are separated by blank lines. The logo line is
 * detected by looking for a known image extension or by sniffing the host; if no
 * logo is present the block can be just `name + URL` or `name + URL + DRM`.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([\w-]+)="([^"]*)"""")
    private val HEX_DRM_REGEX = Regex("""^[0-9a-fA-F]{16,32}\s*:\s*[0-9a-fA-F]{16,32}$""")
    private val SECTION_REGEX = Regex("""^=+\s*([^=].*?)\s*=+$""")
    private val IMAGE_EXTS = listOf(
        ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".bmp", ".ico",
    )
    private val IMAGE_HOST_HINTS = listOf(
        "iili.io", "imgur", "seeklogo", "gstatic.com", "wikimedia.org",
        "image-resizer", "logoeps.com", "logos-world.net", "static.wikia",
    )
    private val IMAGE_PATH_HINTS = listOf(
        "/logo", "/logos/", "/image", "/img/", "/poster", "/cover",
    )

    fun parse(content: String): List<Channel> {
        if (content.isBlank()) return emptyList()
        val first = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (first.startsWith("#EXTM3U", ignoreCase = true) ||
            first.startsWith("#EXTINF", ignoreCase = true)
        ) {
            parseExtinf(content)
        } else {
            parseCustom(content)
        }
    }

    private fun parseExtinf(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pending: PendingChannel? = null
        val preExtinfProps = mutableListOf<String>()
        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", ignoreCase = true)) continue
            if (line.startsWith("#EXT-X-", ignoreCase = true)) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = PendingChannel()
                    parseExtinfHeader(line, pending)
                    preExtinfProps.forEach { applyProp(it, pending!!) }
                    preExtinfProps.clear()
                }
                line.startsWith("#KODIPROP", ignoreCase = true) ||
                    line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    pending?.let { applyProp(line, it) } ?: preExtinfProps.add(line)
                }
                !line.startsWith("#") -> {
                    pending?.let { p ->
                        if (p.name.isNotBlank() || true) {
                            channels.add(p.toChannel(line))
                        }
                    }
                    pending = null
                    preExtinfProps.clear()
                }
            }
        }
        return channels
    }

    private fun parseExtinfHeader(line: String, p: PendingChannel) {
        val commaIdx = line.indexOf(',')
        val header = if (commaIdx >= 0) line.substring(0, commaIdx) else line
        val display = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
        for (m in ATTR_REGEX.findAll(header)) {
            val key = m.groupValues[1].lowercase(Locale.ROOT)
            val value = m.groupValues[2]
            when (key) {
                "tvg-id" -> p.tvgId = value
                "tvg-name" -> p.tvgName = value
                "tvg-logo" -> p.logoUrl = value
                "group-title" -> p.group = value
            }
        }
        p.name = display.ifBlank { p.tvgName.orEmpty() }
    }

    private fun applyProp(line: String, p: PendingChannel) {
        val sep = line.indexOf(':')
        if (sep < 0) return
        val body = line.substring(sep + 1)
        val eq = body.indexOf('=')
        if (eq < 0) return
        val key = body.substring(0, eq).trim().lowercase(Locale.ROOT)
        val value = body.substring(eq + 1).trim()
        when (key) {
            "http-user-agent" -> p.userAgent = value
            "http-referrer", "http-referer" -> p.referer = value
            "inputstream.adaptive.license_type" -> p.licenseType = value
            "inputstream.adaptive.license_key" -> p.licenseKey = value
            "inputstream.adaptive.manifest_type" -> p.manifestType = value
        }
    }

    private fun parseCustom(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentGroup: String? = null
        var block = mutableListOf<String>()
        fun flushBlock() {
            val ch = blockToChannel(block, currentGroup)
            if (ch != null) channels += ch
            block = mutableListOf()
        }
        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                flushBlock()
                continue
            }
            val sectionMatch = SECTION_REGEX.matchEntire(line)
            if (sectionMatch != null) {
                flushBlock()
                currentGroup = sectionMatch.groupValues[1].trim()
                continue
            }
            block += line
        }
        flushBlock()
        return channels
    }

    private fun blockToChannel(block: List<String>, group: String?): Channel? {
        if (block.isEmpty()) return null
        var name: String? = null
        var logo: String? = null
        var stream: String? = null
        var drm: String? = null

        for (line in block) {
            when {
                stream == null && isStreamUrl(line) -> stream = line
                logo == null && isImageUrl(line) -> logo = line
                drm == null && HEX_DRM_REGEX.matches(line) -> drm = line
                name == null && !line.startsWith("http", ignoreCase = true) -> name = line
                stream == null && line.startsWith("http", ignoreCase = true) -> stream = line
            }
        }
        if (stream.isNullOrBlank()) return null
        return Channel(
            name = (name ?: stream).trim(),
            streamUrl = stream.trim(),
            logoUrl = logo?.trim(),
            group = group,
            drmKey = drm?.trim(),
        )
    }

    private fun isImageUrl(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        val lower = url.lowercase(Locale.ROOT)
        if (IMAGE_EXTS.any { lower.contains(it) }) return true
        if (IMAGE_HOST_HINTS.any { lower.contains(it) }) return true
        if (IMAGE_PATH_HINTS.any { lower.contains(it) }) return true
        return false
    }

    private fun isStreamUrl(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        val lower = url.lowercase(Locale.ROOT)
        if (lower.contains(".mpd") || lower.contains(".m3u8") || lower.contains(".m3u")) return true
        if (lower.contains(".ts") || lower.contains(".mp4") || lower.contains(".mkv")) return true
        if (lower.contains("/manifest") || lower.contains("/playlist") || lower.contains("/master")) return true
        if (lower.contains("/dash") || lower.contains("/hls")) return true
        if (lower.contains("format=mpd") || lower.contains("format=m3u8")) return true
        if (lower.contains("type=hls") || lower.contains("type=dash")) return true
        return false
    }

    private class PendingChannel(
        var name: String = "",
        var tvgId: String? = null,
        var tvgName: String? = null,
        var logoUrl: String? = null,
        var group: String? = null,
        var licenseType: String? = null,
        var licenseKey: String? = null,
        var manifestType: String? = null,
        var userAgent: String? = null,
        var referer: String? = null,
    ) {
        fun toChannel(streamUrl: String): Channel {
            val drm = if (licenseType?.equals("clearkey", ignoreCase = true) == true) licenseKey else null
            return Channel(
                name = if (name.isNotBlank()) name else streamUrl,
                streamUrl = streamUrl,
                logoUrl = logoUrl,
                group = group,
                tvgId = tvgId,
                tvgName = tvgName,
                drmKey = drm,
                licenseType = licenseType,
                licenseKey = licenseKey,
                manifestType = manifestType,
                userAgent = userAgent,
                referer = referer,
            )
        }
    }
}
