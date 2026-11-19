package com.momanufaktur.mocam

import android.content.Context

/** Dauerhaft gespeicherte Einstellungen. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("mocam", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = p.getInt(key, def)
    private fun float(key: String, def: Float) = p.getFloat(key, def)
    private fun bool(key: String, def: Boolean) = p.getBoolean(key, def)

    var filterIndex: Int
        get() = int("filter", 0)
        set(v) = p.edit().putInt("filter", v).apply()

    var videoMode: Boolean
        get() = bool("videoMode", true)
        set(v) = p.edit().putBoolean("videoMode", v).apply()

    var qualityIndex: Int
        get() = int("quality", 1)
        set(v) = p.edit().putInt("quality", v).apply()

    var audio: Boolean
        get() = bool("audio", true)
        set(v) = p.edit().putBoolean("audio", v).apply()

    var grid: Boolean
        get() = bool("grid", false)
        set(v) = p.edit().putBoolean("grid", v).apply()

    var timer: Int
        get() = int("timer", 0)
        set(v) = p.edit().putInt("timer", v).apply()

    var logoEnabled: Boolean
        get() = bool("logoEnabled", true)
        set(v) = p.edit().putBoolean("logoEnabled", v).apply()

    var logoOnPhotos: Boolean
        get() = bool("logoOnPhotos", true)
        set(v) = p.edit().putBoolean("logoOnPhotos", v).apply()

    /**
     * Logo-Einstellungen gelten je Kamera und Modus („rear_video“, „front_photo“ …).
     * Wird von MainActivity beim Kamera-/Moduswechsel gesetzt. Fehlt ein Wert, gilt der alte globale.
     */
    var logoContext: String = "rear_video"

    private fun lk(key: String) = key + "_" + logoContext
    private fun lf(key: String, def: Float) = p.getFloat(lk(key), p.getFloat(key, def))
    private fun li(key: String, def: Int) = p.getInt(lk(key), p.getInt(key, def))

    var logoCx: Float
        get() = lf("logoCx", 0.80f)
        set(v) = p.edit().putFloat(lk("logoCx"), v).apply()

    var logoCy: Float
        get() = lf("logoCy", 0.10f)
        set(v) = p.edit().putFloat(lk("logoCy"), v).apply()

    var logoW: Float
        get() = lf("logoW", 0.30f)
        set(v) = p.edit().putFloat(lk("logoW"), v).apply()

    var logoAlpha: Float
        get() = lf("logoAlpha", 1f)
        set(v) = p.edit().putFloat(lk("logoAlpha"), v).apply()

    /** Fingerrichtung beim Verschieben (0..7: Drehung (Wert % 4) * 90°, ab 4 gespiegelt). */
    var logoDragFix: Int
        get() = p.getInt(lk("logoDragFix"), 0)
        set(v) = p.edit().putInt(lk("logoDragFix"), v).apply()

    /** Index in der Formatliste (0 = 9:16, 1 = 3:4, 2 = 4:5, 3 = 1:1) */
    var videoFormat: Int
        get() = int("videoFormat", 0)
        set(v) = p.edit().putInt("videoFormat", v).apply()

    var photoFormat: Int
        get() = int("photoFormat", 1)
        set(v) = p.edit().putInt("photoFormat", v).apply()

    /** Frontkamera gespiegelt (wie Spiegel) oder ungespiegelt aufnehmen/anzeigen. */
    var frontMirror: Boolean
        get() = bool("frontMirror", false)
        set(v) = p.edit().putBoolean("frontMirror", v).apply()

    /**
     * Grundeinstellung Drehen/Spiegeln pro Kamera und Ausgabe (Vorschau, Video, Foto).
     * 0..7: Drehung = (Wert % 4) * 90°, ab 4 zusätzlich gespiegelt.
     */
    fun getFix(front: Boolean, target: String): Int = int(fixKey(front, target), 0)

    fun setFix(front: Boolean, target: String, v: Int) {
        p.edit().putInt(fixKey(front, target), v).apply()
    }

    private fun fixKey(front: Boolean, target: String) = "fix_" + (if (front) "front_" else "rear_") + target

    /** Dateiname des gewählten Logos in der Logo-Bibliothek. */
    var selectedLogo: String?
        get() = p.getString("selectedLogo", null)
        set(v) = p.edit().putString("selectedLogo", v).apply()

    var prompterText: String
        get() = p.getString("prompterText", "") ?: ""
        set(v) = p.edit().putString("prompterText", v).apply()

    /** Tempo-Regler 0..100 */
    var prompterSpeed: Int
        get() = int("prompterSpeed", 25)
        set(v) = p.edit().putInt("prompterSpeed", v).apply()

    var prompterTextSize: Float
        get() = float("prompterTextSize", 28f)
        set(v) = p.edit().putFloat("prompterTextSize", v).apply()

    /** Drehung des Logos selbst (0/90/180/270), gilt für Video, Vorschau und Fotos. */
    var logoRotation: Int
        get() = li("logoRotation", 0)
        set(v) = p.edit().putInt(lk("logoRotation"), v).apply()

    companion object {
        const val TARGET_PREVIEW = "preview"
        const val TARGET_VIDEO = "video"
        const val TARGET_PHOTO = "photo"
    }
}
