package com.animedantv.iptv

/**
 * Writes a list of channels back out in the user-preferred custom format:
 *
 * ```
 * === Group Name ===
 *
 * Channel Name
 * https://...logo.png
 * https://...stream.mpd
 * kid:key
 *
 *
 * Other Channel
 * https://...stream.m3u8
 * ```
 *
 * Channels with no group are grouped under the section title supplied via [defaultGroup]
 * (default `Uncategorized`). Inside a section, channels keep their original order.
 */
object M3uExporter {

    fun export(
        channels: List<Channel>,
        defaultGroup: String = "Uncategorized",
    ): String {
        if (channels.isEmpty()) return ""
        val grouped = LinkedHashMap<String, MutableList<Channel>>()
        for (c in channels) {
            val key = c.group?.takeIf { it.isNotBlank() } ?: defaultGroup
            grouped.getOrPut(key) { mutableListOf() } += c
        }
        val sb = StringBuilder()
        for ((group, items) in grouped) {
            sb.append("=== ").append(group).append(" ===\n\n")
            for (c in items) {
                sb.append(c.name.trim()).append('\n')
                c.logoUrl?.takeIf { it.isNotBlank() }?.let { sb.append(it.trim()).append('\n') }
                sb.append(c.streamUrl.trim()).append('\n')
                c.drmKey?.takeIf { it.contains(':') }?.let { sb.append(it.trim()).append('\n') }
                sb.append('\n')
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd() + "\n"
    }
}
