package com.animedantv.iptv

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class HealthStatus { WORKING, ERROR }

@Parcelize
data class ChannelHealth(
    val channel: Channel,
    val status: HealthStatus,
    val httpCode: Int? = null,
    val message: String = "",
    val responseTimeMs: Long = 0L,
) : Parcelable {
    val isWorking: Boolean get() = status == HealthStatus.WORKING
}
