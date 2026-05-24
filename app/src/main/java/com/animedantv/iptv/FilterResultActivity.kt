package com.animedantv.iptv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.animedantv.iptv.databinding.ActivityFilterResultBinding
import com.animedantv.iptv.databinding.DialogEditChannelBinding
import com.animedantv.iptv.databinding.DialogViewSourceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FilterResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterResultBinding
    private lateinit var adapter: ChannelHealthAdapter

    private val allResults: MutableList<ChannelHealth> = mutableListOf()
    private var showWorking: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            title = getString(R.string.title_filter_result)
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = ChannelHealthAdapter(
            onClick = { h -> openInPlayer(h) },
            onLongClick = { h ->
                showActionsFor(h)
                true
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.tab_working_n, 0)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.tab_error_n, 0)))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showWorking = tab.position == 0
                refresh()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.setQuery(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnDownloadWorking.setOnClickListener { downloadFiltered(working = true) }
        binding.btnDownloadError.setOnClickListener { downloadFiltered(working = false) }

        val channels = ChannelCarrier.take()
        if (channels.isEmpty()) {
            Toast.makeText(this, R.string.err_no_channels, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startCheck(channels)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun startCheck(channels: List<Channel>) {
        binding.txtStatus.text = getString(R.string.checking_progress, 0, channels.size)
        binding.progress.max = channels.size
        binding.progress.progress = 0
        allResults.clear()
        updateTabBadges()
        refresh()
        lifecycleScope.launch {
            HealthChecker.checkAll(channels) { done, total, latest ->
                withContext(Dispatchers.Main) {
                    allResults += latest
                    binding.progress.progress = done
                    binding.txtStatus.text = getString(R.string.checking_progress, done, total)
                    updateTabBadges()
                    if (latest.isWorking == showWorking) {
                        refresh()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                refresh()
                val working = allResults.count { it.isWorking }
                binding.txtStatus.text =
                    getString(R.string.check_complete, working, allResults.size - working)
            }
        }
    }

    private fun updateTabBadges() {
        val working = allResults.count { it.isWorking }
        val error = allResults.size - working
        binding.tabs.getTabAt(0)?.text = getString(R.string.tab_working_n, working)
        binding.tabs.getTabAt(1)?.text = getString(R.string.tab_error_n, error)
    }

    private fun refresh() {
        val filtered = allResults.filter { it.isWorking == showWorking }
        adapter.submit(filtered)
        binding.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openInPlayer(h: ChannelHealth) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_CHANNEL, h.channel)
        }
        startActivity(intent)
    }

    private fun showActionsFor(h: ChannelHealth) {
        val moveLabel = if (h.isWorking) R.string.action_move_to_error else R.string.action_move_to_working
        val items = arrayOf(
            getString(R.string.action_play),
            getString(R.string.action_edit),
            getString(R.string.action_view_source),
            getString(moveLabel),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(h.channel.name.ifBlank { getString(R.string.untitled_channel) })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openInPlayer(h)
                    1 -> showEditDialog(h)
                    2 -> showSourceDialog(h)
                    3 -> moveChannel(h)
                }
            }
            .show()
    }

    private fun moveChannel(h: ChannelHealth) {
        val idx = allResults.indexOfFirst { it === h }
        if (idx < 0) return
        val newStatus = if (h.isWorking) HealthStatus.ERROR else HealthStatus.WORKING
        allResults[idx] = h.copy(status = newStatus, message = "manual")
        updateTabBadges()
        refresh()
    }

    private fun showEditDialog(h: ChannelHealth) {
        val ch = h.channel
        val dlgBinding = DialogEditChannelBinding.inflate(LayoutInflater.from(this))
        dlgBinding.inputName.setText(ch.name)
        dlgBinding.inputGroup.setText(ch.group.orEmpty())
        dlgBinding.inputStreamUrl.setText(ch.streamUrl)
        dlgBinding.inputLicenseType.setText(ch.licenseType.orEmpty())
        dlgBinding.inputLicenseKey.setText(ch.licenseKey ?: ch.drmKey.orEmpty())
        dlgBinding.inputUserAgent.setText(ch.userAgent.orEmpty())
        dlgBinding.inputReferer.setText(ch.referer.orEmpty())
        dlgBinding.inputLogo.setText(ch.logoUrl.orEmpty())
        dlgBinding.inputTvgId.setText(ch.tvgId.orEmpty())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_edit)
            .setView(dlgBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val idx = allResults.indexOfFirst { it === h }
                if (idx < 0) return@setPositiveButton
                fun textOrNull(s: CharSequence?): String? = s?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val newLicenseType = textOrNull(dlgBinding.inputLicenseType.text)
                val newLicenseKey = textOrNull(dlgBinding.inputLicenseKey.text)
                val newDrm = if (newLicenseType?.equals("clearkey", ignoreCase = true) == true) {
                    newLicenseKey
                } else {
                    ch.drmKey
                }
                val updated = ch.copy(
                    name = textOrNull(dlgBinding.inputName.text) ?: ch.name,
                    streamUrl = textOrNull(dlgBinding.inputStreamUrl.text) ?: ch.streamUrl,
                    group = textOrNull(dlgBinding.inputGroup.text),
                    logoUrl = textOrNull(dlgBinding.inputLogo.text),
                    licenseType = newLicenseType,
                    licenseKey = newLicenseKey,
                    drmKey = newDrm,
                    userAgent = textOrNull(dlgBinding.inputUserAgent.text),
                    referer = textOrNull(dlgBinding.inputReferer.text),
                    tvgId = textOrNull(dlgBinding.inputTvgId.text),
                    rawSource = null,
                )
                allResults[idx] = h.copy(channel = updated)
                refresh()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showSourceDialog(h: ChannelHealth) {
        val source = h.channel.rawSource?.takeIf { it.isNotBlank() }
            ?: buildSyntheticSource(h.channel)
        val dlgBinding = DialogViewSourceBinding.inflate(LayoutInflater.from(this))
        dlgBinding.txtSource.text = source
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_view_source)
            .setView(dlgBinding.root)
            .setPositiveButton(R.string.btn_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("channel source", source))
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun buildSyntheticSource(c: Channel): String {
        val sb = StringBuilder()
        val attrs = buildString {
            c.tvgId?.let { append(" tvg-id=\"").append(it).append('"') }
            c.tvgName?.let { append(" tvg-name=\"").append(it).append('"') }
            c.logoUrl?.let { append(" tvg-logo=\"").append(it).append('"') }
            c.group?.let { append(" group-title=\"").append(it).append('"') }
        }
        sb.append("#EXTINF:-1").append(attrs).append(',').append(c.name).append('\n')
        c.licenseType?.let { sb.append("#KODIPROP:inputstream.adaptive.license_type=").append(it).append('\n') }
        c.licenseKey?.let { sb.append("#KODIPROP:inputstream.adaptive.license_key=").append(it).append('\n') }
        c.userAgent?.let { sb.append("#KODIPROP:http-user-agent=").append(it).append('\n') }
        c.referer?.let { sb.append("#KODIPROP:http-referrer=").append(it).append('\n') }
        sb.append(c.streamUrl)
        return sb.toString()
    }

    private fun downloadFiltered(working: Boolean) {
        val list = allResults.filter { it.isWorking == working }.map { it.channel }
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.err_nothing_to_download, Toast.LENGTH_LONG).show()
            return
        }
        val text = M3uExporter.export(list)
        val baseName = if (working) "Working" else "Error"
        val fileName = "$baseName.txt"
        lifecycleScope.launch {
            val path = try {
                withContext(Dispatchers.IO) { writeToDownloads(fileName, text) }
            } catch (e: Throwable) {
                Toast.makeText(this@FilterResultActivity, getString(R.string.err_save, e.message ?: ""), Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(this@FilterResultActivity, getString(R.string.saved_to, path), Toast.LENGTH_LONG).show()
        }
    }

    private fun writeToDownloads(name: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/suzuFiltering")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot open output stream")
            return "Downloads/suzuFiltering/$name"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "suzuFiltering")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            return file.absolutePath
        }
    }
}
