package com.momanufaktur.mocam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sicherung der ganzen App in einer ZIP-Datei: alle Einstellungen (alle Projekte) als JSON,
 * Logos (inkl. GIF) sowie Intro/Outro-Clips. Fotos/Videos liegen ohnehin in der Galerie,
 * Sprachmodelle werden bei Bedarf neu geladen.
 */
object Backup {
    private val PREFS = listOf("mocam", "mocam_donate", "mocam_stats")

    private fun clipFiles(ctx: Context) =
        ctx.filesDir.listFiles { f -> f.isFile && (f.name.startsWith("intro_") || f.name.startsWith("outro_")) && f.name.endsWith(".mp4") }
            ?.toList().orEmpty()

    private fun logoFiles(ctx: Context) =
        File(ctx.filesDir, "logos").listFiles { f -> f.isFile }?.toList().orEmpty()

    /** Schreibt die Sicherung; Rückgabe: Anzahl gesicherter Dateien. */
    fun export(ctx: Context, out: OutputStream): Int {
        val root = JSONObject()
            .put("format", 1)
            .put("app", "MoCam")
            .put("version", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
        val prefs = JSONObject()
        for (name in PREFS) {
            val obj = JSONObject()
            for ((k, v) in ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                val e = JSONObject()
                when (v) {
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                obj.put(k, e)
            }
            prefs.put(name, obj)
        }
        root.put("prefs", prefs)
        var count = 0
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("settings.json"))
            z.write(root.toString(2).toByteArray())
            z.closeEntry()
            for (f in logoFiles(ctx)) { add(z, "files/logos/${f.name}", f); count++ }
            for (f in clipFiles(ctx)) { add(z, "files/${f.name}", f); count++ }
            File(ctx.filesDir, "bg").listFiles { f -> f.isFile && !f.name.endsWith(".tflite") }?.forEach { add(z, "files/bg/${it.name}", it); count++ }
        }
        return count
    }

    private fun add(z: ZipOutputStream, name: String, f: File) {
        z.putNextEntry(ZipEntry(name))
        f.inputStream().use { it.copyTo(z) }
        z.closeEntry()
    }

    /**
     * Stellt eine Sicherung wieder her. Ersetzt Einstellungen, Logos und Intro/Outro-Clips komplett.
     * Wirft eine Ausnahme, wenn die Datei keine MoCam-Sicherung ist (dann bleibt alles unverändert).
     */
    fun import(ctx: Context, input: InputStream): Int {
        val staging = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var settingsJson: JSONObject? = null
        var count = 0
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                when {
                    e.name == "settings.json" -> settingsJson = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    e.name.startsWith("files/") -> {
                        val target = File(staging, e.name.removePrefix("files/"))
                        // Schutz gegen manipulierte Pfade („../“)
                        if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                        target.parentFile?.mkdirs()
                        target.outputStream().use { z.copyTo(it) }
                        count++
                    }
                }
            }
        }
        val root = settingsJson ?: throw IllegalArgumentException("Keine MoCam-Sicherung")
        if (root.optString("app") != "MoCam") throw IllegalArgumentException("Keine MoCam-Sicherung")

        // Alte Dateien ersetzen
        File(ctx.filesDir, "logos").deleteRecursively()
        clipFiles(ctx).forEach { it.delete() }
        File(ctx.filesDir, "bg").listFiles { f -> !f.name.endsWith(".tflite") }?.forEach { it.deleteRecursively() }
        staging.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(staging).path
            val dst = File(ctx.filesDir, rel)
            dst.parentFile?.mkdirs()
            f.copyTo(dst, overwrite = true)
        }
        staging.deleteRecursively()

        val prefs = root.getJSONObject("prefs")
        for (name in PREFS) {
            val obj = prefs.optJSONObject(name) ?: continue
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in obj.keys()) {
                val e = obj.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "set" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
        }
        return count
    }
}
