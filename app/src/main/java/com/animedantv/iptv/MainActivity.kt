package com.animedantv.iptv

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.animedantv.iptv.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.app_name)

        binding.btnImport.setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            binding.txtAbout.visibility =
                if (binding.txtAbout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme -> {
                showThemeChooser()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeChooser() {
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
        )
        val modes = intArrayOf(
            ThemePref.MODE_SYSTEM,
            ThemePref.MODE_LIGHT,
            ThemePref.MODE_DARK,
        )
        val current = ThemePref.getMode(this)
        val checked = modes.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                ThemePref.setMode(this, modes[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
