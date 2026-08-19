package io.github.hatake716.claudecodeandroid

import android.app.Activity
import android.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
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
            text = "Termux + Ubuntu 24.04 上で Claude Code を動かす非公式ランチャー"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(20))
        })

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0x11000000)
        }
        content.addView(statusText, matchWidth())

        content.addView(sectionTitle("初回セットアップ"))
        content.addView(actionButton("1. Termux を開く") { openTermux() }, buttonParams())
        content.addView(actionButton("2. 設定コマンドをコピー") { copyExternalAppsCommand() }, buttonParams())
        content.addView(helpText("コピーしたコマンドを Termux に貼り付けて実行し、allow-external-apps=true を有効にします。"))
        content.addView(actionButton("3. RUN_COMMAND 権限を許可") { requestRunCommandPermission() }, buttonParams())
        content.addView(actionButton("4. Linux + Claude Code をセットアップ") {
            runAssetScript("bootstrap-termux.sh")
        }, buttonParams())

        content.addView(sectionTitle("Claude Code"))
        content.addView(actionButton("Claude Code を起動") {
            runAssetScript("launch-claude.sh")
        }, buttonParams())
        content.addView(helpText("Termux の ~/claude-projects が Ubuntu の /workspace として開きます。"))

        content.addView(sectionTitle("トラブルシューティング"))
        content.addView(actionButton("Android のアプリ設定を開く") { openOwnAppSettings() }, buttonParams())
        content.addView(helpText("RUN_COMMAND 権限が表示されない場合は、Termux がインストール済みか確認してから再度開いてください。"))

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshStatus() {
        val termuxInstalled = TermuxRunner.isInstalled(this)
        val permissionGranted = termuxInstalled &&
            checkSelfPermission(TermuxRunner.RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED

        statusText.text = buildString {
            appendLine("Termux: ${if (termuxInstalled) "✓ インストール済み" else "✗ 未検出"}")
            appendLine("RUN_COMMAND: ${if (permissionGranted) "✓ 許可済み" else "✗ 未許可"}")
            append("allow-external-apps: Termux 側で一度設定が必要")
        }
    }

    private fun openTermux() {
        val launchIntent = packageManager.getLaunchIntentForPackage(TermuxRunner.TERMUX_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Termux が見つかりません")
                .setMessage("Termux をインストールして一度起動してください。")
                .setPositiveButton("Termux Releases を開く") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")))
                }
                .setNegativeButton("閉じる", null)
                .show()
        }
    }

    private fun copyExternalAppsCommand() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux allow-external-apps setup", ENABLE_EXTERNAL_APPS_COMMAND))
        Toast.makeText(this, "Termux 設定コマンドをコピーしました", Toast.LENGTH_SHORT).show()
    }

    private fun requestRunCommandPermission() {
        if (!TermuxRunner.isInstalled(this)) {
            Toast.makeText(this, "先に Termux をインストールしてください", Toast.LENGTH_LONG).show()
            return
        }

        if (checkSelfPermission(TermuxRunner.RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "RUN_COMMAND 権限は許可済みです", Toast.LENGTH_SHORT).show()
            return
        }

        requestPermissions(
            arrayOf(TermuxRunner.RUN_COMMAND_PERMISSION),
            REQUEST_RUN_COMMAND_PERMISSION
        )
    }

    private fun runAssetScript(assetName: String) {
        if (!TermuxRunner.isInstalled(this)) {
            Toast.makeText(this, "Termux が見つかりません", Toast.LENGTH_LONG).show()
            return
        }

        if (checkSelfPermission(TermuxRunner.RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "RUN_COMMAND 権限を先に許可してください", Toast.LENGTH_LONG).show()
            return
        }

        val script = runCatching {
            assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrElse {
            showError("スクリプトを読み込めませんでした", it)
            return
        }

        TermuxRunner.runForegroundScript(this, script)
            .onSuccess {
                Toast.makeText(this, "Termux で処理を開始しました", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                showError(
                    "Termux でコマンドを開始できませんでした。allow-external-apps=true と RUN_COMMAND 権限を確認してください。",
                    it
                )
            }
    }

    private fun openOwnAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun showError(message: String, throwable: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("エラー")
            .setMessage("$message\n\n${throwable.message ?: throwable.javaClass.simpleName}")
            .setPositiveButton("閉じる", null)
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
        if (requestCode == REQUEST_RUN_COMMAND_PERMISSION) {
            refreshStatus()
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) "RUN_COMMAND 権限を許可しました" else "RUN_COMMAND 権限が必要です",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
