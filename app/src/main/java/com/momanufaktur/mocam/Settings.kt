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

    /** Korrektur, falls das Logo im Video gedreht erscheint (0/90/180/270). */
    var logoRotOffset: Int
        get() = int("logoRotOffset", 0)
        set(v) = p.edit().putInt("logoRotOffset", v).apply()
}
