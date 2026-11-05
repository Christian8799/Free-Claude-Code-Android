package com.freeclaudecode.android.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.freeclaudecode.android.FreeClaudeCodeApplication
import com.freeclaudecode.android.R
import com.freeclaudecode.android.databinding.ActivityMainBinding
import com.freeclaudecode.android.model.ServerState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupToolbar()
        setupObservers()
        setupClickListeners()

        // Check for auto-start
        if (viewModel.isAutoStart()) {
            lifecycleScope.launch {
                viewModel.startServer()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_providers -> {
                viewModel.openAdminUi()
                true
            }
            R.id.action_logs -> {
                showLogsDialog()
                true
            }
            R.id.action_crash_logs -> {
                showCrashLogsDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupObservers() {
        viewModel.serverState.observe(this) { state ->
            updateUiForState(state)
        }

        viewModel.logs.observe(this) { logText ->
            binding.tvLogs.text = logText
            binding.tvLogs.post { binding.tvLogs.scrollTo(0, binding.tvLogs.bottom) }
        }

        viewModel.isTermuxInstalled.observe(this) { installed ->
            if (!installed) {
                binding.btnCheckTermux.text = "Install Termux"
                binding.btnInstallFCC.isEnabled = false
                binding.btnStartServer.isEnabled = false
            } else {
                binding.btnCheckTermux.text = "Open Termux"
                binding.btnInstallFCC.isEnabled = true
            }
        }

        viewModel.isTermuxApiInstalled.observe(this) { installed ->
            // UI updates handled via logs
        }

        viewModel.isTermuxServicesInstalled.observe(this) { installed ->
            binding.btnSetupService.isEnabled = installed
        }

        viewModel.isFccInstalled.observe(this) { installed ->
            binding.btnStartServer.isEnabled = installed
            binding.btnSetupService.isEnabled = installed
            if (!installed) {
                binding.btnInstallFCC.text = "Install FCC"
            } else {
                binding.btnInstallFCC.text = "Reinstall FCC"
            }
        }

        viewModel.isServiceInstalled.observe(this) { installed ->
            binding.btnStartServer.isEnabled = installed
            binding.btnStopServer.isEnabled = installed
            binding.btnOpenAdmin.isEnabled = installed
            if (!installed) {
                binding.btnSetupService.text = "Setup FCC Service"
            } else {
                binding.btnSetupService.text = "Recreate Service"
            }
        }

        viewModel.serviceStatus.observe(this) { status ->
            // Could update a status label if needed
        }
    }

    private fun setupClickListeners() {
        binding.btnStartServer.setOnClickListener {
            viewModel.startServer()
        }

        binding.btnStopServer.setOnClickListener {
            viewModel.stopServer()
        }

        binding.btnOpenAdmin.setOnClickListener {
            viewModel.openAdminUi()
        }

        binding.btnCheckTermux.setOnClickListener {
            if (viewModel.isTermuxInstalled.value == true) {
                viewModel.openTermux()
            } else {
                viewModel.openTermuxInstallPage()
            }
        }

        binding.btnInstallFCC.setOnClickListener {
            viewModel.installFcc()
        }

        binding.btnInstallTermuxServices.setOnClickListener {
            viewModel.installTermuxServices()
        }

        binding.btnSetupService.setOnClickListener {
            viewModel.setupFccService()
        }

        binding.btnClearLogs.setOnClickListener {
            viewModel.clearLogs()
        }

        binding.fabSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun updateUiForState(state: ServerState) {
        when (state) {
            is ServerState.Stopped -> {
                binding.tvStatus.text = getString(R.string.server_status_stopped)
                binding.tvStatus.setTextColor(getColor(R.color.error))
                binding.tvStatusLabel.text = getString(R.string.server_status_stopped)
                binding.btnStartServer.visibility = android.view.View.VISIBLE
                binding.btnStopServer.visibility = android.view.View.GONE
                binding.tvServerUrl.text = viewModel.getServerUrl()
                binding.tvAdminUrl.text = viewModel.getAdminUrl()
            }
            is ServerState.Starting -> {
                binding.tvStatus.text = state.message
                binding.tvStatus.setTextColor(getColor(R.color.warning))
                binding.tvStatusLabel.text = getString(R.string.server_status_starting)
                binding.btnStartServer.visibility = android.view.View.GONE
                binding.btnStopServer.visibility = android.view.View.VISIBLE
            }
            is ServerState.Running -> {
                binding.tvStatus.text = getString(R.string.server_status_running)
                binding.tvStatus.setTextColor(getColor(R.color.success))
                binding.tvStatusLabel.text = getString(R.string.server_status_running)
                binding.btnStartServer.visibility = android.view.View.GONE
                binding.btnStopServer.visibility = android.view.View.VISIBLE
                binding.tvServerUrl.text = state.serverUrl
                binding.tvAdminUrl.text = state.adminUrl
            }
            is ServerState.Error -> {
                binding.tvStatus.text = state.message
                binding.tvStatus.setTextColor(getColor(R.color.error))
                binding.tvStatusLabel.text = getString(R.string.server_status_error)
                binding.btnStartServer.visibility = android.view.View.VISIBLE
                binding.btnStopServer.visibility = android.view.View.GONE
            }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etPort = dialogView.findViewById<TextView>(R.id.etPort)
        val etHost = dialogView.findViewById<TextView>(R.id.etHost)
        val cbAutoStart = dialogView.findViewById<android.widget.CheckBox>(R.id.cbAutoStart)
        val cbNotifications = dialogView.findViewById<android.widget.CheckBox>(R.id.cbNotifications)

        etPort.setText(viewModel.getPort().toString())
        etHost.setText(viewModel.getHost())
        cbAutoStart.isChecked = viewModel.isAutoStart()
        cbNotifications.isChecked = viewModel.isShowNotifications()

        MaterialAlertDialogBuilder(this, R.style.Theme_FreeClaudeCode_MaterialAlertDialog)
            .setTitle(R.string.title_settings)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val port = etPort.text.toString().toIntOrNull() ?: viewModel.getPort()
                val host = etHost.text.toString().ifBlank { viewModel.getHost() }
                viewModel.setPort(port)
                viewModel.setHost(host)
                viewModel.setAutoStart(cbAutoStart.isChecked)
                viewModel.setShowNotifications(cbNotifications.isChecked)
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logs, null)
        val tvLogs = dialogView.findViewById<TextView>(R.id.tvLogsFull)
        tvLogs.text = viewModel.logs.value ?: ""

        MaterialAlertDialogBuilder(this, R.style.Theme_FreeClaudeCode_MaterialAlertDialog)
            .setTitle(R.string.title_logs)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> viewModel.clearLogs() }
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this, R.style.Theme_FreeClaudeCode_MaterialAlertDialog)
            .setTitle("Free Claude Code Android")
            .setMessage(
                "A wrapper app to run Free Claude Code (FCC) on Android via Termux.\n\n" +
                "FCC: https://github.com/alishahryar1/free-claude-code\n" +
                "Termux: https://termux.dev\n\n" +
                "Version 1.0.0"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCrashLogsDialog() {
        val logFile = FreeClaudeCodeApplication.logFile
        var logContent = "No logs found."

        if (logFile.exists()) {
            try {
                val fis = FileInputStream(logFile)
                val bytes = fis.readAllBytes()
                fis.close()
                logContent = String(bytes)
            } catch (e: Exception) {
                logContent = "Error reading log: ${e.message}"
            }
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_logs, null)
        val tvLogs = dialogView.findViewById<TextView>(R.id.tvLogsFull)
        tvLogs.text = logContent

        MaterialAlertDialogBuilder(this, R.style.Theme_FreeClaudeCode_MaterialAlertDialog)
            .setTitle("App Logs (Live)")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear Logs") { _, _ ->
                if (logFile.exists()) {
                    logFile.delete()
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}