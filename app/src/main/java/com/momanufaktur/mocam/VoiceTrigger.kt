package com.momanufaktur.mocam

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * #16 Sprachauslöser: hört offline (Vosk) auf wenige Schlüsselwörter und löst dann aus.
 * Läuft nur, solange NICHT aufgenommen wird (das Mikrofon braucht dann die Aufnahme).
 */
class VoiceTrigger(private val ctx: Context, private val onTrigger: (String) -> Unit) {

    @Volatile private var running = false
    private var thread: Thread? = null
    private var model: Model? = null
    private var modelLang: String? = null
    private val main = Handler(Looper.getMainLooper())

    /** Schlüsselwörter (Deutsch und Englisch). */
    private val words = listOf("foto", "aufnahme", "bild", "cheese", "photo", "record")

    val isRunning get() = running

    /** Startet das Zuhören. @return false, wenn kein Sprachmodell geladen ist. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val lang = when {
            Subtitles.isModelReady(ctx, "de") -> "de"
            Subtitles.isModelReady(ctx, "en-us") -> "en-us"
            else -> return false
        }
        running = true
        thread = Thread {
            var rec: Recognizer? = null
            var audio: AudioRecord? = null
            try {
                if (model == null || modelLang != lang) {
                    model?.close()
                    model = Model(Subtitles.modelPath(ctx, lang)!!.absolutePath)
                    modelLang = lang
                }
                val grammar = words.joinToString(",", "[", ",\"[unk]\"]") { "\"$it\"" }
                rec = Recognizer(model, 16000f, grammar)
                val bufSize = maxOf(AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT), 8192)
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                audio.startRecording()
                val buf = ByteArray(4096)
                var lastFire = 0L
                while (running) {
                    val n = audio.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (rec.acceptWaveForm(buf, n)) {
                        val text = JSONObject(rec.result).optString("text")
                        val hit = words.firstOrNull { text.split(" ").contains(it) }
                        val now = System.currentTimeMillis()
                        if (hit != null && now - lastFire > 2500) {
                            lastFire = now
                            main.post { onTrigger(hit) }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("MoCamVoice", "Sprachauslöser", t)
                running = false
            } finally {
                try { audio?.stop() } catch (_: Exception) { }
                audio?.release()
                rec?.close()
            }
        }.apply { name = "MoCamVoice"; start() }
        return true
    }

    /** Hört auf und gibt das Mikrofon frei (wartet kurz, bis es frei ist). */
    fun stop() {
        running = false
        thread?.join(800)
        thread = null
    }

    fun release() {
        stop()
        model?.close()
        model = null
    }
}
