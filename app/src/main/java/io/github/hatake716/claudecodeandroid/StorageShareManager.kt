package io.github.hatake716.claudecodeandroid

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists Android-folder <-> Linux share settings for the Google Play build.
 *
 * sideload 版は「Android の任意パスを PRoot へ常時 bind mount」していたが、
 * targetSdk 30+ の scoped storage では /storage/emulated/0/... への直接
 * ファイルアクセス（PRoot がネイティブに open する経路）がカーネルレベルで
 * 拒否されるため、その方式は成立しない。
 *
 * Play 版では方式を変える:
 *   - ユーザーが SAF (ACTION_OPEN_DOCUMENT_TREE) で共有フォルダを選ぶ
 *   - そのツリー URI を恒久保持（takePersistableUriPermission）
 *   - PRoot がアクセスするのはアプリ専用領域 filesDir 内の
 *     /workspace/phone/<name> だけ（[SafSyncManager] がミラー同期する）
 *
 * したがって [Mapping.treeUri] が SAF ツリー、[Mapping.guestPath] は必ず
 * filesDir 内の /workspace/phone/ 以下を指す。ホストの生パスは保持しない。
 */
object StorageShareManager {
    private const val PREFS = "storage-shares"
    private const val KEY_MAPPINGS = "mappings-json-saf-v2"
    private const val MAX_MAPPINGS = 8

    /** ワークスペース内で SAF 共有フォルダを束ねるサブディレクトリ名。 */
    const val PHONE_SUBDIR = "phone"

    enum class SyncDirection {
        /** Android(SAF) ↔ Linux を双方向ミラー（既定・新しい方優先）。 */
        BIDIRECTIONAL,

        /** Android(SAF) → Linux のみ（取込）。 */
        IMPORT_ONLY,

        /** Linux → Android(SAF) のみ（書出）。 */
        EXPORT_ONLY;

        companion object {
            fun from(value: String?): SyncDirection =
                entries.firstOrNull { it.name == value } ?: BIDIRECTIONAL
        }
    }

    /**
     * 1件の共有設定。
     *
     * @param label      表示名（/workspace/phone/<sanitize(label)> のフォルダ名にも使う）
     * @param enabled    有効フラグ
     * @param treeUri    SAF の永続化ツリー URI（未選択なら null）
     * @param treeLabel  選択フォルダの人間可読名（DocumentsUI が返す表示名）
     * @param direction  同期方向
     */
    data class Mapping(
        val label: String,
        val enabled: Boolean,
        val treeUri: Uri?,
        val treeLabel: String,
        val direction: SyncDirection
    ) {
        /** filesDir 内の workspace 相対パス（例: phone/Documents）。 */
        fun workspaceRelativePath(): String = "$PHONE_SUBDIR/${sanitizeFolderName(label)}"

        /** Linux から見えるパス（/workspace は EmbeddedRuntimeManager が bind 済み）。 */
        fun guestPath(): String = "/workspace/${workspaceRelativePath()}"
    }

    fun defaultMappings(): List<Mapping> = listOf(
        Mapping(
            label = "Documents",
            enabled = true,
            treeUri = null,
            treeLabel = "未選択",
            direction = SyncDirection.BIDIRECTIONAL
        )
    )

    fun load(context: Context): List<Mapping> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MAPPINGS, null)
            ?: return defaultMappings()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val uriString = item.optString("treeUri", "")
                    add(
                        Mapping(
                            label = item.optString("label", "共有フォルダ"),
                            enabled = item.optBoolean("enabled", true),
                            treeUri = uriString.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            treeLabel = item.optString("treeLabel", "未選択"),
                            direction = SyncDirection.from(item.optString("direction"))
                        )
                    )
                }
            }.ifEmpty { defaultMappings() }
        }.getOrElse { defaultMappings() }
    }

    fun save(context: Context, mappings: List<Mapping>): Result<Unit> = runCatching {
        validate(mappings).getOrThrow()
        val array = JSONArray()
        mappings.forEach { mapping ->
            array.put(
                JSONObject().apply {
                    put("label", mapping.label.trim())
                    put("enabled", mapping.enabled)
                    put("treeUri", mapping.treeUri?.toString().orEmpty())
                    put("treeLabel", mapping.treeLabel)
                    put("direction", mapping.direction.name)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAPPINGS, array.toString())
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MAPPINGS)
            .apply()
    }

    fun validate(mappings: List<Mapping>): Result<Unit> = runCatching {
        require(mappings.size <= MAX_MAPPINGS) { "共有設定は最大${MAX_MAPPINGS}件です。" }
        val usedFolders = mutableSetOf<String>()
        mappings.forEachIndexed { index, mapping ->
            val label = mapping.label.trim()
            require(label.isNotBlank()) { "${index + 1}件目の表示名を入力してください。" }
            val folder = sanitizeFolderName(label)
            require(folder.isNotBlank()) {
                "$label: 表示名には英数字を含めてください（Linux側フォルダ名に使用します）。"
            }
            if (mapping.enabled) {
                require(mapping.treeUri != null) {
                    "$label: 「フォルダを選択」で Android 側の共有フォルダを指定してください。"
                }
                require(usedFolders.add(folder)) {
                    "Linux側フォルダ名 phone/$folder が重複しています。表示名を変えてください。"
                }
            }
        }
    }

    fun enabledMappings(context: Context): List<Mapping> =
        load(context).filter { it.enabled && it.treeUri != null }

    /**
     * PRoot 起動前に、有効な共有フォルダの Linux 側ディレクトリ（filesDir 内）を用意する。
     * 実データの同期は [SafSyncManager] がユーザー操作で行う。
     */
    fun prepareGuestDirectories(context: Context, rootfsUnused: File?) {
        val workspace = EmbeddedRuntimeManager.workspaceDir(context)
        enabledMappings(context).forEach { mapping ->
            File(workspace, mapping.workspaceRelativePath()).mkdirs()
        }
    }

    fun newCustomMapping(index: Int): Mapping = Mapping(
        label = "Shared$index",
        enabled = true,
        treeUri = null,
        treeLabel = "未選択",
        direction = SyncDirection.BIDIRECTIONAL
    )

    /**
     * 表示名を Linux フォルダ名・ファイルシステム安全な形へ正規化する。
     * 英数字・. _ - のみ残し、その他は _ に置換。先頭末尾の _ を削る。
     */
    fun sanitizeFolderName(label: String): String {
        val cleaned = buildString {
            label.trim().forEach { ch ->
                append(if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') ch else '_')
            }
        }
        return cleaned.trim('_', '.').ifBlank { "shared" }
    }
}
