package io.github.hatake716.claudecodeandroid

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Lets the user edit Android-folder <-> Linux /phone bind mappings. */
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
    private val rows = mutableListOf<RowRefs>()

    private data class RowRefs(
        val root: View,
        val enabled: CheckBox,
        val label: EditText,
        val hostPath: EditText,
        val guestPath: EditText
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        setContentView(buildView())
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
            text = "Android側フォルダとLinux側 /phone/ 以下のマウント先を編集できます。変更は次回ターミナル起動から反映されます。"
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
        content.addView(button("アクセス状態を確認") { checkAccess() }, top(dp(8)))
        content.addView(button("初期値に戻す") { confirmReset() }, top(dp(8)))

        content.addView(TextView(this).apply {
            text = "初期値: Android Download → /phone/Downloads、Android Documents → /phone/Documents"
            textSize = 12.5f
            setTextColor(muted)
            setPadding(dp(2), dp(12), dp(2), 0)
        })
        content.addView(TextView(this).apply {
            text = "Linux側マウント先は安全のため /phone/ 以下に限定しています。Android側パスは実際に読み書き権限があるフォルダを指定してください。"
            textSize = 12.5f
            setTextColor(muted)
            setPadding(dp(2), dp(8), dp(2), 0)
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

            val label = field("表示名", mapping.label)
            box.addView(label, top(dp(6)))
            val hostPath = field("Android側パス", mapping.hostPath)
            box.addView(hostPath, top(dp(8)))
            val guestPath = field("Linux側マウント先（/phone/ 以下）", mapping.guestPath)
            box.addView(guestPath, top(dp(8)))

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
            rows += RowRefs(box, enabled, label, hostPath, guestPath)
        }
    }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
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
                hostPath = row.hostPath.text.toString().trim(),
                guestPath = row.guestPath.text.toString().trim()
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

    private fun checkAccess() {
        val mappings = collectRows(showErrors = true).getOrNull() ?: return
        val enabled = mappings.filter { it.enabled }
        if (enabled.isEmpty()) {
            Toast.makeText(this, "有効な共有設定はありません", Toast.LENGTH_LONG).show()
            return
        }
        val message = enabled.joinToString("\n") { mapping ->
            val ok = StorageShareManager.canReadWrite(mapping)
            "${if (ok) "✓" else "×"} ${mapping.label}: ${mapping.hostPath}"
        }
        AlertDialog.Builder(this)
            .setTitle("共有フォルダの読み書き確認")
            .setMessage(message)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("初期値に戻す")
            .setMessage("Download / Documents の標準共有設定へ戻します。")
            .setPositiveButton("戻す") { _, _ ->
                StorageShareManager.reset(this)
                render(StorageShareManager.defaultMappings())
                Toast.makeText(this, "初期値に戻しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
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
}
