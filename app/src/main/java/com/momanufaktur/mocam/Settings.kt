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

    // ---------------- Projekte ----------------
    // Ein Projekt = ein Logo aus der Bibliothek. Logo-Position (je Kamera/Modus), Bauchbinde,
    // Copyright und Intro/Outro gelten je Projekt. Fehlt ein Projektwert, gilt der bisherige globale Wert.

    /** Dateiname des gewählten Logos = aktuelles Projekt (gilt für alle Kameras und Modi). */
    var selectedLogo: String?
        get() = if (p.contains("project")) p.getString("project", "")?.ifEmpty { null }
                else p.getString("selectedLogo_rear_video", null) ?: p.getString("selectedLogo", null)
        set(v) = p.edit().putString("project", v ?: "").apply()

    /** Schlüssel-Endung des aktuellen Projekts. */
    private val ps: String get() = "@" + (selectedLogo ?: "_")

    /** Für Dateinamen (Intro/Outro je Projekt). */
    val projectFileKey: String
        get() = (selectedLogo ?: "ohne_logo").replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun pS(key: String, def: String) = p.getString(key + ps, null) ?: p.getString(key, def) ?: def
    private fun pB(key: String, def: Boolean) = p.getBoolean(key + ps, p.getBoolean(key, def))
    private fun pI(key: String, def: Int) = p.getInt(key + ps, p.getInt(key, def))
    private fun pF(key: String, def: Float) = p.getFloat(key + ps, p.getFloat(key, def))

    private fun lk(key: String) = key + "_" + logoContext + ps
    private fun lf(key: String, def: Float) = p.getFloat(lk(key), p.getFloat(key + "_" + logoContext, p.getFloat(key, def)))
    private fun li(key: String, def: Int) = p.getInt(lk(key), p.getInt(key + "_" + logoContext, p.getInt(key, def)))

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
        val rot = logoRotation
        val e = p.edit()
        logoContexts.forEach { c ->
            val k = "_" + c + ps
            e.putFloat("logoCx$k", cx).putFloat("logoCy$k", cy).putFloat("logoW$k", w)
                .putFloat("logoAlpha$k", a).putInt("logoRotation$k", rot)
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

    /** Original-Dateiname des gewählten Intro/Outro-Clips im aktuellen Projekt. */
    fun clipName(kind: String): String? = p.getString("clipName_$kind$ps", null)
    fun clipNameFor(kind: String, logo: String): String? = p.getString("clipName_$kind@$logo", null)

    /** Zeitpunkt des letzten Backups (ms) */
    var lastBackup: Long
        get() = p.getLong("lastBackup", 0L)
        set(v) = p.edit().putLong("lastBackup", v).apply()

    /** Einmalige Aufräum-Markierungen (global) */
    fun flag(key: String) = p.getBoolean(key, false)
    fun setFlag(key: String) { p.edit().putBoolean(key, true).apply() }

    /** Animiertes Logo: 2. und 3. Abspielen bei x % der Aufnahmezeit (je Projekt) */
    var gif2On: Boolean
        get() = p.getBoolean("gif2On$ps", false)
        set(v) = p.edit().putBoolean("gif2On$ps", v).apply()
    var gif2Pct: Int
        get() = p.getInt("gif2Pct$ps", 50)
        set(v) = p.edit().putInt("gif2Pct$ps", v).apply()
    var gif3On: Boolean
        get() = p.getBoolean("gif3On$ps", false)
        set(v) = p.edit().putBoolean("gif3On$ps", v).apply()
    var gif3Pct: Int
        get() = p.getInt("gif3Pct$ps", 90)
        set(v) = p.edit().putInt("gif3Pct$ps", v).apply()
    val gifRepeats: List<Int>
        get() = listOfNotNull(gif2Pct.takeIf { gif2On }, gif3Pct.takeIf { gif3On })
    fun setClipName(kind: String, name: String?) {
        p.edit().putString("clipName_$kind$ps", name).apply()
    }

    /** Ringlicht automatisch regeln */
    var lightAuto: Boolean
        get() = bool("lightAuto", true)
        set(v) = p.edit().putBoolean("lightAuto", v).apply()

    /** Ringlicht-Helligkeit des Displays in Prozent */
    var lightLevel: Int
        get() = int("lightLevel", 100)
        set(v) = p.edit().putInt("lightLevel", v).apply()

    /** #9 Erkennungs-Engine: "whisper-base", "whisper-small" oder "vosk" */
    var subtitleEngine: String
        get() = p.getString("sub_engine", "whisper-small") ?: "whisper-small"
        set(v) = p.edit().putString("sub_engine", v).apply()

    /** Whisper: Sprechpausen überspringen (schneller) */
    var subtitleSkipPauses: Boolean
        get() = bool("sub_vad", true)
        set(v) = p.edit().putBoolean("sub_vad", v).apply()

    /** Wortliste des Projekts (Namen, Fachbegriffe) für die Untertitel-Erkennung */
    var subtitleWords: String
        get() = pS("subWords", "")
        set(v) = p.edit().putString("subWords$ps", v).apply()

    /** Bauchbinde: 2. und 3. Einblendung bei x % der Aufnahmezeit (je Projekt) */
    var lower2On: Boolean
        get() = pB("lower2On", false)
        set(v) = p.edit().putBoolean("lower2On$ps", v).apply()
    var lower2Pct: Int
        get() = pI("lower2Pct", 50)
        set(v) = p.edit().putInt("lower2Pct$ps", v).apply()
    var lower3On: Boolean
        get() = pB("lower3On", false)
        set(v) = p.edit().putBoolean("lower3On$ps", v).apply()
    var lower3Pct: Int
        get() = pI("lower3Pct", 90)
        set(v) = p.edit().putInt("lower3Pct$ps", v).apply()

    /** Prozentwerte der aktiven Zusatz-Einblendungen */
    val lowerRepeats: List<Int>
        get() = listOfNotNull(lower2Pct.takeIf { lower2On }, lower3Pct.takeIf { lower3On })

    /** Intro/Outro je Projekt einmalig aus dem alten gemeinsamen Clip übernehmen? */
    fun takeLegacyClip(kind: String): Boolean {
        val k = "legacyClip_${kind}$ps"
        if (p.getBoolean(k, false)) return false
        p.edit().putBoolean(k, true).apply()
        return true
    }

    private fun fixKey(front: Boolean, target: String) = "fix2_" + (if (front) "front_" else "rear_") + target


    /** Teleprompter-Text je Projekt (Logo) – ohne Übernahme von anderen Logos */
    var prompterText: String
        get() = p.getString("prompterText$ps", "") ?: ""
        set(v) = p.edit().putString("prompterText$ps", v).apply()

    /** Alter gemeinsamer Prompter-Text → einmalig dem aktuell gewählten Projekt geben */
    fun migratePrompterText() {
        if (p.getBoolean("prompterMigrated", false)) return
        val old = p.getString("prompterText", "") ?: ""
        val e = p.edit().putBoolean("prompterMigrated", true)
        if (old.isNotBlank() && !p.contains("prompterText$ps")) e.putString("prompterText$ps", old)
        e.apply()
    }

    /** Prompter startet automatisch mit der Aufnahme (nach dem Selbstauslöser-Countdown) */
    var prompterAutoStart: Boolean
        get() = bool("prompterAutoStart", true)
        set(v) = p.edit().putBoolean("prompterAutoStart", v).apply()

    /** Deckkraft des Prompter-Hintergrunds in % (0 = durchsichtig) */
    var prompterBgAlpha: Int
        get() = int("prompterBgAlpha", 100)
        set(v) = p.edit().putInt("prompterBgAlpha", v).apply()

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
        get() = pS("lowerName", "Kai")
        set(v) = p.edit().putString("lowerName$ps", v).apply()

    var lowerTitle: String
        get() = pS("lowerTitle", "Gründer · MoManufaktur")
        set(v) = p.edit().putString("lowerTitle$ps", v).apply()

    var lowerSeconds: Int
        get() = pI("lowerSeconds", 5)
        set(v) = p.edit().putInt("lowerSeconds$ps", v).apply()

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
        get() = p.getBoolean("introOutro$ps", false)  // je Projekt, ohne Übernahme von anderen Logos
        set(v) = p.edit().putBoolean("introOutro$ps", v).apply()

    /** Alter gemeinsamer Intro/Outro-Schalter → nur dem ersten Projekt geben */
    fun migrateIntroOutroTo(logo: String) {
        if (!p.contains("introOutro@$logo") && p.getBoolean("introOutro", false))
            p.edit().putBoolean("introOutro@$logo", true).apply()
    }

    /** #9 Untertitel automatisch einbrennen. */
    var subtitlesEnabled: Boolean
        get() = bool("subtitles", false)
        set(v) = p.edit().putBoolean("subtitles", v).apply()

    /** #6 Gestaltung der Bauchbinde */
    var lowerStyle: LowerThirdStyle
        get() {
            val d = LowerThirdStyle()
            return LowerThirdStyle(
                x = pF("lt_x", d.x), y = pF("lt_y", d.y), width = pF("lt_w", d.width),
                nameSize = pF("lt_ns", d.nameSize), titleSize = pF("lt_ts", d.titleSize),
                font = pI("lt_font", d.font),
                bgColor = pI("lt_bg", d.bgColor), bgAlpha = pF("lt_bga", d.bgAlpha),
                accentColor = pI("lt_acc", d.accentColor),
                nameColor = pI("lt_nc", d.nameColor), titleColor = pI("lt_tc", d.titleColor)
            )
        }
        set(v) = p.edit()
            .putFloat("lt_x$ps", v.x).putFloat("lt_y$ps", v.y).putFloat("lt_w$ps", v.width)
            .putFloat("lt_ns$ps", v.nameSize).putFloat("lt_ts$ps", v.titleSize).putInt("lt_font$ps", v.font)
            .putInt("lt_bg$ps", v.bgColor).putFloat("lt_bga$ps", v.bgAlpha).putInt("lt_acc$ps", v.accentColor)
            .putInt("lt_nc$ps", v.nameColor).putInt("lt_tc$ps", v.titleColor)
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
        get() = pB("copyrightOn", true)
        set(v) = p.edit().putBoolean("copyrightOn$ps", v).apply()

    var copyrightAuthor: String
        get() = pS("copyrightAuthor", "Kai")
        set(v) = p.edit().putString("copyrightAuthor$ps", v).apply()

    var copyrightText: String
        get() = pS("copyrightText", "© MoManufaktur")
        set(v) = p.edit().putString("copyrightText$ps", v).apply()

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

    /** #1–#3 Pro-Modus (0 bzw. -1 = automatisch) */
    var proIso: Int
        get() = int("proIso", 0)
        set(v) = p.edit().putInt("proIso", v).apply()

    var proShutterNs: Long
        get() = p.getLong("proShutter", 0L)
        set(v) = p.edit().putLong("proShutter", v).apply()

    /** 0 = Auto, 1..n = Preset-Index, >= 1000 = Kelvin */
    var proWb: Int
        get() = int("proWb", 0)
        set(v) = p.edit().putInt("proWb", v).apply()

    /** -1 = Autofokus, sonst 0 (unendlich) … 1 (nächste Distanz) */
    var proFocus: Float
        get() = float("proFocus", -1f)
        set(v) = p.edit().putFloat("proFocus", v).apply()

    var peaking: Boolean
        get() = bool("peaking", false)
        set(v) = p.edit().putBoolean("peaking", v).apply()

    /** #13 Intervall-Fotos in Sekunden (0 = aus), #14 Belichtungsreihe (0, 3, 5) */
    var photoInterval: Int
        get() = int("photoInterval", 0)
        set(v) = p.edit().putInt("photoInterval", v).apply()

    var bracket: Int
        get() = int("bracket", 0)
        set(v) = p.edit().putInt("bracket", v).apply()

    companion object {
        const val TARGET_PREVIEW = "preview"
        const val TARGET_VIDEO = "video"
        const val TARGET_PHOTO = "photo"
    }
}
