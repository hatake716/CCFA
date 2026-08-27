package io.github.hatake716.claudecodeandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * scoped storage 準拠の共有フォルダ同期。
 *
 * PRoot は filesDir 内の /workspace/phone/<name> だけを触り、Android 側の
 * SAF ツリーとの受け渡しはこのクラスが [ContentResolver] 経由のコピーで行う。
 * targetSdk 30+ では /storage/emulated/0/... への生ファイルアクセスができないため、
 * 「常時 bind mount」ではなく「任意タイミングの双方向ミラー同期」で共有を実現する。
 *
 * 同期方向は [StorageShareManager.SyncDirection] に従う:
 *   - BIDIRECTIONAL: 両側を走査し、更新時刻が新しい方を採用（両欠損は無視）
 *   - IMPORT_ONLY:   SAF → ローカル のみ
 *   - EXPORT_ONLY:   ローカル → SAF のみ
 */
object SafSyncManager {

    /** ツリー選択時にアプリへ渡すフラグ（恒久的な読み書き許可）。 */
    const val TREE_PERMISSION_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    data class SyncProgress(val message: String, val copied: Int, val skipped: Int)

    data class SyncResult(
        val label: String,
        val imported: Int,
        val exported: Int,
        val skipped: Int,
        val failures: List<String>
    )

