package com.animedantv.iptv

/**
 * Repairs ClearKey "KID" strings whose UUID segments lost their leading zeros
 * (a common malformation in user-pasted DRM-protected playlists).
 *
 * - `912760c4-9eb-5aff-3e06-0422c502f410`  (segment 2 should be 4 chars; here it
 *   is 3 because the leading `0` was stripped) is repaired to
 *   `912760c4-09eb-5aff-3e06-0422c502f410` and returned as the 32-char hex form.
 * - Plain 30/31-char hex strings are left-padded to 32.
 * - Non-hex characters, or segments longer than expected, return `null`.
 */
object KidUtils {

    private val UUID_SEGMENT_SIZES = intArrayOf(8, 4, 4, 4, 12)
    private val HEX_REGEX = Regex("^[0-9a-fA-F]+$")

    fun normalizeKid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return if (trimmed.contains('-')) normalizeUuid(trimmed) else normalizeHex(trimmed)
    }

    private fun normalizeUuid(uuid: String): String? {
        val parts = uuid.split('-')
        if (parts.size != UUID_SEGMENT_SIZES.size) return null
        val sb = StringBuilder(32)
        for ((idx, part) in parts.withIndex()) {
            if (!HEX_REGEX.matches(part)) return null
            val expected = UUID_SEGMENT_SIZES[idx]
            if (part.length > expected) return null
            sb.append(part.padStart(expected, '0'))
        }
        return sb.toString().lowercase()
    }

    private fun normalizeHex(hex: String): String? {
        if (!HEX_REGEX.matches(hex)) return null
        return when {
            hex.length == 32 -> hex.lowercase()
            hex.length in 30..31 -> hex.padStart(32, '0').lowercase()
            else -> null
        }
    }
}
