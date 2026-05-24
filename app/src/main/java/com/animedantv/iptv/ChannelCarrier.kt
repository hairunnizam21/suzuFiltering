package com.animedantv.iptv

/**
 * Process-singleton ferry for the large channel list between
 * [ImportActivity] -> [FilterResultActivity]. We avoid putting a
 * 10 MB playlist through an Intent (Binder transactions are capped at 1 MB)
 * by stashing the list here and only passing a flag.
 */
object ChannelCarrier {
    private var pending: List<Channel> = emptyList()

    fun set(channels: List<Channel>) {
        pending = channels
    }

    fun take(): List<Channel> {
        val out = pending
        pending = emptyList()
        return out
    }
}
