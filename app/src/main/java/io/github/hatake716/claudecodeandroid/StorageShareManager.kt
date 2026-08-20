package io.github.hatake716.claudecodeandroid

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists and validates Android-folder <-> Linux bind mount settings. */
object StorageShareManager {
    private const val PREFS = "storage-shares"
    private const val KEY_MAPPINGS = "mappings-json"
    private const val MAX_MAPPINGS = 8

    data class Mapping(
        val label: String,
        val enabled: Boolean,
        val hostPath: String,
        val guestPath: String
    )

    fun defaultMappings(): List<Mapping> = listOf(
        Mapping(
            label = "Download",
            enabled = true,
            hostPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            guestPath = "/phone/Downloads"
        ),
        Mapping(
            label = "Documents",
            enabled = true,
            hostPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
            guestPath = "/phone/Documents"
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
                    add(
                        Mapping(
                            label = item.optString("label", "共有フォルダ"),
                            enabled = item.optBoolean("enabled", true),
                            hostPath = item.getString("hostPath"),
                            guestPath = item.getString("guestPath")
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
                    put("hostPath", normalizeHostPath(mapping.hostPath))
                    put("guestPath", normalizeGuestPath(mapping.guestPath))
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
        val enabledTargets = mutableSetOf<String>()
        mappings.forEachIndexed { index, raw ->
            val label = raw.label.trim()
            val host = normalizeHostPath(raw.hostPath)
            val guest = normalizeGuestPath(raw.guestPath)
            require(label.isNotBlank()) { "${index + 1}件目の表示名を入力してください。" }
            require(host.startsWith('/')) { "$label: Android側パスは / から始めてください。" }
            require(guest.startsWith("/phone/")) {
                "$label: Linux側マウント先は /phone/ 以下にしてください。"
            }
            require(!host.contains('\n') && !guest.contains('\n')) { "$label: パスに改行は使用できません。" }
            require(!host.contains(':') && !guest.contains(':')) { "$label: パスに : は使用できません。" }
            require(!guest.split('/').contains("..")) { "$label: Linux側パスに .. は使用できません。" }
            if (raw.enabled) {
                require(enabledTargets.add(guest)) { "Linux側マウント先 $guest が重複しています。" }
            }
        }
    }

    fun enabledMappings(context: Context): List<Mapping> =
        load(context).filter { it.enabled }

    fun canReadWrite(mapping: Mapping): Boolean = canReadWrite(File(mapping.hostPath))

    fun canReadWrite(dir: File): Boolean = runCatching {
        if (!dir.exists()) dir.mkdirs()
        if (!dir.isDirectory) return@runCatching false
        val probe = File(dir, ".ccfa-storage-probe-${android.os.Process.myPid()}")
        probe.writeText("ok")
        val ok = probe.readText() == "ok"
        probe.delete()
        ok
    }.getOrDefault(false)

    fun allEnabledMappingsAccessible(context: Context): Boolean {
        val enabled = enabledMappings(context)
        return enabled.isEmpty() || enabled.all(::canReadWrite)
    }

    fun prepareGuestDirectories(rootfs: File, mappings: List<Mapping>) {
        mappings.forEach { mapping ->
            val relative = normalizeGuestPath(mapping.guestPath).removePrefix("/")
            File(rootfs, relative).mkdirs()
        }
    }

    fun newCustomMapping(index: Int): Mapping = Mapping(
        label = "共有フォルダ $index",
        enabled = true,
        hostPath = "/storage/emulated/0/",
        guestPath = "/phone/Shared$index"
    )

    private fun normalizeHostPath(value: String): String =
        value.trim().let { if (it.length > 1) it.trimEnd('/') else it }

    private fun normalizeGuestPath(value: String): String =
        value.trim().let { if (it.length > 1) it.trimEnd('/') else it }
}
