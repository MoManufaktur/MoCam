package com.momanufaktur.mocam

import android.util.Log

/** Fortschritt der Erkennung (0–100 %), wird aus dem nativen Code aufgerufen. */
fun interface WhisperProgress {
    fun onProgress(percent: Int)
}

/** Dünne Kotlin-Hülle um whisper.cpp (native Bibliothek „mocamwhisper“). */
object Whisper {

    val available: Boolean by lazy {
        try {
            System.loadLibrary("mocamwhisper")
            true
        } catch (t: Throwable) {
            Log.e("MoCam", "Whisper-Bibliothek fehlt", t)
            false
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeFree(ctx: Long)
    private external fun nativeTranscribe(
        ctx: Long, pcm: FloatArray, lang: String, prompt: String?, threads: Int, beam: Int, progress: WhisperProgress?
    ): ByteArray

    /** Ein erkannter Block: Start/Ende in Sekunden und Text. */
    data class Segment(val start: Double, val end: Double, val text: String)

    fun transcribe(
        modelPath: String, pcm: FloatArray, lang: String, prompt: String?,
        threads: Int = 4, beam: Int = 1, progress: WhisperProgress? = null
    ): List<Segment> {
        check(available) { "Whisper ist auf diesem Gerät nicht verfügbar" }
        val ctx = nativeInit(modelPath)
        check(ctx != 0L) { "Whisper-Modell konnte nicht geladen werden" }
        try {
            val raw = String(nativeTranscribe(ctx, pcm, lang, prompt, threads, beam, progress), Charsets.UTF_8)
            return raw.lineSequence().mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val t0 = parts[0].toLongOrNull() ?: return@mapNotNull null
                val t1 = parts[1].toLongOrNull() ?: return@mapNotNull null
                Segment(t0 / 100.0, t1 / 100.0, parts[2].trim())
            }.filter { it.text.isNotBlank() }.toList()
        } finally {
            nativeFree(ctx)
        }
    }
}
