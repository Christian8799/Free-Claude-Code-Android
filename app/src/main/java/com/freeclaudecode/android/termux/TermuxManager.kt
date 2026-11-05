package com.freeclaudecode.android.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.freeclaudecode.android.FreeClaudeCodeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * TermuxManager using termux-services (runit) for persistent FCC server management.
 *
 * Architecture:
 * 1. App writes a runit service definition to ~/.termux/services/fcc-server/
 * 2. Service runs via termux-services daemon (persistent, survives reboots)
 * 3. App controls service via `sv` commands (start/stop/status/restart) sent via Termux:API
 * 4. Health monitoring via direct HTTP to server's admin API
 *
 * Requirements on device:
 * - Termux (com.termux)
 * - Termux:API (com.termux.api)
 * - termux-services package (install via: pkg install termux-services)
 * - Run `termux-services enable` once to start supervision tree
 */
class TermuxManager(private val context: Context = FreeClaudeCodeApplication.instance) {
    companion object {
        private const val TAG = "TermuxManager"
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        const val ACTION_RUN_COMMAND = "com.termux.api.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.api.RUN_COMMAND_PATH"
        const val EXTRA_COMMAND_ARGS = "com.termux.api.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_COMMAND_WORKDIR = "com.termux.api.RUN_COMMAND_WORKDIR"

        // Service paths in Termux
        const val SERVICE_DIR = "/data/data/com.termux/files/home/.termux/services/fcc-server"
        const val SERVICE_RUN = "$SERVICE_DIR/run"
        const val SERVICE_LOG_DIR = "$SERVICE_DIR/log"
        const val SERVICE_LOG_RUN = "$SERVICE_LOG_DIR/run"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxApiInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxServicesInstalled(callback: (Boolean) -> Unit) {
        executeCommandInTermux("command -v sv >/dev/null && echo 'yes' || echo 'no'") { result ->
            callback(result.trim() == "yes")
        }
    }

    fun openTermuxApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    fun openTermuxInstallPage() {
        val url = "https://f-droid.org/packages/com.termux/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Send command via Termux:API broadcast (fire-and-forget) */
    fun executeCommandInTermux(command: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                val intent = Intent(ACTION_RUN_COMMAND)
                intent.putExtra(EXTRA_COMMAND_PATH, "sh")
                intent.putExtra(EXTRA_COMMAND_ARGS, arrayOf("-c", command))
                intent.putExtra(EXTRA_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
                intent.setPackage(TERMUX_API_PACKAGE)

                if (Build.VERSION.SDK_INT >= 33) {
                    intent.addFlags(0x4000000) // FLAG_RECEIVER_EXPORTED
                }

                FreeClaudeCodeApplication.instance.logLive("Termux cmd: $command")
                context.sendBroadcast(intent)
                callback("Sent: $command")
                FreeClaudeCodeApplication.instance.logLive("Sent: $command")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command to Termux: $command", e)
                FreeClaudeCodeApplication.instance.logLive("Termux cmd error: ${e.message}")
                callback("Error: ${e.message}")
            }
        }
    }

    /** Send command and capture output by writing to a temp file in Termux */
    fun executeCommandWithOutput(command: String, callback: (String) -> Unit) {
        val outputFile = "/data/data/com.termux/files/home/.fcc_cmd_output"
        val wrappedCmd = "($command) > $outputFile 2>&1; echo \"EXIT_CODE:\$?\" >> $outputFile"
        executeCommandInTermux(wrappedCmd) { _ ->
            // Read output file via a second command
            Thread.sleep(500)
            executeCommandInTermux("cat $outputFile 2>/dev/null || echo 'NO_OUTPUT'") { output ->
                callback(output)
            }
        }
    }

    // ===== SERVICE SETUP (runit) =====

    /** Install termux-services and create FCC service definition */
    fun setupFccService(port: Int = 8080, host: String = "127.0.0.1", callback: (String) -> Unit) {
        val envVars = "PORT=$port HOST=$host"
        val runScript = "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "# FCC Server runit service\n" +
            "# Generated by FreeClaudeCodeAndroid\n\n" +
            "export PATH=\"\$HOME/.local/bin:\$PATH\"\n" +
            "export PORT=$port\n" +
            "export HOST=$host\n\n" +
            "# Find FCC binary\n" +
            "if command -v free-claude-code >/dev/null; then\n" +
            "    exec free-claude-code\n" +
            "elif command -v fcc-server >/dev/null; then\n" +
            "    exec fcc-server\n" +
            "elif command -v fcc >/dev/null; then\n" +
            "    exec fcc\n" +
            "else\n" +
            "    echo \"ERROR: No FCC binary found in ~/.local/bin/\"\n" +
            "    ls ~/.local/bin/\n" +
            "    exit 1\n" +
            "fi\n"

        val logRunScript = "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "# FCC Server log service (svlogd)\n" +
            "exec svlogd -tt \$HOME/.termux/services/fcc-server/log/main\n"

        // Multi-step setup via sequential commands
        val setupCmd = """
            mkdir -p $SERVICE_DIR $SERVICE_LOG_DIR/main &&
            cat > $SERVICE_RUN << 'EOF'
$runScript
EOF
            chmod +x $SERVICE_RUN &&
            cat > $SERVICE_LOG_RUN << 'EOF'
$logRunScript
EOF
            chmod +x $SERVICE_LOG_RUN &&
            echo "Service scripts created"
        """.trimIndent()

        executeCommandInTermux(setupCmd) { result ->
            FreeClaudeCodeApplication.instance.logLive("Setup service result: $result")
            callback(result)
        }
    }

    /** Install termux-services package and enable it */
    fun installTermuxServices(callback: (String) -> Unit) {
        val cmd = "pkg update -y && pkg install -y termux-services && termux-services enable && echo 'termux-services installed and enabled'"
        executeCommandInTermux(cmd, callback)
    }

    /** Check if FCC service is installed */
    fun checkServiceInstalled(callback: (Boolean) -> Unit) {
        executeCommandWithOutput("test -f $SERVICE_RUN && echo 'yes' || echo 'no'") { result ->
            callback(result.trim().contains("yes"))
        }
    }

    // ===== SERVICE CONTROL (via sv command) =====

    fun startService(callback: (String) -> Unit) {
        // sv up starts the service, supervised by runit
        executeCommandWithOutput("sv up $SERVICE_DIR 2>&1; sleep 2; sv status $SERVICE_DIR 2>&1") { result ->
            FreeClaudeCodeApplication.instance.logLive("Service start: $result")
            callback(result)
        }
    }

    fun stopService(callback: (String) -> Unit) {
        executeCommandWithOutput("sv down $SERVICE_DIR 2>&1; sv status $SERVICE_DIR 2>&1") { result ->
            FreeClaudeCodeApplication.instance.logLive("Service stop: $result")
            callback(result)
        }
    }

    fun restartService(callback: (String) -> Unit) {
        executeCommandWithOutput("sv restart $SERVICE_DIR 2>&1; sleep 2; sv status $SERVICE_DIR 2>&1") { result ->
            FreeClaudeCodeApplication.instance.logLive("Service restart: $result")
            callback(result)
        }
    }

    fun getServiceStatus(callback: (String) -> Unit) {
        executeCommandWithOutput("sv status $SERVICE_DIR 2>&1") { result ->
            callback(result)
        }
    }

    fun getServiceLog(callback: (String) -> Unit) {
        executeCommandWithOutput("tail -50 \${HOME}/.termux/services/fcc-server/log/main/current 2>/dev/null || echo 'No logs yet'") { result ->
            callback(result)
        }
    }

    // ===== FCC INSTALLATION =====

    fun installFcc(callback: (String) -> Unit) {
        val installCmd = "pkg update -y && pkg install -y python uv python-pip termux-services && uv tool install free-claude-code && echo '---BINS---' && ls -la ~/.local/bin/ && echo '---ENTRY---' && uv tool list"
        executeCommandInTermux(installCmd, callback)
    }

    fun checkFccInstalled(callback: (Boolean) -> Unit) {
        executeCommandWithOutput("ls ~/.local/bin/ 2>/dev/null | grep -E 'free-claude-code|fcc-server|fcc' || echo 'NOT_FOUND'") { result ->
            callback(!result.trim().contains("NOT_FOUND"))
        }
    }

    // ===== HTTP STATUS CHECK (direct from Android) =====

    fun getServerStatus(port: Int = 8080, callback: (String) -> Unit) {
        scope.launch {
            try {
                val url = URL("http://127.0.0.1:$port/admin/api/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    callback(response)
                } else {
                    callback("HTTP $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                FreeClaudeCodeApplication.instance.logLive("HTTP status error: ${e.message}")
                callback("Error: ${e.message}")
            }
        }
    }

    fun getAdminUrl(port: Int = 8080): String = "http://127.0.0.1:$port/admin"
    fun getServerUrl(port: Int = 8080): String = "http://127.0.0.1:$port"

    private fun copyToClipboard(text: String) {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FCC Command", text)
            clipboardManager.setPrimaryClip(clip)
            FreeClaudeCodeApplication.instance.logLive("Copied to clipboard")
        } catch (e: Exception) {
            FreeClaudeCodeApplication.instance.logLive("Clipboard error: ${e.message}")
        }
    }
}