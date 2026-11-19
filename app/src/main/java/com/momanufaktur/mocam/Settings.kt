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

    /** Alle vier Register (Foto/Video × Rück/Front). */
    val logoContexts = listOf("rear_photo", "front_photo", "rear_video", "front_video")

    /** Überträgt Logo, Position, Größe, Deckkraft und Drehung des aktuellen Registers auf alle anderen. */
    fun copyLogoToAllContexts() {
        val cx = logoCx; val cy = logoCy; val w = logoW; val a = logoAlpha
        val rot = logoRotation; val logo = selectedLogo
        val e = p.edit()
        logoContexts.forEach { c ->
            e.putFloat("logoCx_$c", cx).putFloat("logoCy_$c", cy).putFloat("logoW_$c", w)
                .putFloat("logoAlpha_$c", a).putInt("logoRotation_$c", rot)
                .putString("selectedLogo_$c", logo)
        }
        e.apply()
    }

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

    /** Video: Bild links/rechts tauschen (falls die Aufnahme gegenüber der Vorschau gespiegelt ist). */
    // Mate 20 Pro: Frontvideo ist gegenüber der Vorschau gespiegelt -> standardmäßig zurückspiegeln
    fun getVideoFlip(front: Boolean): Boolean = p.getBoolean("videoFlip2_" + if (front) "front" else "rear", front)

    fun setVideoFlip(front: Boolean, v: Boolean) {
        p.edit().putBoolean("videoFlip2_" + (if (front) "front" else "rear"), v).apply()
    }

    private fun fixKey(front: Boolean, target: String) = "fix2_" + (if (front) "front_" else "rear_") + target

    /** Dateiname des gewählten Logos in der Logo-Bibliothek. */
    var selectedLogo: String?
        get() = p.getString(lk("selectedLogo"), p.getString("selectedLogo", null))
        set(v) = p.edit().putString(lk("selectedLogo"), v).apply()

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

    /** #1 Zeitlimit für Videos in Sekunden (0 = aus). */
    var timeLimit: Int
        get() = int("timeLimit", 0)
        set(v) = p.edit().putInt("timeLimit", v).apply()

    /** #2 Projektordner: Liste der angelegten Ordner und Zuordnung Logo -> Ordner. */
    var folders: Set<String>
        get() = p.getStringSet("folders", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("folders", HashSet(v)).apply()

    fun folderForLogo(logoFile: String?): String =
        if (logoFile == null) "" else p.getString("logoFolder_$logoFile", "") ?: ""

    fun setFolderForLogo(logoFile: String, folder: String) {
        p.edit().putString("logoFolder_$logoFile", folder).apply()
    }

    /** #6 Bauchbinde */
    var lowerEnabled: Boolean
        get() = bool("lowerEnabled", false)
        set(v) = p.edit().putBoolean("lowerEnabled", v).apply()

    var lowerName: String
        get() = p.getString("lowerName", "Kai") ?: ""
        set(v) = p.edit().putString("lowerName", v).apply()

    var lowerTitle: String
        get() = p.getString("lowerTitle", "Gründer · MoManufaktur") ?: ""
        set(v) = p.edit().putString("lowerTitle", v).apply()

    var lowerSeconds: Int
        get() = int("lowerSeconds", 5)
        set(v) = p.edit().putInt("lowerSeconds", v).apply()

    /** #5 Zeitraffer-Faktor (0 = aus). */
    var timelapse: Int
        get() = int("timelapse", 0)
        set(v) = p.edit().putInt("timelapse", v).apply()

    /** #8 Mikrofon: "auto" (intern bzw. USB automatisch) oder "bluetooth". */
    var micMode: String
        get() = p.getString("micMode", "auto") ?: "auto"
        set(v) = p.edit().putString("micMode", v).apply()

    /** #10 Intro/Outro an jedes Video anhängen. */
    var introOutroEnabled: Boolean
        get() = bool("introOutro", false)
        set(v) = p.edit().putBoolean("introOutro", v).apply()

    /** #9 Untertitel automatisch einbrennen. */
    var subtitlesEnabled: Boolean
        get() = bool("subtitles", false)
        set(v) = p.edit().putBoolean("subtitles", v).apply()

    /** #6 Gestaltung der Bauchbinde */
    var lowerStyle: LowerThirdStyle
        get() {
            val d = LowerThirdStyle()
            return LowerThirdStyle(
                x = float("lt_x", d.x), y = float("lt_y", d.y), width = float("lt_w", d.width),
                nameSize = float("lt_ns", d.nameSize), titleSize = float("lt_ts", d.titleSize),
                font = int("lt_font", d.font),
                bgColor = int("lt_bg", d.bgColor), bgAlpha = float("lt_bga", d.bgAlpha),
                accentColor = int("lt_acc", d.accentColor),
                nameColor = int("lt_nc", d.nameColor), titleColor = int("lt_tc", d.titleColor)
            )
        }
        set(v) = p.edit()
            .putFloat("lt_x", v.x).putFloat("lt_y", v.y).putFloat("lt_w", v.width)
            .putFloat("lt_ns", v.nameSize).putFloat("lt_ts", v.titleSize).putInt("lt_font", v.font)
            .putInt("lt_bg", v.bgColor).putFloat("lt_bga", v.bgAlpha).putInt("lt_acc", v.accentColor)
            .putInt("lt_nc", v.nameColor).putInt("lt_tc", v.titleColor)
            .apply()

    /** #9 Gestaltung der Untertitel */
    var subtitleStyle: SubtitleStyle
        get() {
            val d = SubtitleStyle()
            return SubtitleStyle(float("sub_y", d.posY), float("sub_size", d.size), float("sub_bga", d.bgAlpha))
        }
        set(v) = p.edit().putFloat("sub_y", v.posY).putFloat("sub_size", v.size).putFloat("sub_bga", v.bgAlpha).apply()

    /** #9 "burn" = einbrennen, "srt" = .srt-Datei, "both" = beides */
    var subtitleMode: String
        get() = p.getString("sub_mode", "burn") ?: "burn"
        set(v) = p.edit().putString("sub_mode", v).apply()

    /** Obere Buttonleiste: false = eine Zeile scrollbar, true = alle sichtbar (mehrere Zeilen). */
    var topBarWrap: Boolean
        get() = bool("topBarWrap", false)
        set(v) = p.edit().putBoolean("topBarWrap", v).apply()

    /** #9 Sprache der Untertitel: "de", "en-gb", "en-us" */
    var subtitleLang: String
        get() = p.getString("sub_lang", "de") ?: "de"
        set(v) = p.edit().putString("sub_lang", v).apply()

    /** #9 Bildrate (0 = automatisch) und #10 Bitrate in Mbit/s (0 = automatisch). */
    var fps: Int
        get() = int("fps", 0)
        set(v) = p.edit().putInt("fps", v).apply()

    var bitrateMbps: Int
        get() = int("bitrate", 0)
        set(v) = p.edit().putInt("bitrate", v).apply()

    /** #15 Copyright/Autor in den Foto-Metadaten. */
    var copyrightEnabled: Boolean
        get() = bool("copyrightOn", true)
        set(v) = p.edit().putBoolean("copyrightOn", v).apply()

    var copyrightAuthor: String
        get() = p.getString("copyrightAuthor", "Kai") ?: ""
        set(v) = p.edit().putString("copyrightAuthor", v).apply()

    var copyrightText: String
        get() = p.getString("copyrightText", "© MoManufaktur") ?: ""
        set(v) = p.edit().putString("copyrightText", v).apply()

    /** #16 Sprachauslöser */
    var voiceTrigger: Boolean
        get() = bool("voiceTrigger", false)
        set(v) = p.edit().putBoolean("voiceTrigger", v).apply()

    /** #5–#8 Anzeigen */
    var zebra: Boolean
        get() = bool("zebra", false)
        set(v) = p.edit().putBoolean("zebra", v).apply()

    var histogram: Boolean
        get() = bool("histogram", false)
        set(v) = p.edit().putBoolean("histogram", v).apply()

    var level: Boolean
        get() = bool("level", false)
        set(v) = p.edit().putBoolean("level", v).apply()

    var markers: Boolean
        get() = bool("markers", false)
        set(v) = p.edit().putBoolean("markers", v).apply()

    companion object {
        const val TARGET_PREVIEW = "preview"
        const val TARGET_VIDEO = "video"
        const val TARGET_PHOTO = "photo"
    }
}
