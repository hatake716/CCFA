package io.github.hatake716.claudecodeandroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Main launcher for CCFA's fully embedded Linux + PTY architecture. */
class MainActivity : Activity() {
    companion object {
        private const val BASE_DEV_SETUP =
            "apt-get -o Acquire::Retries=3 update && " +
                "DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 install -y ca-certificates curl git ripgrep locales"
    }

    private val page = Color.rgb(244, 241, 234)
    private val card = Color.rgb(251, 249, 245)
    private val text = Color.rgb(45, 42, 38)
    private val muted = Color.rgb(110, 103, 94)
    private val border = Color.rgb(221, 214, 203)
    private val soft = Color.rgb(236, 231, 222)
    private val accent = Color.rgb(201, 100, 66)
    private val accentDark = Color.rgb(171, 77, 48)
    private val terminal = Color.rgb(40, 38, 35)

    private lateinit var setupProgress: ProgressBar
    private lateinit var setupOperationText: TextView
    private lateinit var setupButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        setContentView(buildView())
        EmbeddedRuntimeManager.ensureHostRuntime(this)
    }

    private fun buildView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(36))
            setBackgroundColor(page)
        }

        content.addView(TextView(this).apply {
            text = "CCFA"
            textSize = 32f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "日本語入力対応Linuxコンテナ"
            textSize = 15f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(18))
        })

        content.addView(agentCard())
        content.addView(containerCard(), top(dp(14)))
        content.addView(storageCard(), top(dp(14)))
        content.addView(setupCard(), top(dp(14)))
        content.addView(legalCard(), top(dp(14)))

        return ScrollView(this).apply {
            setBackgroundColor(page)
            isFillViewport = true
            addView(content)
        }
    }

    private fun agentCard(): View {
        val section = section("エージェント環境", "日本語IMEコンポーザー + PCキー + アプリ内PTY")
        section.addView(primary("エージェントターミナルを開く") {
            launch(EmbeddedRuntimeManager.LaunchMode.SHELL)
        }, top(dp(14)))
        section.addView(help(
            "CCFA配布版は特定ベンダーのAI CLIを自動インストール・自動ログインしません。" +
                "利用したいCLIはLinuxシェル内で、各提供元の条件を確認したうえでユーザー自身が導入・認証してください。"
        ))
        section.addView(TextView(this).apply {
            text = "ESC   CTRL   ALT   TAB   ↑   HOME   END\nPGUP   ←   ↓   →   PGDN   BKSP   ENTER"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(244, 239, 230))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(terminal, terminal, 10)
        }, top(dp(10)))
        return section
    }

    private fun containerCard(): View {
        val section = section("Linux コンテナ", "アプリ専用領域にLinux環境を複数保持")
        section.addView(primary("Linux コンテナ管理") {
            startActivity(Intent(this, ContainerManagerActivity::class.java))
        }, top(dp(12)))
        section.addView(help(
            "Linux rootfsはアプリ内部のprivate storageに保存します。外部Termux・PRoot-Distroアプリ・root権限は不要です。"
        ))
        return section
    }

    private fun storageCard(): View {
        val section = section("スマートフォンストレージ", "AndroidとLinuxの共有フォルダをSAFで同期")
        section.addView(badge("SAFで選択  ↔  /workspace/phone/<名前>"), top(dp(12)))
        section.addView(badge("双方向ミラー同期（任意のタイミングで実行）"), top(dp(6)))
        section.addView(primary("共有フォルダを設定・同期") { openStorageSharing() }, top(dp(10)))
        section.addView(button("現在の共有先を確認") {
            val mappings = StorageShareManager.enabledMappings(this)
            val message = if (mappings.isEmpty()) {
                "有効な共有設定はありません。"
            } else {
                mappings.joinToString("\n") { mapping ->
                    val ok = mapping.treeUri?.let { SafSyncManager.hasAccess(this, it) } ?: false
                    "${if (ok) "✓" else "×"} ${mapping.label}: ${mapping.treeLabel} ↔ ${mapping.guestPath()}"
                }
            }
            AlertDialog.Builder(this)
                .setTitle("現在のストレージ共有")
                .setMessage(message)
                .setPositiveButton("閉じる", null)
                .show()
        }, top(dp(8)))
        section.addView(help(
            "Android側フォルダはSAF（フォルダ選択）で指定し、Linux側 /workspace/phone/ 以下と" +
                "双方向ミラー同期します。同期は設定画面の「今すぐ同期」で任意のタイミングに実行できます。"
        ))
        return section
    }

    private fun setupCard(): View {
        val section = section("初回セットアップ", "CCFAのLinuxコンテナ実行環境をこのAPK内に構築")
        setupButton = primary("初期Linux環境を作成") { createInitialRuntime() }
        section.addView(setupButton, top(dp(8)))

        setupProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = 100
            progress = 0
        }
        section.addView(setupProgress, top(dp(10)))

        setupOperationText = TextView(this).apply {
            visibility = View.GONE
            text = "未開始"
            textSize = 13f
            setTextColor(this@MainActivity.text)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(soft, border, 10)
        }
        section.addView(setupOperationText, top(dp(8)))
        section.addView(help(
            "Linux Baseイメージは提供元公式サーバーから端末へ直接取得します。" +
                "CCFAは第三者AI CLI、第三者アカウント認証、APIキーを配布・代理取得しません。"
        ))
        section.addView(help(
            "各種AIエージェントの実装は、それぞれの公式サイトを確認したうえで、インストール手順に従ってください。"
        ))
        return section
    }

    private fun legalCard(): View {
        val section = section("配布・ライセンス", "第三者ライセンス、対応ソース、商標・非提携情報")
        section.addView(button("ライセンス・法的情報を表示") {
            startActivity(Intent(this, LegalActivity::class.java))
        }, top(dp(12)))
        section.addView(help("配布APKにはGPL/LGPL対象コンポーネントの対応ソースとライセンス本文を同梱します。"))
        return section
    }

    private fun createInitialRuntime() {
        val existing = EmbeddedRuntimeManager.listContainers(this)
        if (existing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Linux環境は作成済みです")
                .setMessage("現在 ${existing.size} 個のコンテナがあります。管理画面を開きますか？")
                .setPositiveButton("開く") { _, _ ->
                    startActivity(Intent(this, ContainerManagerActivity::class.java))
                }
                .setNegativeButton("閉じる", null)
                .show()
            return
        }

        setupButton.isEnabled = false
        setupProgress.visibility = View.VISIBLE
        setupOperationText.visibility = View.VISIBLE
        setupProgress.isIndeterminate = true
        setupOperationText.text = "Linux環境のセットアップを開始しています…"

        EmbeddedRuntimeManager.installUbuntuContainer(
            this,
            EmbeddedRuntimeManager.DEFAULT_CONTAINER,
            onProgress = { updateInstallProgress(it) },
            onComplete = { result ->
                setupButton.isEnabled = true
                result.onSuccess {
                    setupProgress.isIndeterminate = false
                    setupProgress.progress = 100
                    setupOperationText.text = "Linux rootfsとPRootセルフテストが完了しました。基本CLIをセットアップします。"
                    startActivity(
                        EmbeddedTerminalActivity.intent(
                            this,
                            EmbeddedRuntimeManager.DEFAULT_CONTAINER,
                            EmbeddedRuntimeManager.LaunchMode.COMMAND,
                            BASE_DEV_SETUP
                        )
                    )
                }.onFailure {
                    setupProgress.visibility = View.GONE
                    setupOperationText.visibility = View.VISIBLE
                    setupOperationText.text = "失敗: ${it.message ?: it.javaClass.simpleName}"
                    showError(it.message ?: "Linux環境の作成に失敗しました。")
                }
            }
        )
    }

    private fun updateInstallProgress(value: EmbeddedRuntimeManager.InstallProgress) {
        setupOperationText.visibility = View.VISIBLE
        setupOperationText.text = "${value.phase}: ${value.message}"
        setupProgress.visibility = View.VISIBLE
        if (value.percent == null) {
            setupProgress.isIndeterminate = true
        } else {
            setupProgress.isIndeterminate = false
            setupProgress.progress = value.percent.coerceIn(0, 100)
        }
    }

    private fun launch(mode: EmbeddedRuntimeManager.LaunchMode) {
        val active = EmbeddedRuntimeManager.activeContainer(this)
        if (active == null) {
            AlertDialog.Builder(this)
                .setTitle("Linux環境がありません")
                .setMessage("先に「初期Linux環境を作成」を実行してください。")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        startActivity(EmbeddedTerminalActivity.intent(this, active, mode))
    }

    // Play 版は全ファイルアクセスを使わない。共有フォルダの選択・同期は
    // scoped storage 準拠の SAF で行うため、その設定画面へ誘導する。
    private fun openStorageSharing() {
        startActivity(Intent(this, StorageSettingsActivity::class.java))
    }

    private fun showError(message: String) = AlertDialog.Builder(this)
        .setTitle("エラー")
        .setMessage(message)
        .setPositiveButton("閉じる", null)
        .show()

    private fun section(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(card, border, 18)
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 20f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(this@MainActivity).apply {
            text = subtitle
            textSize = 13.5f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun help(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(muted)
        setPadding(dp(2), dp(10), dp(2), 0)
    }

    private fun badge(value: String) = TextView(this).apply {
        text = value
        textSize = 12.5f
        setTextColor(this@MainActivity.text)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = rounded(soft, border, 10)
    }

    private fun primary(value: String, click: () -> Unit) = styled(value, accent, Color.WHITE, click)
    private fun button(value: String, click: () -> Unit) = styled(value, soft, text, click)

    private fun styled(value: String, bg: Int, fg: Int, click: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(fg)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(bg, if (bg == accent) accentDark else border, 12)
        setOnClickListener { click() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun full() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun top(v: Int) = full().apply { topMargin = v }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
