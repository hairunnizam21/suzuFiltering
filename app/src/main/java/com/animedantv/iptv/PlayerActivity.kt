package com.animedantv.iptv

import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.animedantv.iptv.databinding.ActivityPlayerBinding
import org.json.JSONArray
import org.json.JSONObject

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        val channel = readChannelExtra()
        if (channel == null) {
            Toast.makeText(this, R.string.err_no_channels, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val streamUrl = UrlUtils.convertShareUrl(channel.streamUrl)
        val mime = UrlUtils.detectMimeType(streamUrl)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(streamUrl)
            .apply { if (mime != null) setMimeType(mime) }

        if (channel.hasClearKey) {
            val normalizedKid = KidUtils.normalizeKid(channel.clearKeyKid)
            val key = channel.clearKeyKey
            if (normalizedKid != null && !key.isNullOrBlank()) {
                val licenseJson = clearKeyLicense(normalizedKid, key)
                val drm = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setKeySetId(null)
                    .setLicenseUri("data:application/json;base64," + Base64.encodeToString(licenseJson.toByteArray(), Base64.NO_WRAP))
                    .build()
                mediaItemBuilder.setDrmConfiguration(drm)
            }
        }

        val mediaItem = mediaItemBuilder.build()
        player = ExoPlayer.Builder(this).build().also { p ->
            binding.playerView.player = p
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
        }
    }

    @Suppress("DEPRECATION")
    private fun readChannelExtra(): Channel? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_CHANNEL, Channel::class.java)
    } else {
        intent.getParcelableExtra(EXTRA_CHANNEL)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun clearKeyLicense(kidHex: String, keyHex: String): String {
        val kidB64 = base64UrlNoPad(hexToBytes(kidHex))
        val keyB64 = base64UrlNoPad(hexToBytes(keyHex))
        val key = JSONObject()
            .put("kty", "oct")
            .put("k", keyB64)
            .put("kid", kidB64)
        val root = JSONObject()
            .put("keys", JSONArray().put(key))
            .put("type", "temporary")
        return root.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace("-", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or
                Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        const val EXTRA_CHANNEL = "channel"
    }
}