    /**
     * ツリー選択インテント。呼び出し側は startActivityForResult / ActivityResult で受ける。
     */
    fun openTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(TREE_PERMISSION_FLAGS)
        }

    /**
     * ツリー選択結果を恒久化する。成功すれば端末再起動後も同じフォルダへアクセスできる。
     * 返り値はフォルダの表示名（取得できなければ "共有フォルダ"）。
     */
    fun persistTree(context: Context, treeUri: Uri): String {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            TREE_PERMISSION_FLAGS
        )
        val root = DocumentFile.fromTreeUri(context, treeUri)
        return root?.name ?: "共有フォルダ"
    }

    /**
     * 恒久化済みツリー許可を解放する。共有設定を削除したときに呼ぶ。
     */
    fun releaseTree(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                TREE_PERMISSION_FLAGS
            )
        }
    }

    /** 保存済みツリーへ今もアクセスできるか。 */
    fun hasAccess(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && (it.isReadPermission || it.isWritePermission)
        }

    /**
     * 有効な全マッピングを非同期で同期する。進捗と結果はメインスレッドへ返す。
     */
    fun syncAll(
        context: Context,
        onProgress: (SyncProgress) -> Unit,
        onComplete: (Result<List<SyncResult>>) -> Unit
    ) {
        val appContext = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            val outcome = runCatching {
                val mappings = StorageShareManager.enabledMappings(appContext)
                check(mappings.isNotEmpty()) { "有効な共有フォルダがありません。" }
                mappings.map { mapping ->
                    main.post {
                        onProgress(SyncProgress("${mapping.label} を同期中…", 0, 0))
                    }
                    syncOne(appContext, mapping) { copied, skipped ->
                        main.post {
                            onProgress(SyncProgress("${mapping.label}: $copied 件コピー", copied, skipped))
                        }
                    }
                }
            }
            main.post { onComplete(outcome) }
        }, "CCFASafSync").start()
    }

    private fun syncOne(
        context: Context,
        mapping: StorageShareManager.Mapping,
        onCount: (Int, Int) -> Unit
    ): SyncResult {
        val treeUri = mapping.treeUri
            ?: return SyncResult(mapping.label, 0, 0, 0, listOf("フォルダ未選択"))
        val root = DocumentFile.fromTreeUri(context, treeUri)
        check(root != null && root.isDirectory) {
            "${mapping.label}: 共有フォルダにアクセスできません。選び直してください。"
        }
        val localRoot = File(EmbeddedRuntimeManager.workspaceDir(context), mapping.workspaceRelativePath())
        localRoot.mkdirs()

        val counters = Counters()
        val failures = mutableListOf<String>()
        mirror(context, root, localRoot, mapping.direction, counters, failures) {
            onCount(counters.imported + counters.exported, counters.skipped)
        }
        return SyncResult(
            label = mapping.label,
            imported = counters.imported,
            exported = counters.exported,
            skipped = counters.skipped,
            failures = failures
        )
    }

    private class Counters(var imported: Int = 0, var exported: Int = 0, var skipped: Int = 0)

    /**
     * SAF ディレクトリ [safDir] とローカルディレクトリ [localDir] を再帰的にミラーする。
     */
    private fun mirror(
        context: Context,
        safDir: DocumentFile,
        localDir: File,
        direction: StorageShareManager.SyncDirection,
        counters: Counters,
        failures: MutableList<String>,
        tick: () -> Unit
    ) {
        val importAllowed = direction != StorageShareManager.SyncDirection.EXPORT_ONLY
        val exportAllowed = direction != StorageShareManager.SyncDirection.IMPORT_ONLY

        val safChildren = safDir.listFiles()
            .mapNotNull { child -> child.name?.takeIf { it.isNotBlank() }?.let { it to child } }
            .toMap()
        val localChildren = localDir.listFiles()?.associateBy { it.name }.orEmpty()
        val names = (safChildren.keys + localChildren.keys).toSortedSet()

        for (name in names) {
            if (name == "." || name == "..") continue
            val safChild = safChildren[name]
            val localChild = localChildren[name]

            when {
                // 両側にディレクトリ → 再帰
                safChild?.isDirectory == true && localChild?.isDirectory == true ->
                    mirror(context, safChild, localChild, direction, counters, failures, tick)

                // SAF 側だけディレクトリ（取込）
                safChild?.isDirectory == true -> {
                    if (importAllowed) {
                        val newLocal = File(localDir, name).apply { mkdirs() }
                        mirror(context, safChild, newLocal, direction, counters, failures, tick)
                    }
                }

                // ローカル側だけディレクトリ（書出）
                localChild?.isDirectory == true -> {
                    if (exportAllowed) {
                        val newSaf = safDir.findFile(name)?.takeIf { it.isDirectory }
                            ?: safDir.createDirectory(name)
                        if (newSaf != null) {
                            mirror(context, newSaf, localChild, direction, counters, failures, tick)
                        } else {
                            failures += "$name フォルダを Android 側に作成できませんでした"
                        }
                    }
                }

                // 両側にファイル → 新しい方を採用
                safChild?.isFile == true && localChild?.isFile == true -> {
                    val safTime = safChild.lastModified()
                    val localTime = localChild.lastModified()
                    when {
                        safTime > localTime + TIME_SLACK_MS && importAllowed ->
                            copyImport(context, safChild, localChild, counters, failures).also { tick() }
                        localTime > safTime + TIME_SLACK_MS && exportAllowed ->
                            copyExport(context, safDir, localChild, counters, failures).also { tick() }
                        else -> counters.skipped++
                    }
                }

                // SAF 側だけファイル（取込）
                safChild?.isFile == true -> {
                    if (importAllowed) {
                        copyImport(context, safChild, File(localDir, name), counters, failures).also { tick() }
                    }
                }

                // ローカル側だけファイル（書出）
                localChild?.isFile == true -> {
                    if (exportAllowed) {
                        copyExport(context, safDir, localChild, counters, failures).also { tick() }
                    }
                }
            }
        }
    }

    private fun copyImport(
        context: Context,
        src: DocumentFile,
        dest: File,
        counters: Counters,
        failures: MutableList<String>
    ) {
        runCatching {
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(src.uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("入力ストリームを開けません")
            val t = src.lastModified()
            if (t > 0) dest.setLastModified(t)
            counters.imported++
        }.onFailure { failures += "取込失敗: ${src.name} (${it.message})" }
    }

    private fun copyExport(
        context: Context,
        destDir: DocumentFile,
        src: File,
        counters: Counters,
        failures: MutableList<String>
    ) {
        runCatching {
            val existing = destDir.findFile(src.name)?.takeIf { it.isFile }
            val target = existing ?: destDir.createFile(mimeFor(src.name), src.name)
            ?: error("Android 側にファイルを作成できません")
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                src.inputStream().use { input -> input.copyTo(output) }
            } ?: error("出力ストリームを開けません")
            counters.exported++
        }.onFailure { failures += "書出失敗: ${src.name} (${it.message})" }
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "md", "log", "sh", "py", "kt", "java", "c", "h", "json", "yaml", "yml", "toml", "ini", "conf" ->
                "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "gz", "tgz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }

    /**
     * SAF(秒精度) と ext4(ミリ秒) の丸め差でミラーが往復し続けないよう許容差を設ける。
     */
    private const val TIME_SLACK_MS = 2_000L
}
