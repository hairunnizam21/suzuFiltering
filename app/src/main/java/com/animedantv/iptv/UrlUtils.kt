package com.animedantv.iptv

import java.net.URI
import java.util.Locale

/**
 * Normalizes share URLs from common providers (Google Drive, Dropbox, GitHub blob,
 * OneDrive) into direct-download URLs. Unknown hosts are passed through unchanged,
 * so it is safe to call this on every URL the user pastes.
 */
object UrlUtils {

    fun convertShareUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        return runCatching {
            val u = URI(trimmed)
            val host = (u.host ?: "").lowercase(Locale.ROOT)
            when {
                host.contains("drive.google.com") || host == "docs.google.com" -> convertDrive(trimmed)
                host.endsWith("dropbox.com") -> convertDropbox(trimmed)
                host == "github.com" -> convertGithubBlob(trimmed)
                host.endsWith("onedrive.live.com") || host.endsWith("1drv.ms") -> convertOneDrive(trimmed)
                else -> trimmed
            }
        }.getOrDefault(trimmed)
    }

    private fun convertDrive(url: String): String {
        val idPatterns = listOf(
            Regex("""/file/d/([\w-]+)"""),
            Regex("""[?&]id=([\w-]+)"""),
            Regex("""/open\?id=([\w-]+)"""),
        )
        for (re in idPatterns) {
            val m = re.find(url) ?: continue
            return "https://drive.google.com/uc?export=download&id=${m.groupValues[1]}"
        }
        return url
    }

    private fun convertDropbox(url: String): String {
        var converted = url
        if (converted.startsWith("https://www.dropbox.com")) {
            converted = converted.replace("https://www.dropbox.com", "https://dl.dropboxusercontent.com")
        }
        converted = converted.replace(Regex("""([?&])dl=0"""), "$1raw=1")
        if (!converted.contains("raw=1") && !converted.contains("dl=1")) {
            converted += if (converted.contains('?')) "&raw=1" else "?raw=1"
        }
        return converted
    }

    private fun convertGithubBlob(url: String): String {
        val m = Regex("""github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)""").find(url) ?: return url
        val (owner, repo, ref, path) = m.destructured
        return "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
    }

    private fun convertOneDrive(url: String): String =
        url.replace("action=embed", "action=download")
            .replace("action=view", "action=download")

    fun detectMimeType(url: String): String? {
        val lower = url.lowercase(Locale.ROOT)
        val pathPart = lower.substringBefore('?')
        val queryPart = lower.substringAfter('?', "")
        return when {
            ".mpd" in pathPart || "format=mpd" in queryPart || "type=dash" in queryPart ||
                "/dash" in pathPart -> "application/dash+xml"
            ".m3u8" in pathPart || ".m3u" in pathPart || "format=m3u8" in queryPart ||
                "type=hls" in queryPart || "/hls" in pathPart ||
                "/master" in pathPart || "/playlist" in pathPart -> "application/x-mpegURL"
            ".mp4" in pathPart -> "video/mp4"
            ".ts" in pathPart -> "video/MP2T"
            ".mkv" in pathPart -> "video/x-matroska"
            else -> null
        }
    }
}
