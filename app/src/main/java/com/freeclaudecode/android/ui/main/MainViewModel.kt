package com.freeclaudecode.android.ui.main

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.freeclaudecode.android.FreeClaudeCodeApplication
import com.freeclaudecode.android.model.ServerState
import com.freeclaudecode.android.model.ServerStatus
import com.freeclaudecode.android.settings.SettingsManager
import com.freeclaudecode.android.termux.TermuxManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val settingsManager = SettingsManager(application)
    private val termuxManager = TermuxManager(application)
    private val gson = Gson()

    // UI State
    val serverState = MutableLiveData<ServerState>()
    val logs = MutableLiveData<String>()
    val isTermuxInstalled = MutableLiveData<Boolean>()
    val isTermuxApiInstalled = MutableLiveData<Boolean>()
    val isTermuxServicesInstalled = MutableLiveData<Boolean>()
    val isFccInstalled = MutableLiveData<Boolean>()
    val isServiceInstalled = MutableLiveData<Boolean>()
    val serviceStatus = MutableLiveData<String>()

    init {
        serverState.value = ServerState.Stopped()
        logs.value = "Free Claude Code Android\nChecking environment...\n"
        checkEnvironment()
    }

    private fun checkEnvironment() {
        viewModelScope.launch {
            val termux = termuxManager.isTermuxInstalled()
            isTermuxInstalled.postValue(termux)

            val api = termuxManager.isTermuxApiInstalled()
            isTermuxApiInstalled.postValue(api)

            if (!termux) {
                appendLog("❌ Termux not installed. Install from F-Droid.")
                return@launch
            }
            appendLog("✅ Termux installed")

            if (!api) {
                appendLog("⚠️ Termux:API not installed. Install from F-Droid for command execution.")
            } else {
                appendLog("✅ Termux:API installed")
            }

            checkTermuxServices()
            checkFccInstalled()
        }
    }

    private fun checkTermuxServices() {
        termuxManager.isTermuxServicesInstalled { installed ->
            isTermuxServicesInstalled.postValue(installed)
            if (installed) {
                appendLog("✅ termux-services installed")
                checkServiceInstalled()
            } else {
                appendLog("⚠️ termux-services not installed. Tap 'Setup Service' to install.")
            }
        }
    }

    private fun checkServiceInstalled() {
        termuxManager.checkServiceInstalled { installed ->
            isServiceInstalled.postValue(installed)
            if (installed) {
                appendLog("✅ FCC service configured")
                refreshServiceStatus()
            } else {
                appendLog("⚠️ FCC service not configured. Tap 'Setup Service'.")
            }
        }
    }

    fun checkFccInstalled() {
        viewModelScope.launch {
            termuxManager.checkFccInstalled { installed ->
                isFccInstalled.postValue(installed)
                appendLog(if (installed) "✅ FCC binary found" else "❌ FCC not installed. Tap 'Install FCC'.")
            }
        }
    }

    fun installFcc() {
        appendLog("📦 Installing FCC via uv tool... (may take 2-5 min)")
        viewModelScope.launch {
            termuxManager.installFcc { result ->
                appendLog(result)
                checkFccInstalled()
            }
        }
    }

    /** Install termux-services and enable supervision tree */
    fun installTermuxServices() {
        appendLog("🔧 Installing termux-services...")
        viewModelScope.launch {
            termuxManager.installTermuxServices { result ->
                appendLog(result)
                checkTermuxServices()
            }
        }
    }

    /** Create FCC runit service definition */
    fun setupFccService() {
        val port = settingsManager.port
        val host = settingsManager.host
        appendLog("📝 Creating FCC service (port=$port, host=$host)...")
        viewModelScope.launch {
            termuxManager.setupFccService(port, host) { result ->
                appendLog(result)
                checkServiceInstalled()
            }
        }
    }

    /** Start FCC server via runit service */
    fun startServer() {
        appendLog("▶️ Starting FCC server via service...")
        serverState.postValue(ServerState.Starting("Starting service..."))
        viewModelScope.launch {
            termuxManager.startService { result ->
                appendLog("Service start: $result")
                refreshServiceStatus()
                // Check HTTP status after service starts
                Thread.sleep(3000)
                checkHttpStatus()
            }
        }
    }

    /** Stop FCC server via runit service */
    fun stopServer() {
        appendLog("⏹️ Stopping FCC server via service...")
        viewModelScope.launch {
            termuxManager.stopService { result ->
                appendLog("Service stop: $result")
                serverState.postValue(ServerState.Stopped())
                refreshServiceStatus()
            }
        }
    }

    /** Restart FCC server via runit service */
    fun restartServer() {
        appendLog("🔄 Restarting FCC server...")
        serverState.postValue(ServerState.Starting("Restarting service..."))
        viewModelScope.launch {
            termuxManager.restartService { result ->
                appendLog("Service restart: $result")
                refreshServiceStatus()
                Thread.sleep(3000)
                checkHttpStatus()
            }
        }
    }

    /** Refresh runit service status */
    fun refreshServiceStatus() {
        viewModelScope.launch {
            termuxManager.getServiceStatus { output ->
                serviceStatus.postValue(output.trim())
                appendLog("Service status: ${output.trim()}")
            }
        }
    }

    /** Get service logs */
    fun fetchServiceLogs() {
        viewModelScope.launch {
            termuxManager.getServiceLog { output ->
                appendLog("--- SERVICE LOGS ---")
                appendLog(output)
            }
        }
    }

    private fun checkHttpStatus() {
        viewModelScope.launch {
            termuxManager.getServerStatus(settingsManager.port) { result ->
                try {
                    if (result.isNotBlank() && result.startsWith("{")) {
                        val status = gson.fromJson(result, ServerStatus::class.java)
                        if (status.isRunning) {
                            serverState.postValue(ServerState.Running(
                                serverUrl = settingsManager.getServerUrl(),
                                adminUrl = settingsManager.getAdminUrl(),
                                instanceId = status.instanceId
                            ))
                            appendLog("✅ Server running! Instance: ${status.instanceId}")
                        } else {
                            serverState.postValue(ServerState.Error("Server not ready: ${status.status}"))
                            appendLog("⚠️ Server not ready: ${status.status}")
                        }
                    } else if (result.contains("HTTP")) {
                        serverState.postValue(ServerState.Error("Server HTTP $result"))
                        appendLog("⚠️ Server HTTP $result")
                    } else {
                        appendLog("⏳ Waiting for server... ($result)")
                        Thread.sleep(2000)
                        checkHttpStatus()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing server status", e)
                    serverState.postValue(ServerState.Error("Status parse error: ${e.message}"))
                    appendLog("❌ Status error: ${e.message}")
                }
            }
        }
    }

    fun openAdminUi() {
        val adminUrl = settingsManager.getAdminUrl()
        appendLog("🌐 Opening Admin UI: $adminUrl")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(adminUrl))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openTermux() {
        termuxManager.openTermuxApp()
    }

    fun openTermuxInstallPage() {
        termuxManager.openTermuxInstallPage()
    }

    private fun appendLog(message: String) {
        val current = logs.value ?: ""
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "$current[$timestamp] $message\n"
        logs.postValue(newLog)
        FreeClaudeCodeApplication.instance.logLive(message)
    }

    fun clearLogs() {
        logs.value = ""
    }

    // Settings
    fun getPort(): Int = settingsManager.port
    fun getHost(): String = settingsManager.host
    fun setPort(port: Int) { settingsManager.port = port }
    fun setHost(host: String) { settingsManager.host = host }
    fun isAutoStart(): Boolean = settingsManager.autoStart
    fun setAutoStart(enabled: Boolean) { settingsManager.autoStart = enabled }
    fun isShowNotifications(): Boolean = settingsManager.showNotifications
    fun setShowNotifications(enabled: Boolean) { settingsManager.showNotifications = enabled }
    fun getServerUrl(): String = settingsManager.getServerUrl()
    fun getAdminUrl(): String = settingsManager.getAdminUrl()
}