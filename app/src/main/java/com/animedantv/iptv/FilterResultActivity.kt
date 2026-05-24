package com.animedantv.iptv

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.animedantv.iptv.databinding.ActivityFilterResultBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FilterResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterResultBinding
    private lateinit var adapter: ChannelHealthAdapter

    private var allResults: List<ChannelHealth> = emptyList()
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
        lifecycleScope.launch {
            val results = HealthChecker.checkAll(channels) { done, total, _ ->
                withContext(Dispatchers.Main) {
                    binding.progress.progress = done
                    binding.txtStatus.text = getString(R.string.checking_progress, done, total)
                }
            }
            allResults = results
            updateTabBadges()
            refresh()
            val working = results.count { it.isWorking }
            binding.txtStatus.text = getString(R.string.check_complete, working, results.size - working)
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
            getString(moveLabel),
        )
        AlertDialog.Builder(this)
            .setTitle(h.channel.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openInPlayer(h)
                    1 -> moveChannel(h)
                }
            }
            .show()
    }

    private fun moveChannel(h: ChannelHealth) {
        val newStatus = if (h.isWorking) HealthStatus.ERROR else HealthStatus.WORKING
        allResults = allResults.map { if (it === h) it.copy(status = newStatus, message = "manual") else it }
        updateTabBadges()
        refresh()
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
