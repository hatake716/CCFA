package io.github.hatake716.claudecodeandroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 共有フォルダ設定（Google Play / scoped storage 版）。
 *
 * sideload 版の「Android 側パスを自由入力して常時 bind」ではなく、
 * SAF (ACTION_OPEN_DOCUMENT_TREE) でフォルダを選び、/workspace/phone/<名前> と
 * 双方向ミラー同期する。同期は「今すぐ同期」で任意のタイミングに実行する。
 */
class StorageSettingsActivity : Activity() {
    private val page = Color.rgb(244, 241, 234)
    private val card = Color.rgb(251, 249, 245)
    private val text = Color.rgb(45, 42, 38)
    private val muted = Color.rgb(110, 103, 94)
    private val border = Color.rgb(221, 214, 203)
    private val soft = Color.rgb(236, 231, 222)
    private val accent = Color.rgb(201, 100, 66)
    private val danger = Color.rgb(157, 65, 54)

    private lateinit var rowsHost: LinearLayout
    private lateinit var syncProgress: ProgressBar
    private lateinit var syncStatus: TextView
    private val rows = mutableListOf<RowRefs>()

    // SAF フォルダ選択の結果を、どの行に反映するか覚えておく。
    private var pendingTreeRowIndex: Int = -1

    private data class RowRefs(
        val root: View,
        val enabled: CheckBox,
        val label: EditText,
        var treeUri: Uri?,
        var treeLabel: String,
        val treeButton: Button,
        val treeText: TextView,
        var direction: StorageShareManager.SyncDirection,
        val directionButton: Button,
        val guestText: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        setContentView(buildView().also { it.applyEdgeToEdgeInsets(includeIme = true) })
        render(StorageShareManager.load(this))
    }

