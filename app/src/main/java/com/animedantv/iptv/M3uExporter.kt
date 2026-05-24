package com.animedantv.iptv

/**
 * Writes a list of channels back out in standard `#EXTM3U` / `#EXTINF` format
 * so that ClearKey, custom User-Agent, and Referer survive a round-trip:
 *
 * ```
 * #EXTM3U
 *
 * #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Channel Name
 * #KODIPROP:inputstream.adaptive.license_type=clearkey
 * #KODIPROP:inputstream.adaptive.license_key=kid:key
 * #KODIPROP:http-user-agent=Mozilla/5.0 ...
 * #KODIPROP:http-referrer=https://example.com/
 * https://example.com/stream.mpd
 * ```
 *
 * Channels keep their original order; if any channel has a [Channel.rawSource]
 * (captured during parsing and not invalidated by an edit) we emit that block
 * verbatim, which preserves any directive the importer hasn't explicitly
 * modeled (e.g. inputstream.adaptive.manifest_headers).
 */
object M3uExporter {

    fun export(
        channels: List<Channel>,
        @Suppress("UNUSED_PARAMETER") defaultGroup: String = "Uncategorized",
    ): String {
        if (channels.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("#EXTM3U\n\n")
        for (c in channels) {
            val raw = c.rawSource?.takeIf { it.isNotBlank() }
            if (raw != null) {
                sb.append(raw.trimEnd()).append("\n\n")
            } else {
                sb.append(buildExtinfBlock(c)).append("\n\n")
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun buildExtinfBlock(c: Channel): String {
        val sb = StringBuilder()
        val attrs = buildString {
            c.tvgId?.let { append(" tvg-id=\"").append(it).append('"') }
            c.tvgName?.let { append(" tvg-name=\"").append(it).append('"') }
            c.logoUrl?.let { append(" tvg-logo=\"").append(it).append('"') }
            c.group?.let { append(" group-title=\"").append(it).append('"') }
        }
        sb.append("#EXTINF:-1").append(attrs).append(',').append(c.name.trim()).append('\n')
        c.licenseType?.let {
            sb.append("#KODIPROP:inputstream.adaptive.license_type=").append(it).append('\n')
        }
        val licenseKey = c.licenseKey?.takeIf { it.isNotBlank() }
            ?: c.drmKey?.takeIf { it.contains(':') }
        licenseKey?.let {
            sb.append("#KODIPROP:inputstream.adaptive.license_key=").append(it).append('\n')
        }
        c.manifestType?.let {
            sb.append("#KODIPROP:inputstream.adaptive.manifest_type=").append(it).append('\n')
        }
        c.userAgent?.let { sb.append("#KODIPROP:http-user-agent=").append(it).append('\n') }
        c.referer?.let { sb.append("#KODIPROP:http-referrer=").append(it).append('\n') }
        sb.append(c.streamUrl.trim())
        return sb.toString()
    }
}
