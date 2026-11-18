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

    var logoCx: Float
        get() = float("logoCx", 0.80f)
        set(v) = p.edit().putFloat("logoCx", v).apply()

    var logoCy: Float
        get() = float("logoCy", 0.10f)
        set(v) = p.edit().putFloat("logoCy", v).apply()

    var logoW: Float
        get() = float("logoW", 0.30f)
        set(v) = p.edit().putFloat("logoW", v).apply()

    var logoAlpha: Float
        get() = float("logoAlpha", 1f)
        set(v) = p.edit().putFloat("logoAlpha", v).apply()

    /** Index in der Formatliste (0 = 9:16, 1 = 3:4, 2 = 4:5, 3 = 1:1) */
    var videoFormat: Int
        get() = int("videoFormat", 0)
        set(v) = p.edit().putInt("videoFormat", v).apply()

    var photoFormat: Int
        get() = int("photoFormat", 1)
        set(v) = p.edit().putInt("photoFormat", v).apply()

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

    /** Korrektur, falls das Logo im Video gedreht erscheint (0/90/180/270). */
    var logoRotOffset: Int
        get() = int("logoRotOffset", 0)
        set(v) = p.edit().putInt("logoRotOffset", v).apply()
}
