package com.animedantv.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.animedantv.iptv.databinding.ActivityImportBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Three input modes:
 *
 * - **URL**: paste a URL, app fetches the text (handles GitHub blob / Drive / Dropbox
 *   share URLs via [UrlUtils]).
 * - **PASTE**: paste the playlist text directly.
 * - **FILE**: pick a `.m3u` / `.m3u8` / `.txt` from the system file picker.
 *
 * Each path reads on `Dispatchers.IO`, caps the input at [MAX_BYTES], and surfaces
 * any I/O failure as a Toast instead of crashing.
 */
class ImportActivity : AppCompatActivity() {

    private enum class Mode { URL, PASTE, FILE }

    private lateinit var binding: ActivityImportBinding
    private var mode = Mode.URL
    private var pickedUri: Uri? = null
    private var pickedName: String? = null

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedName = uri.lastPathSegment
            binding.txtFileName.text = pickedName ?: uri.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            title = getString(R.string.title_import)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_url))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_paste))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_file))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mode = Mode.values()[tab.position]
                refreshPanels()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        refreshPanels()

        binding.btnPickFile.setOnClickListener {
            openFile.launch(arrayOf("*/*"))
        }
        binding.btnImport.setOnClickListener { onImportClicked() }
    }

    private fun refreshPanels() {
        binding.layoutUrl.visibility = if (mode == Mode.URL) View.VISIBLE else View.GONE
        binding.layoutPaste.visibility = if (mode == Mode.PASTE) View.VISIBLE else View.GONE
        binding.layoutFile.visibility = if (mode == Mode.FILE) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun onImportClicked() {
        binding.progress.visibility = View.VISIBLE
        binding.btnImport.isEnabled = false
        lifecycleScope.launch {
            val text = try {
                when (mode) {
                    Mode.URL -> fetchUrl(binding.editUrl.text.toString().trim())
                    Mode.PASTE -> binding.editPaste.text.toString()
                    Mode.FILE -> readUri(pickedUri ?: error("No file picked"))
                }
            } catch (e: Throwable) {
                binding.progress.visibility = View.GONE
                binding.btnImport.isEnabled = true
                Toast.makeText(this@ImportActivity, getString(R.string.err_import, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                return@launch
            }
            val channels = withContext(Dispatchers.Default) { M3uParser.parse(text) }
            binding.progress.visibility = View.GONE
            binding.btnImport.isEnabled = true
            if (channels.isEmpty()) {
                Toast.makeText(this@ImportActivity, R.string.err_no_channels, Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = Intent(this@ImportActivity, FilterResultActivity::class.java)
            ChannelCarrier.set(channels)
            startActivity(intent)
        }
    }

    private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        if (url.isEmpty()) throw IllegalArgumentException("URL kosong")
        val finalUrl = UrlUtils.convertShareUrl(url)
        val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "suzuFiltering/1.0 (Android)")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            conn.inputStream.use { input ->
                val sb = StringBuilder()
                var totalRead = 0L
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val charBuf = CharArray(8 * 1024)
                    while (true) {
                        val n = reader.read(charBuf)
                        if (n <= 0) break
                        totalRead += n
                        if (totalRead > MAX_BYTES) throw IllegalStateException(getString(R.string.err_too_large))
                        sb.append(charBuf, 0, n)
                    }
                }
                sb.toString()
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private suspend fun readUri(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { input ->
            val sb = StringBuilder()
            var totalRead = 0L
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val charBuf = CharArray(8 * 1024)
                while (true) {
                    val n = reader.read(charBuf)
                    if (n <= 0) break
                    totalRead += n
                    if (totalRead > MAX_BYTES) throw IllegalStateException(getString(R.string.err_too_large))
                    sb.append(charBuf, 0, n)
                }
            }
            sb.toString()
        } ?: error("Cannot open file")
    }

    private companion object {
        // 50 MB. Anything bigger is almost certainly not a playlist (the user's
        // typical pasted file in the field is ~10 MB; 50 MB gives headroom).
        const val MAX_BYTES: Long = 50L * 1024 * 1024
    }
}
