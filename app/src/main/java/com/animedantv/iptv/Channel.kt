package com.animedantv.iptv

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val drmKey: String? = null,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val manifestType: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val rawSource: String? = null,
) : Parcelable {
    val hasClearKey: Boolean
        get() = !drmKey.isNullOrBlank() && drmKey.contains(':')

    val clearKeyKid: String?
        get() = drmKey?.substringBefore(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

    val clearKeyKey: String?
        get() = drmKey?.substringAfter(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
}