    private fun buildView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(36))
            setBackgroundColor(page)
        }

        content.addView(Button(this).apply {
            text = "← 戻る"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        content.addView(TextView(this).apply {
            text = "ストレージ共有設定"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@StorageSettingsActivity.text)
            setPadding(0, dp(14), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Android側フォルダを選び、Linux側 /workspace/phone/ 以下と双方向ミラー同期します。" +
                "常時マウントではなく「今すぐ同期」で任意のタイミングに反映します。"
            textSize = 13.5f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(14))
        })

        rowsHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rowsHost)

        content.addView(primary("＋ 共有先を追加") {
            val current = collectRows(showErrors = false).getOrElse { StorageShareManager.load(this) }.toMutableList()
            current += StorageShareManager.newCustomMapping(current.size + 1)
            render(current)
        }, top(dp(12)))

        content.addView(primary("保存") { save() }, top(dp(8)))
        content.addView(primary("今すぐ同期") { syncNow() }, top(dp(8)))
        content.addView(button("初期値に戻す") { confirmReset() }, top(dp(8)))

        syncProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        content.addView(syncProgress, top(dp(10)))
        syncStatus = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(this@StorageSettingsActivity.text)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(soft, border, 10)
        }
        content.addView(syncStatus, top(dp(8)))

        content.addView(TextView(this).apply {
            text = "Linux側マウント先は安全のため /workspace/phone/ 以下に固定です（フォルダ名は表示名から生成）。" +
                "同期はファイルをコピーする方式のため、大きなファイルは時間がかかります。"
            textSize = 12.5f
            setTextColor(muted)
            setPadding(dp(2), dp(12), dp(2), 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(page)
            isFillViewport = true
            addView(content)
        }
    }

    private fun render(mappings: List<StorageShareManager.Mapping>) {
        rows.clear()
        rowsHost.removeAllViews()
        if (mappings.isEmpty()) {
            rowsHost.addView(TextView(this).apply {
                text = "共有設定はありません。「共有先を追加」または「初期値に戻す」を使用できます。"
                textSize = 13.5f
                setTextColor(muted)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(soft, border, 12)
            })
            return
        }

        mappings.forEachIndexed { index, mapping ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(card, border, 16)
            }

            val enabled = CheckBox(this).apply {
                text = "この共有を有効にする"
                isChecked = mapping.enabled
                setTextColor(this@StorageSettingsActivity.text)
            }
            box.addView(enabled)

            val label = field("表示名（Linux側フォルダ名に使用）", mapping.label)
            box.addView(label, top(dp(6)))

            val guestText = TextView(this).apply {
                textSize = 12.5f
                setTextColor(muted)
                setPadding(dp(2), dp(6), dp(2), 0)
                text = "Linux側: ${mapping.guestPath()}"
            }
            // 表示名の変更に追従して Linux 側パス表示を更新する。
            label.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val name = StorageShareManager.sanitizeFolderName(s?.toString().orEmpty())
                    guestText.text = "Linux側: /workspace/phone/$name"
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            box.addView(guestText)

            val treeText = TextView(this).apply {
                textSize = 13f
                setTextColor(this@StorageSettingsActivity.text)
                setPadding(dp(2), dp(8), dp(2), 0)
                text = "Android側: ${describeTree(mapping.treeUri, mapping.treeLabel)}"
            }
            box.addView(treeText)

            val treeButton = button("Android側フォルダを選択") {
                pendingTreeRowIndex = rows.indexOfFirst { it.root === box }
                runCatching {
                    startActivityForResult(SafSyncManager.openTreeIntent(), REQUEST_OPEN_TREE)
                }.onFailure {
                    Toast.makeText(this, "フォルダ選択を開けませんでした", Toast.LENGTH_LONG).show()
                }
            }
            box.addView(treeButton, top(dp(8)))

            val directionButton = button(directionLabel(mapping.direction)) {}
            directionButton.setOnClickListener {
                val next = nextDirection(rowFor(box)?.direction ?: mapping.direction)
                rowFor(box)?.let {
                    it.direction = next
                    directionButton.text = directionLabel(next)
                }
            }
            box.addView(directionButton, top(dp(8)))

            box.addView(Button(this).apply {
                text = "この共有を削除"
                isAllCaps = false
                setTextColor(danger)
                background = rounded(Color.rgb(245, 229, 226), border, 10)
                setOnClickListener {
                    val current = collectRows(showErrors = false).getOrElse { mappings }.toMutableList()
                    if (index in current.indices) current.removeAt(index)
                    render(current)
                }
            }, top(dp(8)))

            rowsHost.addView(box, if (index == 0) top(0) else top(dp(10)))
            rows += RowRefs(
                root = box,
                enabled = enabled,
                label = label,
                treeUri = mapping.treeUri,
                treeLabel = mapping.treeLabel,
                treeButton = treeButton,
                treeText = treeText,
                direction = mapping.direction,
                directionButton = directionButton,
                guestText = guestText
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_TREE) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val row = rows.getOrNull(pendingTreeRowIndex) ?: return
        val treeLabel = runCatching { SafSyncManager.persistTree(this, uri) }
            .getOrElse {
                Toast.makeText(this, "フォルダの許可を保存できませんでした", Toast.LENGTH_LONG).show()
                return
            }
        row.treeUri = uri
        row.treeLabel = treeLabel
        row.treeText.text = "Android側: ${describeTree(uri, treeLabel)}"
        Toast.makeText(this, "「$treeLabel」を共有フォルダに設定しました", Toast.LENGTH_SHORT).show()
    }

    private fun rowFor(box: View): RowRefs? = rows.firstOrNull { it.root === box }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        // フォルダ名用途。オートコレクト/サジェストを抑制する。
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(true)
        textSize = 14f
        setTextColor(this@StorageSettingsActivity.text)
        setHintTextColor(muted)
        setPadding(dp(10), dp(9), dp(10), dp(9))
        background = rounded(soft, border, 10)
    }

    private fun collectRows(showErrors: Boolean): Result<List<StorageShareManager.Mapping>> = runCatching {
        val result = rows.map { row ->
            StorageShareManager.Mapping(
                label = row.label.text.toString().trim(),
                enabled = row.enabled.isChecked,
                treeUri = row.treeUri,
                treeLabel = row.treeLabel,
                direction = row.direction
            )
        }
        StorageShareManager.validate(result).getOrThrow()
        result
    }.onFailure { error ->
        if (showErrors) {
            AlertDialog.Builder(this)
                .setTitle("共有設定を確認してください")
                .setMessage(error.message ?: "設定値が不正です。")
                .setPositiveButton("閉じる", null)
                .show()
        }
    }

    private fun save() {
        val mappings = collectRows(showErrors = true).getOrNull() ?: return
        StorageShareManager.save(this, mappings)
            .onSuccess {
                Toast.makeText(this, "共有設定を保存しました", Toast.LENGTH_SHORT).show()
                render(StorageShareManager.load(this))
            }
            .onFailure {
                AlertDialog.Builder(this)
                    .setTitle("保存できませんでした")
                    .setMessage(it.message ?: "不明なエラー")
                    .setPositiveButton("閉じる", null)
                    .show()
            }
    }

    private fun syncNow() {
        val mappings = collectRows(showErrors = true).getOrNull() ?: return
        // 同期前に必ず保存し、SafSyncManager が最新設定を読めるようにする。
        val saved = StorageShareManager.save(this, mappings)
        if (saved.isFailure) {
            AlertDialog.Builder(this)
                .setTitle("保存できませんでした")
                .setMessage(saved.exceptionOrNull()?.message ?: "不明なエラー")
                .setPositiveButton("閉じる", null)
                .show()
            return
        }
        if (StorageShareManager.enabledMappings(this).isEmpty()) {
            Toast.makeText(this, "有効な共有フォルダ（フォルダ選択済み）がありません", Toast.LENGTH_LONG).show()
            return
        }

        syncProgress.visibility = View.VISIBLE
        syncStatus.visibility = View.VISIBLE
        syncStatus.text = "同期を開始しています…"
        SafSyncManager.syncAll(
            this,
            onProgress = { syncStatus.text = it.message },
            onComplete = { result ->
                syncProgress.visibility = View.GONE
                result.onSuccess { results ->
                    val totalIn = results.sumOf { it.imported }
                    val totalOut = results.sumOf { it.exported }
                    val totalSkip = results.sumOf { it.skipped }
                    val failures = results.flatMap { it.failures }
                    syncStatus.text = buildString {
                        append("同期完了: 取込 $totalIn 件 / 書出 $totalOut 件 / 変更なし $totalSkip 件")
                        if (failures.isNotEmpty()) {
                            append("\n\n失敗 ${failures.size} 件:\n")
                            append(failures.take(5).joinToString("\n"))
                            if (failures.size > 5) append("\n… 他 ${failures.size - 5} 件")
                        }
                    }
                }.onFailure {
                    syncStatus.text = "同期に失敗しました: ${it.message ?: "不明なエラー"}"
                }
            }
        )
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("初期値に戻す")
            .setMessage("共有設定を Documents 1件（フォルダ未選択）へ戻します。選択済みのフォルダ許可は保持されます。")
            .setPositiveButton("戻す") { _, _ ->
                StorageShareManager.reset(this)
                render(StorageShareManager.defaultMappings())
                Toast.makeText(this, "初期値に戻しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun describeTree(uri: Uri?, treeLabel: String): String =
        if (uri == null) "未選択（フォルダを選択してください）" else treeLabel

    private fun directionLabel(direction: StorageShareManager.SyncDirection): String =
        when (direction) {
            StorageShareManager.SyncDirection.BIDIRECTIONAL -> "同期方向: 双方向（新しい方優先）"
            StorageShareManager.SyncDirection.IMPORT_ONLY -> "同期方向: 取込のみ（Android → Linux）"
            StorageShareManager.SyncDirection.EXPORT_ONLY -> "同期方向: 書出のみ（Linux → Android）"
        }

    private fun nextDirection(current: StorageShareManager.SyncDirection): StorageShareManager.SyncDirection {
        val values = StorageShareManager.SyncDirection.entries
        return values[(values.indexOf(current) + 1) % values.size]
    }

    private fun primary(value: String, click: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(accent, accent, 12)
        setOnClickListener { click() }
    }

    private fun button(value: String, click: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(this@StorageSettingsActivity.text)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(soft, border, 12)
        setOnClickListener { click() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun top(value: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = value }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_OPEN_TREE = 4201
    }
}
