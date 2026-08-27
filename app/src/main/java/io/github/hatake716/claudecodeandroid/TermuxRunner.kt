package io.github.hatake716.claudecodeandroid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object TermuxRunner {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"

    // Termux's install prefix is fixed by the Termux app itself; these are not
    // this app's private paths, so Context.getFilesDir() does not apply.
    @Suppress("SdCardPath")
    private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"

    @Suppress("SdCardPath")
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    /**
     * Open a new terminal session and bring the Termux activity to the front.
     * Termux only accepts this as a string extra.
     */
    private const val SESSION_ACTION_NEW_SESSION_AND_OPEN_ACTIVITY = "0"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun runForegroundScript(context: Context, script: String, label: String): Result<Unit> = runCatching {
        require(script.isNotBlank()) { "Script is empty" }

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            // Pass the script via stdin-free `-c` so quoting inside the script is preserved.
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", script))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, false)
            putExtra(EXTRA_SESSION_ACTION, SESSION_ACTION_NEW_SESSION_AND_OPEN_ACTIVITY)
            putExtra(EXTRA_COMMAND_LABEL, label)
        }

        // RunCommandService calls startForeground() in onCreate(), so it must be
        // started with startForegroundService(). minSdk is 26, so this is always
        // the correct call. Using startService() threw IllegalStateException
        // whenever the caller was not already in a foreground state.
        val component = context.startForegroundService(intent)

        checkNotNull(component) {
            "Termux の RunCommandService が見つかりませんでした。Termux のバージョンを確認してください。"
        }
        Unit
    }.recoverCatching { error ->
        throw when (error) {
            is SecurityException -> IllegalStateException(
                "Termux がコマンドの実行を拒否しました。allow-external-apps=true が設定されているか確認してください。",
                error
            )

            is ActivityNotFoundException -> IllegalStateException(
                "Termux が見つかりませんでした。",
                error
            )

            else -> error
        }
    }
}
