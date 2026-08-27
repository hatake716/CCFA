package io.github.hatake716.claudecodeandroid

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_RUN_COMMAND_PERMISSION = 716

        private const val TERMUX_RELEASES_URL =
            "https://github.com/termux/termux-app/releases"

        private val ENABLE_EXTERNAL_APPS_COMMAND = """
            mkdir -p ~/.termux
            touch ~/.termux/termux.properties
            if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then
              sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties
            else
              printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
            fi
            termux-reload-settings
        """.trimIndent()
    }

    private lateinit var statusText: TextView

    /**
     * Set once the user has been shown the system permission dialog. Lets us tell
     * "not asked yet" apart from "permanently denied", which otherwise look
     * identical through shouldShowRequestPermissionRationale().
     */
    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequested =
            savedInstanceState?.getBoolean(::permissionRequested.name, false) ?: false
        setContentView(buildContentView())
        refreshStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(::permissionRequested.name, permissionRequested)
    }

    override fun onResume() {
        super.onResume()
        // statusText is always initialised by onCreate() before onResume() runs,
        // so refreshing unconditionally is safe here.
        refreshStatus()
    }

    private fun buildContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.app_tagline)
            textSize = 16f
            setPadding(0, dp(8), 0, dp(20))
        })

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(getColor(R.color.status_panel_background))
        }
        content.addView(statusText, matchWidth())

        content.addView(sectionTitle(getString(R.string.section_setup)))
        content.addView(
            actionButton(getString(R.string.action_open_termux)) { openTermux() },
            buttonParams()
        )
        content.addView(
            actionButton(getString(R.string.action_copy_command)) { copyExternalAppsCommand() },
            buttonParams()
        )
        content.addView(helpText(getString(R.string.help_copy_command)))
        content.addView(
            actionButton(getString(R.string.action_request_permission)) {
                requestRunCommandPermission()
            },
            buttonParams()
        )
        content.addView(
            actionButton(getString(R.string.action_bootstrap)) {
                runAssetScript("bootstrap-termux.sh", getString(R.string.action_bootstrap))
            },
            buttonParams()
        )

        content.addView(sectionTitle(getString(R.string.section_claude)))
        content.addView(
            actionButton(getString(R.string.action_launch)) {
                runAssetScript("launch-claude.sh", getString(R.string.action_launch))
            },
            buttonParams()
        )
        content.addView(helpText(getString(R.string.help_workspace)))

        content.addView(sectionTitle(getString(R.string.section_troubleshooting)))
        content.addView(
            actionButton(getString(R.string.action_open_app_settings)) { openOwnAppSettings() },
            buttonParams()
        )
        content.addView(helpText(getString(R.string.help_troubleshooting)))

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshStatus() {
        val termuxInstalled = TermuxRunner.isInstalled(this)
        val permissionGranted = termuxInstalled && hasRunCommandPermission()

        statusText.text = buildString {
            appendLine(
                getString(
                    R.string.status_termux,
                    getString(
                        if (termuxInstalled) R.string.status_installed
                        else R.string.status_not_found
                    )
                )
            )
            appendLine(
                getString(
                    R.string.status_run_command,
                    getString(
                        if (permissionGranted) R.string.status_granted
                        else R.string.status_denied
                    )
                )
            )
            append(getString(R.string.status_external_apps))
        }
    }

    private fun hasRunCommandPermission(): Boolean =
        checkSelfPermission(TermuxRunner.RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    private fun openTermux() {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(TermuxRunner.TERMUX_PACKAGE)
        if (launchIntent != null) {
            // getLaunchIntentForPackage() can still fail if Termux is disabled
            // or was uninstalled between the lookup and the call.
            if (!safeStartActivity(launchIntent, R.string.toast_termux_not_found)) {
                showTermuxMissingDialog()
            }
            return
        }
        showTermuxMissingDialog()
    }

    private fun showTermuxMissingDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_termux_missing_title)
            .setMessage(R.string.dialog_termux_missing_message)
            .setPositiveButton(R.string.dialog_open_termux_releases) { _, _ ->
                safeStartActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_RELEASES_URL)),
                    R.string.error_no_browser
                )
            }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun copyExternalAppsCommand() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Toast.makeText(this, R.string.error_settings_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.clip_label),
                ENABLE_EXTERNAL_APPS_COMMAND
            )
        )
        Toast.makeText(this, R.string.toast_command_copied, Toast.LENGTH_SHORT).show()
    }

    private fun requestRunCommandPermission() {
        if (!TermuxRunner.isInstalled(this)) {
            Toast.makeText(this, R.string.toast_install_termux_first, Toast.LENGTH_LONG).show()
            return
        }

        if (hasRunCommandPermission()) {
            Toast.makeText(this, R.string.toast_permission_already_granted, Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Once the user picks "don't ask again" the system dialog never appears,
        // so send them to app settings instead of silently doing nothing.
        val permanentlyDenied = permissionRequested &&
            !shouldShowRequestPermissionRationale(TermuxRunner.RUN_COMMAND_PERMISSION)
        if (permanentlyDenied) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setPositiveButton(R.string.dialog_open_settings) { _, _ -> openOwnAppSettings() }
                .setNegativeButton(R.string.dialog_close, null)
                .show()
            return
        }

        permissionRequested = true
        requestPermissions(
            arrayOf(TermuxRunner.RUN_COMMAND_PERMISSION),
            REQUEST_RUN_COMMAND_PERMISSION
        )
    }

    private fun runAssetScript(assetName: String, label: String) {
        if (!TermuxRunner.isInstalled(this)) {
            Toast.makeText(this, R.string.toast_termux_not_found, Toast.LENGTH_LONG).show()
            return
        }

        if (!hasRunCommandPermission()) {
            Toast.makeText(this, R.string.toast_permission_required_first, Toast.LENGTH_LONG)
                .show()
            return
        }

        val script = runCatching {
            assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrElse {
            showError(getString(R.string.error_script_read_failed), it)
            return
        }

        TermuxRunner.runForegroundScript(this, script, label)
            .onSuccess {
                Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                showError(getString(R.string.error_run_command_failed), it)
            }
    }

    private fun openOwnAppSettings() {
        safeStartActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ),
            R.string.error_settings_unavailable
        )
    }

    /**
     * startActivity() throws when nothing on the device can handle the intent
     * (common for ACTION_VIEW on devices with no browser). Report it instead of
     * crashing.
     */
    private fun safeStartActivity(intent: Intent, errorMessageRes: Int): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, errorMessageRes, Toast.LENGTH_LONG).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(this, errorMessageRes, Toast.LENGTH_LONG).show()
        false
    }

    private fun showError(message: String, throwable: Throwable) {
        val detail = throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable.javaClass.simpleName
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_error_title)
            .setMessage("$message\n\n$detail")
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(26), 0, dp(8))
    }

    private fun helpText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(dp(4), dp(6), dp(4), dp(8))
    }

    private fun actionButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun buttonParams() = matchWidth().apply { topMargin = dp(8) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RUN_COMMAND_PERMISSION) return

        refreshStatus()
        // An empty grantResults means the request was cancelled; treat it as denied
        // rather than indexing into the array and crashing.
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) R.string.toast_permission_granted
            else R.string.toast_permission_denied,
            Toast.LENGTH_SHORT
        ).show()
    }
}
