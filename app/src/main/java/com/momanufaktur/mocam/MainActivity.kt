package com.momanufaktur.mocam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // ---------------- Views ----------------
    private lateinit var previewView: PreviewView
    private lateinit var overlay: CameraOverlayView
    private lateinit var flashView: View
    private lateinit var countdownText: TextView
    private lateinit var recIndicator: TextView
    private lateinit var topBar: View
    private lateinit var bottomPanel: View
    private lateinit var btnFlash: TextView
    private lateinit var btnTimer: TextView
    private lateinit var btnLimit: TextView
    private lateinit var btnLower: TextView
    private lateinit var btnTimelapse: TextView
    private lateinit var btnFps: TextView
    private lateinit var btnBitrate: TextView
    private lateinit var btnVoice: TextView
    private lateinit var statusText: TextView
    private lateinit var voice: VoiceTrigger
    private lateinit var processingText: TextView
    private lateinit var post: PostProcessor
    private var recordingFolder = ""
    private lateinit var audioLevel: android.widget.ProgressBar
    private lateinit var shareBar: View
    private lateinit var shareButtons: LinearLayout
    private lateinit var btnGrid: TextView
    private lateinit var btnZebra: TextView
    private lateinit var btnPro: TextView
    private lateinit var btnBackground: TextView
    private lateinit var btnLight: TextView
    private lateinit var btnInterval: TextView
    private lateinit var btnBracket: TextView
    private lateinit var btnHisto: TextView
    private lateinit var btnLevel: TextView
    private lateinit var btnMarkers: TextView
    private lateinit var btnMarker: TextView
    private val markerTimes = mutableListOf<Long>()
    private var lastRecordedNs = 0L
    private lateinit var btnQuality: TextView
    private lateinit var btnAudio: TextView
    private lateinit var btnExtension: TextView
    private lateinit var btnLogo: TextView
    private lateinit var btnFormat: TextView
    private lateinit var btnSettings: TextView
    private lateinit var settingsPanel: View
    private lateinit var settingsContent: LinearLayout
    private lateinit var editorPanel: View
    private lateinit var topScroll: View
    private lateinit var topChips: LinearLayout
    private lateinit var topFlow: FlowLayout
    private lateinit var recentPanel: View
    private lateinit var recentContent: LinearLayout
    private lateinit var editorContent: LinearLayout
    private lateinit var exposureRow: View
    private lateinit var exposureBar: SeekBar
    private lateinit var exposureValue: TextView
    private lateinit var zoomChips: LinearLayout
    private lateinit var zoomBar: SeekBar
    private lateinit var zoomText: TextView
    private lateinit var filterBar: LinearLayout
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var shutter: View
    private lateinit var galleryThumb: ImageView
    private lateinit var btnPause: TextView
    private lateinit var btnSwitch: TextView
    private lateinit var logoPanel: View
    private lateinit var logoSwitch: SwitchCompat
    private lateinit var logoPhotosSwitch: SwitchCompat
    private lateinit var logoAlphaBar: SeekBar
    private lateinit var logoLibrary: LinearLayout
    private lateinit var btnPrompter: TextView
    private lateinit var prompterBox: View
    private lateinit var prompterView: TeleprompterView
    private lateinit var prompterSpeed: SeekBar
    private lateinit var btnPrompterPlay: TextView

    // ---------------- Zustand ----------------
    private lateinit var settings: Settings
    private val fxState = FxState()
    /** Vorschau: nur Filter. Video: Filter + Logo + Spiegel. Beide lesen fxState. */
    private lateinit var processor: GlEffectProcessor
    private lateinit var videoProcessor: GlEffectProcessor
    private lateinit var mainExec: Executor
    private val bg = Executors.newSingleThreadExecutor()
    private val sound = MediaActionSound()

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isPaused = false
    private var effectActive = false

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var videoMode = true
    private var flashMode = FLASH_OFF
    private var extensionMode = ExtensionMode.NONE
    private var availableExtensions = listOf(ExtensionMode.NONE)
    private var sensorRotation = 90
    private var logoBitmap: Bitmap? = null
    private var logoOriginal: Bitmap? = null
    private var lastMediaUri: Uri? = null
    private var lastMediaIsVideo = false
    private var countdown: CountDownTimer? = null
    private var zoomBarTracking = false
    private var filterViews = mutableListOf<TextView>()

    /** Bildformate (Breite:Höhe im Hochformat). */
    private class Format(val label: String, val w: Int, val h: Int, val wide: Boolean, val isDefault: Boolean = false)

    private val formats = listOf(
        Format("9:16", 9, 16, wide = true, isDefault = true),
        Format("3:4", 3, 4, wide = false, isDefault = true),
        Format("4:5", 4, 5, wide = false),
        Format("1:1", 1, 1, wide = false)
    )

    private fun currentFormat(): Format {
        val i = if (videoMode) settings.videoFormat else settings.photoFormat
        return formats.getOrElse(i) { if (videoMode) formats[0] else formats[1] }
    }

    private val qualities = listOf(
        Quality.UHD to "4K",
        Quality.FHD to "1080p",
        Quality.HD to "720p",
        Quality.SD to "480p"
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                toast("Ohne Kamera-Berechtigung kann MoCam nicht arbeiten.")
            }
        }

    private val pickIntro =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importClip(uri, introFile(), "intro")
        }
    private val pickOutro =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importClip(uri, outroFile(), "outro")
        }

    private val pickLogo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importLogo(uri)
        }

    /** Logo austauschen: neues Bild unter demselben Dateinamen → Projekt-Einstellungen bleiben erhalten. */
    private var replaceTarget: File? = null
    /** Projekt kopieren: Einstellungen dieses Logos gehen an das neu gewählte Logo. */
    private var copyFromLogo: String? = null
    private val pickCopyLogo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val from = copyFromLogo
            copyFromLogo = null
            if (uri != null && from != null) importLogo(uri, copyFrom = from)
        }
    private val pickReplacement =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val t = replaceTarget
            replaceTarget = null
            if (uri != null && t != null) importLogo(uri, t)
        }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) maybeShowDonateHint()

        mainExec = ContextCompat.getMainExecutor(this)
        settings = Settings(this)
        processor = GlEffectProcessor(fxState, burnLogo = false)
        videoProcessor = GlEffectProcessor(fxState, burnLogo = true)
        voice = VoiceTrigger(this) { word ->
            if (recording == null && countdown == null) {
                toast("🗣 „$word“")
                onShutter()
            }
        }
        post = PostProcessor(this).apply {
            onStatus = { msg ->
                processingText.text = msg ?: ""
                findViewById<View>(R.id.processingRow).visibility = if (msg == null) View.GONE else View.VISIBLE
            }
            onDone = { uri, msg ->
                if (uri != null) setLastMedia(uri, true)
                if (msg?.startsWith("Abgebrochen") == true) {
                    // gelöschtes Video nicht mehr als letzte Aufnahme anzeigen
                    lastMediaUri = null
                    galleryThumb.setImageDrawable(null)
                    shareBar.visibility = View.GONE
                }
                msg?.let { toast(it) }
            }
        }

        bindViews()
        applyTopBarMode()
        // Logo-Menü: Kopfzeile mit „←“ und „✓ Fertig“ (Titel wandert in die Zeile)
        run {
            val lp = logoPanel as LinearLayout
            val title = findViewById<TextView>(R.id.logoPanelTitle)
            lp.removeView(title)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(uiChip("←") { closeLogoPanel() }, wrapLp(10))
            row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(uiChip("✓ Fertig", selected = true) { closeLogoPanel() }, wrapLp(0))
            lp.addView(row, 0)
        }
        // Vorschau-Knopf im Logo-Menü (unter Titel und Registern)
        (logoPanel as LinearLayout).addView(peekChip(logoPanel), 2,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        findViewById<View>(R.id.btnRecent).setOnClickListener { openRecent() }
        restoreSettings()
        setupUi()
        loadSavedLogo()
        sound.load(MediaActionSound.SHUTTER_CLICK)

        // Seitenverhältnis der Vorschau kommt aus dem gewählten Format

        if (hasAllPermissions()) startCamera() else permissionLauncher.launch(requiredPermissions())
    }

    override fun onStop() {
        super.onStop()
        countdown?.cancel()
        countdownText.visibility = View.GONE
        prompterView.playing = false
        recording?.stop()
        voice.stop()
        sensorManager.unregisterListener(levelListener)
        overlay.removeCallbacks(histoTicker)
    }

    override fun onStart() {
        super.onStart()
        if (settings.voiceTrigger && recording == null) startVoice()
        statusTicker.run()
        applyLevelSensor()
        if (settings.histogram) histoTicker.run()
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.close()
        cameraProvider?.unbindAll()
        processor.release()
        voice.release()
        statusText.removeCallbacks(statusTicker)
        videoProcessor.release()
        sound.release()
        bg.shutdown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Lautstärketasten und Bluetooth-Fernauslöser (#16): die meisten senden Lautstärke, Enter oder Medientasten
        val shutterKeys = setOf(
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_SPACE
        )
        val editing = currentFocus is EditText
        if (keyCode in shutterKeys && !(editing && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.repeatCount == 0) onShutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // =====================================================================
    // Setup
    // =====================================================================

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        flashView = findViewById(R.id.flashView)
        countdownText = findViewById(R.id.countdownText)
        recIndicator = findViewById(R.id.recIndicator)
        topBar = findViewById(R.id.topBar)
        bottomPanel = findViewById(R.id.bottomPanel)
        btnFlash = findViewById(R.id.btnFlash)
        btnTimer = findViewById(R.id.btnTimer)
        btnLimit = findViewById(R.id.btnLimit)
        btnLower = findViewById(R.id.btnLower)
        btnTimelapse = findViewById(R.id.btnTimelapse)
        btnFps = findViewById(R.id.btnFps)
        btnBitrate = findViewById(R.id.btnBitrate)
        btnVoice = findViewById(R.id.btnVoice)
        statusText = findViewById(R.id.statusText)
        processingText = findViewById(R.id.processingText)
        findViewById<View>(R.id.btnCancelProcessing).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Bearbeitung abbrechen?")
                .setMessage("Die Bearbeitung wird gestoppt und das gesamte Video wird gelöscht. Das lässt sich nicht rückgängig machen.")
                .setNegativeButton("Weiter bearbeiten", null)
                .setPositiveButton("Abbrechen & löschen") { _, _ -> post.cancelCurrent() }
                .show()
        }
        audioLevel = findViewById(R.id.audioLevel)
        shareBar = findViewById(R.id.shareBar)
        shareButtons = findViewById(R.id.shareButtons)
        btnGrid = findViewById(R.id.btnGrid)
        btnZebra = findViewById(R.id.btnZebra)
        btnPro = findViewById(R.id.btnPro)
        btnLight = findViewById(R.id.btnLight)
        btnLight.setOnClickListener {
            if (isFrontCam) setLightMode(!lightMode) else toggleTorch()
        }
        btnInterval = findViewById(R.id.btnInterval)
        btnBracket = findViewById(R.id.btnBracket)
        btnHisto = findViewById(R.id.btnHisto)
        btnLevel = findViewById(R.id.btnLevel)
        btnMarkers = findViewById(R.id.btnMarkers)
        btnMarker = findViewById(R.id.btnMarker)
        btnQuality = findViewById(R.id.btnQuality)
        btnAudio = findViewById(R.id.btnAudio)
        btnExtension = findViewById(R.id.btnExtension)
        btnLogo = findViewById(R.id.btnLogo)
        btnFormat = findViewById(R.id.btnFormat)
        btnSettings = findViewById(R.id.btnSettings)
        settingsPanel = findViewById(R.id.settingsPanel)
        settingsContent = findViewById(R.id.settingsContent)
        editorPanel = findViewById(R.id.editorPanel)
        topScroll = findViewById(R.id.topScroll)
        topChips = findViewById(R.id.topChips)
        topFlow = findViewById(R.id.topFlow)
        recentPanel = findViewById(R.id.recentPanel)
        recentContent = findViewById(R.id.recentContent)
        editorContent = findViewById(R.id.editorContent)
        exposureRow = findViewById(R.id.exposureRow)
        exposureBar = findViewById(R.id.exposureBar)
        exposureValue = findViewById(R.id.exposureValue)
        zoomChips = findViewById(R.id.zoomChips)
        zoomBar = findViewById(R.id.zoomBar)
        zoomText = findViewById(R.id.zoomText)
        filterBar = findViewById(R.id.filterBar)
        modePhoto = findViewById(R.id.modePhoto)
        modeVideo = findViewById(R.id.modeVideo)
        shutter = findViewById(R.id.shutter)
        galleryThumb = findViewById(R.id.galleryThumb)
        btnPause = findViewById(R.id.btnPause)
        btnSwitch = findViewById(R.id.btnSwitch)
        logoPanel = findViewById(R.id.logoPanel)
        logoSwitch = findViewById(R.id.logoSwitch)
        logoPhotosSwitch = findViewById(R.id.logoPhotosSwitch)
        logoAlphaBar = findViewById(R.id.logoAlphaBar)
        logoLibrary = findViewById(R.id.logoLibrary)
        btnPrompter = findViewById(R.id.btnPrompter)
        btnBackground = findViewById(R.id.btnBackground)
        btnBackground.setOnClickListener { setBackgroundEnabled(!settings.bgEnabled) }
        prompterBox = findViewById(R.id.prompterBox)
        prompterView = findViewById(R.id.prompterView)
        prompterSpeed = findViewById(R.id.prompterSpeed)
        btnPrompterPlay = findViewById(R.id.btnPrompterPlay)
    }

    private fun restoreSettings() {
        videoMode = settings.videoMode
        processor.filter = Filters.ALL.getOrElse(settings.filterIndex) { Filters.ALL[0] }
        processor.logoEnabled = settings.logoEnabled
        settings.logoContext = logoContextKey()
        loadLogoContextValues()
        overlay.showGrid = settings.grid
    }

    private fun setupUi() {
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        // Gesten auf der Vorschau
        overlay.onTap = { x, y -> focusAt(x, y) }
        overlay.onLongPress = { x, y -> lockAt(x, y) }
        fxState.zebra = settings.zebra
        btnZebra.setOnClickListener {
            settings.zebra = !settings.zebra
            fxState.zebra = settings.zebra
            updateUi()
        }
        btnHisto.setOnClickListener {
            settings.histogram = !settings.histogram
            if (settings.histogram) histoTicker.run() else overlay.histogram = null
            updateUi()
        }
        btnLevel.setOnClickListener {
            settings.level = !settings.level
            applyLevelSensor()
            updateUi()
        }
        btnMarkers.setOnClickListener {
            settings.markers = !settings.markers
            if (settings.markers) toast("Während der Aufnahme 🔖 tippen, um Stellen zu markieren")
            updateUi()
        }
        btnMarker.setOnClickListener { addMarker() }
        fxState.peaking = settings.peaking
        btnPro.setOnClickListener { openProEditor() }
        btnInterval.setOnClickListener {
            if (intervalRunning) return@setOnClickListener
            settings.photoInterval = when (settings.photoInterval) { 0 -> 2; 2 -> 5; 5 -> 10; 10 -> 30; 30 -> 60; else -> 0 }
            if (settings.photoInterval > 0) toast("Auslöser startet eine Fotoserie alle ${settings.photoInterval} s – nochmal drücken stoppt")
            updateUi()
        }
        btnBracket.setOnClickListener {
            settings.bracket = when (settings.bracket) { 0 -> 3; 3 -> 5; else -> 0 }
            if (settings.bracket > 0) toast("Belichtungsreihe: ${settings.bracket} Fotos mit je 1 EV Abstand")
            updateUi()
        }
        // #13 Serienbild: Auslöser gedrückt halten (Foto)
        shutter.setOnLongClickListener {
            if (videoMode || recording != null) return@setOnLongClickListener false
            burstActive = true
            burstCount = 0
            burstNext()
            true
        }
        shutter.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_UP || e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                if (burstActive) {
                    burstActive = false
                    toast("Serienbild: $burstCount Fotos")
                    recIndicator.visibility = View.GONE
                }
            }
            false
        }
        overlay.onDoubleTap = { switchCamera() }
        overlay.onZoomScale = { factor ->
            camera?.let { cam ->
                val zs = cam.cameraInfo.zoomState.value
                if (zs != null) {
                    cam.cameraControl.setZoomRatio(
                        (zs.zoomRatio * factor).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                    )
                }
            }
        }
        overlay.onLogoChanged = { cx, cy, w ->
            processor.logoCx = cx
            processor.logoCy = cy
            processor.logoW = w
            settings.logoCx = cx
            settings.logoCy = cy
            settings.logoW = w
        }

        // Obere Leiste
        btnFlash.setOnClickListener { cycleFlash() }
        btnTimelapse.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.timelapse = when (settings.timelapse) { 0 -> 5; 5 -> 10; 10 -> 30; 30 -> 60; 60 -> 120; else -> 0 }
            if (settings.timelapse > 0) {
                toast("Zeitraffer ${settings.timelapse}x – 1 Minute wird zu ${60 / settings.timelapse.coerceAtLeast(1)} s (ohne Ton)")
            }
            updateUi()
        }
        btnFps.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.fps = when (settings.fps) { 0 -> 24; 24 -> 25; 25 -> 30; 30 -> 60; else -> 0 }
            updateUi()
            bindCamera()
        }
        btnBitrate.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.bitrateMbps = when (settings.bitrateMbps) { 0 -> 8; 8 -> 16; 16 -> 24; 24 -> 40; 40 -> 60; else -> 0 }
            updateUi()
            bindCamera()
        }
        btnVoice.setOnClickListener {
            settings.voiceTrigger = !settings.voiceTrigger
            if (settings.voiceTrigger) {
                if (!startVoice()) {
                    settings.voiceTrigger = false
                    toast("Bitte zuerst ein Sprachmodell laden (⚙️ → Untertitel einstellen)")
                } else {
                    toast("Sprachauslöser an: „Foto“ oder „Aufnahme“ sagen")
                }
            } else voice.stop()
            updateUi()
        }
        btnLower.setOnClickListener {
            settings.lowerEnabled = !settings.lowerEnabled
            if (settings.lowerEnabled) {
                applyLowerThird()
                previewLowerThird()
                toast("Bauchbinde an – Text in ⚙️ ändern")
            }
            updateUi()
        }
        applyLowerThird()
        btnLimit.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.timeLimit = when (settings.timeLimit) { 0 -> 15; 15 -> 30; 30 -> 60; 60 -> 90; else -> 0 }
            if (settings.timeLimit > 0) {
                val cut = introOutroSeconds()
                toast(if (cut > 0) "Endvideo ${settings.timeLimit} s: Aufnahme stoppt nach ${effectiveLimit()} s (Intro/Outro ${cut} s)"
                      else "Video stoppt automatisch nach ${settings.timeLimit} s")
            }
            updateUi()
        }
        findViewById<View>(R.id.btnLogoFolder).setOnClickListener { chooseFolderForLogo() }
        galleryThumb.setOnLongClickListener {
            lastMediaUri?.let { showShareBar(it, lastMediaIsVideo) }
            true
        }
        buildShareButtons()
        btnTimer.setOnClickListener {
            settings.timer = when (settings.timer) { 0 -> 3; 3 -> 10; else -> 0 }
            updateUi()
        }
        btnGrid.setOnClickListener {
            settings.grid = !settings.grid
            overlay.showGrid = settings.grid
            updateUi()
        }
        btnQuality.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.qualityIndex = (settings.qualityIndex + 1) % qualities.size
            updateUi()
            bindCamera()
        }
        btnAudio.setOnClickListener {
            if (recording != null) return@setOnClickListener
            settings.audio = !settings.audio
            updateUi()
        }
        btnExtension.setOnClickListener {
            val idx = availableExtensions.indexOf(extensionMode)
            extensionMode = availableExtensions[(idx + 1) % availableExtensions.size]
            updateUi()
            bindCamera()
        }
        btnLogo.setOnClickListener {
            val on = !settings.logoEnabled
            settings.logoEnabled = on
            processor.logoEnabled = on
            overlay.showLogo = on
            logoSwitch.isChecked = on
            if (on && logoBitmap == null) toast("Noch kein Logo gewählt – ⚙️ → Logo einstellen")
            updateUi()
        }
        btnSettings.setOnClickListener {
            if (settingsPanel.visibility == View.VISIBLE) closeSettings()
        if (editorPanel.visibility == View.VISIBLE) {
            editorPanel.visibility = View.GONE
            overlay.lowerPinned = false
            overlay.subPreviewText = null
            bottomPanel.visibility = View.VISIBLE
        } else openSettings()
        }
        btnFormat.setOnClickListener {
            if (recording != null) return@setOnClickListener
            val next = (formats.indexOf(currentFormat()) + 1) % formats.size
            if (videoMode) settings.videoFormat = next else settings.photoFormat = next
            updateUi()
            bindCamera()
        }

        // Belichtung
        exposureBar.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val cam = camera ?: return
                val st = cam.cameraInfo.exposureState
                val index = st.exposureCompensationRange.lower + progress
                if (fromUser) cam.cameraControl.setExposureCompensationIndex(index)
                val ev = index * st.exposureCompensationStep.toFloat()
                exposureValue.text = String.format("%+.1f EV", ev)
            }
        })

        // Zoom
        zoomBar.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) camera?.cameraControl?.setLinearZoom(progress / 1000f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { zoomBarTracking = true }
            override fun onStopTrackingTouch(sb: SeekBar) { zoomBarTracking = false }
        })

        // Filter
        buildFilterBar()

        // Modus
        modePhoto.setOnClickListener { setVideoMode(false) }
        modeVideo.setOnClickListener { setVideoMode(true) }

        // Auslöser & Co.
        shutter.setOnClickListener { onShutter() }
        btnSwitch.setOnClickListener { switchCamera() }
        btnPause.setOnClickListener { togglePause() }
        galleryThumb.setOnClickListener { openLastMedia() }

        // Logo-Panel
        logoSwitch.isChecked = settings.logoEnabled
        logoSwitch.setOnCheckedChangeListener { _, checked ->
            settings.logoEnabled = checked
            overlay.showLogo = checked
            updateUi()
            processor.logoEnabled = checked
        }
        logoPhotosSwitch.isChecked = settings.logoOnPhotos
        logoPhotosSwitch.setOnCheckedChangeListener { _, checked -> settings.logoOnPhotos = checked }
        logoAlphaBar.progress = (settings.logoAlpha * 100).roundToInt()
        logoAlphaBar.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val a = max(progress, 5) / 100f
                processor.logoAlpha = a
                overlay.logoAlpha = a
                settings.logoAlpha = a
            }
        })
        findViewById<View>(R.id.btnPickLogo).setOnClickListener { pickLogo.launch("image/*") }
        findViewById<View>(R.id.btnLogoDone).setOnClickListener { closeLogoPanel() }
        findViewById<View>(R.id.btnLogoCopyAll).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Für alle Register übernehmen?")
                .setMessage("Logo, Position, Größe, Deckkraft und Drehung von „${logoContextName()}“ gelten danach für Foto und Video, Rück- und Frontkamera.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    settings.copyLogoToAllContexts()
                    toast("Für alle Register übernommen")
                }
                .show()
        }
        findViewById<View>(R.id.btnLogoReset).setOnClickListener {
            overlay.logoCx = 0.80f
            overlay.logoCy = 0.10f
            overlay.logoW = 0.30f
            overlay.onLogoChanged?.invoke(0.80f, 0.10f, 0.30f)
            overlay.invalidate()
        }
        findViewById<View>(R.id.btnDragRotate).setOnClickListener {
            val v = settings.logoDragFix
            settings.logoDragFix = (v / 4) * 4 + (v % 4 + 1) % 4
            applyDragFix()
        }
        findViewById<View>(R.id.btnDragMirror).setOnClickListener {
            settings.logoDragFix = (settings.logoDragFix + 4) % 8
            applyDragFix()
        }
        findViewById<View>(R.id.btnLogoRotate).setOnClickListener {
            settings.logoRotation = (settings.logoRotation + 90) % 360
            logoOriginal?.let { applyLogo(it) }
        }

        setupPrompter()
        fxState.segmenter = Background.liveSegmenter()
        applyBackground()
        updateUi()
    }

    private fun buildFilterBar() {
        filterBar.removeAllViews()
        filterViews.clear()
        val pad = dp(12)
        Filters.ALL.forEachIndexed { i, f ->
            val tv = TextView(this).apply {
                text = f.name
                textSize = 13f
                setPadding(pad, dp(7), pad, dp(7))
                gravity = Gravity.CENTER
                setOnClickListener { selectFilter(i) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            filterBar.addView(tv, lp)
            filterViews += tv
        }
        highlightFilter()
    }

    private fun selectFilter(i: Int) {
        settings.filterIndex = i
        processor.filter = Filters.ALL[i]
        highlightFilter()
        if (!effectActive && i != 0) {
            toast("Live-Vorschau in diesem Modus ohne Filter – wird beim Speichern angewendet.")
        }
    }

    private fun highlightFilter() {
        val sel = settings.filterIndex
        filterViews.forEachIndexed { i, tv ->
            tv.setBackgroundResource(if (i == sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            tv.setTextColor(if (i == sel) Color.BLACK else Color.WHITE)
        }
    }

    // =====================================================================
    // Kamera
    // =====================================================================

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "CameraProvider", e)
                toast("Kamera konnte nicht gestartet werden.")
                return@addListener
            }
            cameraProvider = provider
            val emFuture = ExtensionsManager.getInstanceAsync(this, provider)
            emFuture.addListener({
                extensionsManager = try { emFuture.get() } catch (e: Exception) { null }
                bindCamera()
            }, mainExec)
        }, mainExec)
    }

    private fun baseSelector() = CameraSelector.Builder().requireLensFacing(lensFacing).build()

    private class FxEffect(targets: Int, p: GlEffectProcessor) : CameraEffect(
        targets, p.executor, p, Consumer<Throwable> { t -> Log.e(TAG, "Effekt-Fehler", t) }
    )

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        // Während der Aufnahme nur Kamerawechsel erlaubt – dabei dieselbe VideoCapture weiterverwenden
        val keepVideo = recording != null
        if (keepVideo && (!videoMode || videoCapture == null)) return
        val runningVc = if (keepVideo) videoCapture else null

        camera?.cameraInfo?.zoomState?.removeObservers(this)
        provider.unbindAll()
        camera = null
        imageCapture = null
        videoCapture = null

        if (!provider.hasCamera(baseSelector())) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        }
        val base = baseSelector()

        // Sensor-Drehung für das Logo-Mapping
        sensorRotation = try {
            provider.availableCameraInfos.firstOrNull { base.filter(listOf(it)).isNotEmpty() }
                ?.sensorRotationDegrees ?: 90
        } catch (e: Exception) { 90 }
        overlay.mirrorX = false
        processor.isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
        processor.frontMirror = settings.frontMirror
        applyCameraFixes()
        val fmt = currentFormat()
        overlay.contentAspect = fmt.w.toFloat() / fmt.h
        applyRotation()

        // Verfügbare Hersteller-Modi (Huawei: HDR, Nacht, Bokeh, Beauty …)
        val em = extensionsManager
        availableExtensions = listOf(ExtensionMode.NONE) + listOf(
            ExtensionMode.AUTO, ExtensionMode.HDR, ExtensionMode.NIGHT,
            ExtensionMode.BOKEH, ExtensionMode.FACE_RETOUCH
        ).filter { mode ->
            try { em?.isExtensionAvailable(base, mode) == true } catch (e: Exception) { false }
        }
        if (extensionMode !in availableExtensions) extensionMode = ExtensionMode.NONE

        // Format: 9:16 nutzt den 16:9-Sensorstrom, alle anderen den volleren 4:3-Strom
        val ratioStrategy = if (fmt.wide)
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val viewPort = ViewPort.Builder(Rational(fmt.w, fmt.h), Surface.ROTATION_0).build()

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build())
        attachIsoReader(previewBuilder) // aktuellen ISO-Wert mitlesen (Ringlicht-Automatik)
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        /** Baut eine UseCaseGroup mit/ohne Effekt und mit/ohne Bildausschnitt. */
        fun group(effectTargets: Int, withViewPort: Boolean, vararg cases: androidx.camera.core.UseCase): UseCaseGroup {
            val b = UseCaseGroup.Builder()
            cases.forEach { b.addUseCase(it) }
            if (effectTargets != 0) b.addEffect(FxEffect(effectTargets, processor))
            if (withViewPort) b.setViewPort(viewPort)
            return b.build()
        }

        var bound: Camera? = null
        if (videoMode) {
            fun makeVc(withFps: Boolean): VideoCapture<Recorder> {
                val q = qualities[settings.qualityIndex.coerceIn(0, qualities.size - 1)].first
                val rb = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                    .setAspectRatio(if (fmt.wide) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3)
                // #10 Bitrate
                if (settings.bitrateMbps > 0) rb.setTargetVideoEncodingBitRate(settings.bitrateMbps * 1_000_000)
                val vb = VideoCapture.Builder(rb.build())
                // #9 Bildrate
                if (withFps && settings.fps > 0) vb.setTargetFrameRate(android.util.Range(settings.fps, settings.fps))
                return vb.build()
            }
            var vc = runningVc ?: makeVc(true)
            videoCapture = vc
            // Getrennte Wege: Vorschau nur mit Filter, Aufnahme mit Filter + Logo
            fun splitGroup(withViewPort: Boolean): UseCaseGroup {
                val b = UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc)
                    .addEffect(FxEffect(CameraEffect.PREVIEW, processor))
                    .addEffect(FxEffect(CameraEffect.VIDEO_CAPTURE, videoProcessor))
                if (withViewPort) b.setViewPort(viewPort)
                return b.build()
            }
            fun videoOnlyGroup(withViewPort: Boolean): UseCaseGroup {
                val b = UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc)
                    .addEffect(FxEffect(CameraEffect.VIDEO_CAPTURE, videoProcessor))
                if (withViewPort) b.setViewPort(viewPort)
                return b.build()
            }

            bound = tryBind(provider, base, splitGroup(true))
                ?: tryBind(provider, base, splitGroup(false))
                ?: tryBind(provider, base, videoOnlyGroup(true))
                ?: tryBind(provider, base, videoOnlyGroup(false))
            if (bound == null && runningVc == null && settings.fps > 0) {
                // Bildrate nicht unterstützt -> ohne feste Bildrate erneut versuchen
                toast("${settings.fps} fps wird von dieser Kamera nicht unterstützt – automatisch")
                vc = makeVc(false)
                videoCapture = vc
                bound = tryBind(provider, base, splitGroup(true)) ?: tryBind(provider, base, splitGroup(false))
            }
            effectActive = bound != null
            if (bound == null) {
                toast("Filter/Logo-Effekt wird von diesem Gerät nicht unterstützt.")
                bound = tryBind(provider, base, group(0, true, preview, vc))
                    ?: tryBind(provider, base, group(0, false, preview, vc))
            }
        } else {
            val ic = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(imageFlashMode())
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(4000, 3000),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        ).build()
                )
                .build()
            imageCapture = ic

            val selector = if (extensionMode != ExtensionMode.NONE && em != null)
                em.getExtensionEnabledCameraSelector(base, extensionMode) else base

            bound = tryBind(provider, selector, group(CameraEffect.PREVIEW, true, preview, ic))
                ?: tryBind(provider, selector, group(CameraEffect.PREVIEW, false, preview, ic))
            effectActive = bound != null
            if (bound == null) {
                bound = tryBind(provider, selector, group(0, true, preview, ic))
                    ?: tryBind(provider, selector, group(0, false, preview, ic))
                if (bound != null && extensionMode != ExtensionMode.NONE) {
                    toast("${extensionName(extensionMode)}: Live-Filter aus, Filter/Logo kommen beim Speichern dazu.")
                }
            }
            if (bound == null && selector != base) {
                extensionMode = ExtensionMode.NONE
                bound = tryBind(provider, base, group(0, false, preview, ic))
            }
        }

        camera = bound
        if (bound == null) {
            toast("Kamera konnte nicht verbunden werden.")
            return
        }
        setupCameraControls(bound)
        overlay.drawLogoPreview = !effectActive
        applyProSettings()
        applyPreviewMirror()
        applyTorch()
        updateUi()
    }

    // ---------------- Kameralicht (Taschenlampe) der Rückkamera ----------------
    private var torchOn = false

    private fun hasTorch() = camera?.cameraInfo?.hasFlashUnit() == true

    private fun toggleTorch() {
        if (!hasTorch()) { toast("Diese Kamera hat kein Licht."); return }
        torchOn = !torchOn
        applyTorch()
        updateUi()
    }

    /** Licht nach jedem Kamera-/Moduswechsel wiederherstellen (beim Neuverbinden geht es sonst aus). */
    private fun applyTorch() {
        val cam = camera ?: return
        if (isFrontCam || !cam.cameraInfo.hasFlashUnit()) return
        cam.cameraControl.enableTorch(torchOn)
    }

    // =====================================================================
    // Grundeinstellungen: Drehen/Spiegeln pro Kamera für Vorschau, Video und Foto
    // =====================================================================

    private val isFrontCam get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    /** Spiegel der Frontkamera in der Vorschau: einfach die Anzeige umdrehen (Logo-Ebene bleibt lesbar). */
    private fun applyPreviewMirror() {
        previewView.scaleX = if (isFrontCam && settings.frontMirror) -1f else 1f
    }

    private fun applyCameraFixes() {
        switchLogoContext()
        processor.isFront = isFrontCam
        processor.frontMirror = settings.frontMirror
        processor.previewFix = settings.getFix(isFrontCam, Settings.TARGET_PREVIEW)
        processor.videoFix = settings.getFix(isFrontCam, Settings.TARGET_VIDEO)
        processor.videoFlip = settings.getVideoFlip(isFrontCam)
    }

    private fun fixLabel(v: Int): String {
        val rot = (v % 4) * 90
        return "$rot°" + if (v >= 4) " · gespiegelt" else ""
    }

    private fun openSettings() {
        if (logoPanel.visibility == View.VISIBLE) closeLogoPanel()
        buildSettings()
        bottomPanel.visibility = View.GONE
        settingsPanel.visibility = View.VISIBLE
    }

    private fun closeSettings() {
        settingsPanel.visibility = View.GONE
        bottomPanel.visibility = View.VISIBLE
    }

    private fun buildSettings() {
        settingsContent.removeAllViews()

        fun text(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
            TextView(this).apply {
                text = t
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        fun chip(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.chip_bg)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener { onClick() }
        }

        /** Großer Menüeintrag: Titel + Kurzinfo, öffnet ein Untermenü. */
        fun entry(title: String, info: String, onClick: () -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { onClick() }
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(title, 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("›", 20f, getColor(R.color.accent), true))
            box.addView(row)
            if (info.isNotEmpty()) box.addView(text(info, 12f, Color.argb(190, 255, 255, 255)))
            settingsContent.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        settingsContent.addView(text("⚙️ Einstellungen", 17f, getColor(R.color.accent), true))

        // 1. Projekteinstellungen (alles, was zum gewählten Logo gehört)
        val logo = settings.selectedLogo
        entry("📁 Projekteinstellungen",
            if (logo == null) "Noch kein Logo gewählt"
            else "Projekt: ${projectName(logo)} · Ordner: ${folderLabel(settings.folderForLogo(logo))}") {
            openProjectEditor()
        }

        // 2. Untertitel
        entry("💬 Untertitel",
            (if (settings.subtitlesEnabled) "An" else "Aus") + " · " +
                (Subtitles.ENGINES.firstOrNull { it.first == settings.subtitleEngine }?.second ?: "") + " · " +
                subModeName(settings.subtitleMode) +
                (if (Subtitles.isModelReady(this, settings.subtitleLang, settings.subtitleEngine)) "" else " · Sprachmodell fehlt")) {
            openSubtitleEditor()
        }

        // Hintergrund ersetzen
        entry("🖼 Hintergrund", (if (settings.bgEnabled) "An · " else "Aus · ") +
            listOf("", "Weichzeichnen", "Foto", "Video")[settings.bgType.coerceIn(1, 3)]) {
            openBackgroundEditor()
        }

        // 3. Mikrofon
        entry("🎤 Mikrofon",
            if (settings.micMode == "bluetooth") "Bluetooth-Headset" else "Automatisch (intern / USB)") {
            openMicEditor()
        }

        // 4. Ansicht
        entry("🧭 Ansicht", if (settings.topBarWrap) "Alle Buttons sichtbar" else "Buttonleiste scrollbar") {
            openViewEditor()
        }

        // 5. Grundeinstellungen
        entry("🔧 Grundeinstellungen", "Rück- & Frontkamera: Spiegeln, Ausrichtung von Logo und Videobild") {
            openBaseEditor()
        }

        // 6. Backup
        entry("💾 Backup", settings.lastBackup.let { if (it == 0L) "Noch kein Backup erstellt"
            else "Letztes Backup: " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(it)) }) {
            openBackupEditor()
        }

        // 7. Unterstützen
        settingsContent.addView(text("💛 Unterstützen", 14f, getColor(R.color.accent), true).apply { setPadding(0, dp(18), 0, 0) })
        settingsContent.addView(text("MoCam ist Open Source, kostenlos und werbefrei – das Projekt lebt von Spenden.",
            12f, Color.argb(200, 255, 255, 255)))
        val donateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        donateRow.addView(chip("💛 Spenden") {}.also {
            it.textSize = 15f
            it.setPadding(dp(22), dp(20), dp(22), dp(20)) // doppelt so hoch wie die übrigen Knöpfe
            it.gravity = Gravity.CENTER
            attachDonateGesture(it)
        },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        donateRow.addView(chip("GitHub") { openUrl(GITHUB_URL) })
        settingsContent.addView(donateRow)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }
        buttons.addView(chip("Zurücksetzen") { confirmReset() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        buttons.addView(chip("✓ Fertig") { closeSettings() })
        settingsContent.addView(buttons)

        // Versionsnummer ganz unten
        settingsContent.addView(text("MoCam Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.COMMIT})",
            11f, Color.argb(140, 255, 255, 255)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    /** Lesbarer Projektname aus dem Logo-Dateinamen. */
    private fun projectName(logo: String) =
        logo.substringBeforeLast('.').replace('_', ' ').let { if (it.length > 28) it.take(26) + "…" else it }

    /** Zurücksetzen mit Rückfrage: Ausrichtung von Logo und Videobild beider Kameras auf Standard. */
    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Zurücksetzen?")
            .setMessage("Die Ausrichtung von Logo und Videobild wird für Rück- und Frontkamera auf den Standard zurückgesetzt. " +
                "Projekte, Logos, Bauchbinden und alle anderen Einstellungen bleiben erhalten.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Zurücksetzen") { _, _ ->
                listOf(false, true).forEach { front ->
                    settings.setFix(front, Settings.TARGET_VIDEO, 0)
                    settings.setVideoFlip(front, front)
                }
                applyCameraFixes()
                toast("Zurückgesetzt")
                buildSettings()
            }
            .show()
    }

    // ---------------- Untermenüs ----------------

    /** Projekteinstellungen: Ordner, Logo-Position, Bauchbinde, Copyright, Intro/Outro – alles je Logo. */
    /** Großer Menüeintrag im Editor (wie auf der Einstellungs-Hauptseite): Titel, Kurzinfo, öffnet ein Untermenü. */
    private fun editorEntry(title: String, info: String, onClick: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.chip_bg)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(uiText(title, 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(uiText("›", 20f, getColor(R.color.accent), true))
        box.addView(row)
        if (info.isNotEmpty()) box.addView(uiText(info, 12f, Color.argb(190, 255, 255, 255)))
        editorContent.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    /** Neu zeichnen, wenn sich im offenen Projekt-Untermenü etwas von außen ändert (Ordner, Clip gewählt). */
    private var projectRefresh: (() -> Unit)? = null

    /** Öffnet ein Untermenü der Projekteinstellungen; „←“/„✓“ führen zurück zur Projekt-Übersicht. */
    private fun projectSub(open: () -> Unit) {
        editorReturn = { openProjectEditor() }
        projectRefresh = null
        open()
    }

    private fun openLogoPanelFromProject() {
        editorPanel.visibility = View.GONE
        projectEditorOpen = false
        projectRefresh = null
        logoPanelToProject = true
        openLogoPanel()
    }

    private fun shortText(t: String, max: Int = 70) =
        t.replace('\n', ' ').trim().let { if (it.length > max) it.take(max) + " …" else it }

    /** Projekteinstellungen: Übersicht mit Einträgen, jeder öffnet sein Untermenü (wie die Einstellungs-Hauptseite). */
    private fun openProjectEditor() {
        openEditor("📁 Projekteinstellungen") {
            projectEditorOpen = true
            projectRefresh = { openProjectEditor() }
            val logo = settings.selectedLogo
            if (logo == null) {
                editorContent.addView(uiText("Ein Projekt gehört immer zu einem Logo. Wähle zuerst ein Logo – " +
                    "danach kannst du Speicherordner, Logo-Position, Bauchbinde, Copyright und Intro/Outro für dieses Projekt einstellen.",
                    13f, Color.argb(220, 255, 255, 255)))
                editorContent.addView(uiChip("🏷 Logo wählen …") { openLogoPanelFromProject() }, wrapLp(0))
                return@openEditor
            }
            editorContent.addView(uiText("Projekt: ${projectName(logo)}", 15f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
            editorContent.addView(uiText("Anderes Logo wählen = anderes Projekt. Alles hier gilt nur für dieses Projekt.",
                11f, Color.argb(170, 255, 255, 255)))

            editorEntry("🏷 Logo", if (animFor(logo) != null) "Animiert · Position, Größe, Wiederholungen" else "Position, Größe, Drehung") {
                projectSub { openProjectLogoEditor() }
            }
            editorEntry("📂 Speicherort", "Ordner: ${folderLabel(settings.folderForLogo(logo))}") {
                projectSub { openProjectFolderEditor() }
            }
            editorEntry("🪪 Bauchbinde", (if (settings.lowerEnabled) "An · " else "Aus · ") + "${settings.lowerName} · ${settings.lowerTitle}") {
                projectSub { openLowerThirdEditor() }
            }
            val intro = introFile().exists(); val outro = outroFile().exists()
            editorEntry("🎬 Intro & Outro", (if (settings.introOutroEnabled) "An" else "Aus") + " · " +
                listOfNotNull("Intro".takeIf { intro }, "Outro".takeIf { outro }).joinToString(" + ").ifEmpty { "keine Clips" }) {
                projectSub { openProjectClipsEditor() }
            }
            val pt = settings.prompterText
            editorEntry("📜 Teleprompter-Text", if (pt.isBlank()) "Noch kein Text" else shortText(pt)) {
                editPrompterText { openProjectEditor() }
            }
            val words = settings.subtitleWords
            editorEntry("📝 Wortliste für Untertitel", if (words.isBlank()) "Noch keine Begriffe" else shortText(words)) {
                projectSub { openProjectWordsEditor() }
            }
            editorEntry("©️ Copyright",
                if (settings.copyrightEnabled) "An · ${settings.copyrightAuthor} · ${settings.copyrightText}" else "Aus") {
                projectSub { openCopyrightEditor() }
            }
        }
    }

    private fun openProjectLogoEditor() {
        val logo = settings.selectedLogo ?: return openProjectEditor()
        openEditor("🏷 Logo") {
            editorContent.addView(uiText("Position, Größe, Deckkraft und Drehung je Foto/Video und Rück-/Frontkamera. " +
                "Lange auf ein Logo drücken = austauschen oder löschen.", 12f, Color.argb(200, 255, 255, 255)))
            val logoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
            logoRow.addView(uiChip("🏷 Logo einstellen …") { openLogoPanelFromProject() }, wrapLp(8))
            logoRow.addView(uiChip("Logo / Projekt wechseln …") { openLogoPanelFromProject() }, wrapLp(0))
            editorContent.addView(logoRow)
            if (animFor(logo) != null) {
                uiSection("✨ Animiertes Logo")
                editorContent.addView(uiText(
                    "Die Animation läuft am Anfang jeder Videoaufnahme. Zusätzlich kann sie zu einem Zeitpunkt in % der " +
                        "Aufnahmezeit (ohne Intro/Outro) erneut ablaufen." +
                        if (settings.timeLimit > 0) " Aufnahmezeit jetzt ${effectiveLimit()} s."
                        else " ⚠ Dafür muss ein Zeitlimit (⏳) eingestellt sein – sonst steht die Länge vorher nicht fest.",
                    11f, Color.argb(170, 255, 255, 255)))
                fun gifRow(n: Int, on: Boolean, pct: Int, setOn: (Boolean) -> Unit, setPct: (Int) -> Unit) {
                    editorContent.addView(SwitchCompat(this).apply {
                        text = "$n. Abspielen"
                        setTextColor(Color.WHITE)
                        isChecked = on
                        setOnCheckedChangeListener { _, c -> setOn(c) }
                    })
                    uiSlider("$n. bei", 0f, 1f, pct / 100f) { v -> setPct((v * 100).roundToInt()) }
                }
                gifRow(2, settings.gif2On, settings.gif2Pct, { settings.gif2On = it }, { settings.gif2Pct = it })
                gifRow(3, settings.gif3On, settings.gif3Pct, { settings.gif3On = it }, { settings.gif3Pct = it })
                editorContent.addView(uiChip("▶ Animation testen") { playLogoAnimation() }, wrapLp(0))
            }
        }
    }

    private fun openProjectFolderEditor() {
        val logo = settings.selectedLogo ?: return openProjectEditor()
        openEditor("📂 Speicherort") {
            projectRefresh = { openProjectFolderEditor() }
            val fl = folderLabel(settings.folderForLogo(logo))
            editorContent.addView(uiText("Fotos: Bilder/$fl\nVideos: Filme/$fl\nUntertitel & Marker: Download/$fl",
                13f, Color.argb(220, 255, 255, 255)))
            editorContent.addView(uiChip("📁 Ordner: $fl – ändern") { chooseFolderForLogo() }, wrapLp(0))
        }
    }

    private fun openProjectWordsEditor() {
        openEditor("📝 Wortliste") {
            editorContent.addView(uiText("Namen und Fachbegriffe dieses Projekts, durch Komma getrennt – Whisper schreibt sie " +
                "dann in den Untertiteln richtig.", 12f, Color.argb(200, 255, 255, 255)))
            uiField("z. B. MoManufaktur, MMES, Steinbergkirche", settings.subtitleWords) { settings.subtitleWords = it }
        }
    }

    private fun openProjectClipsEditor() {
        openEditor("🎬 Intro & Outro") {
            projectRefresh = { openProjectClipsEditor() }
            editorContent.addView(SwitchCompat(this).apply {
                text = "An jedes Video dieses Projekts anhängen"
                setTextColor(Color.WHITE)
                isChecked = settings.introOutroEnabled
                setOnCheckedChangeListener { _, c -> settings.introOutroEnabled = c }
            })
            if (settings.timeLimit > 0) editorContent.addView(uiText(
                "Zeitlimit aktiv: Intro und Outro werden von der Aufnahmezeit abgezogen (jetzt ${effectiveLimit()} s Aufnahme).",
                11f, Color.argb(170, 255, 255, 255)))
            fun clipRow(label: String, kind: String, file: File, pick: () -> Unit) {
                val exists = file.exists()
                editorContent.addView(uiText(
                    "$label: " + if (exists) (settings.clipName(kind) ?: "übernommener Clip") + " (${clipSeconds(file)} s)" else "– keiner gewählt –",
                    13f, if (exists) Color.WHITE else Color.argb(160, 255, 255, 255)).apply { setPadding(0, dp(10), 0, 0) })
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, 0) }
                if (exists) row.addView(uiChip("▶ Abspielen") { playClip(file, label) }, wrapLp(6))
                row.addView(uiChip(if (exists) "Ändern" else "$label wählen") { pick() }, wrapLp(6))
                if (exists) row.addView(uiChip("Entfernen") {
                    file.delete(); settings.setClipName(kind, null)
                    toast("$label für dieses Projekt entfernt")
                    openProjectClipsEditor()
                }, wrapLp(0))
                editorContent.addView(row)
            }
            clipRow("Intro", "intro", introFile()) { pickIntro.launch("video/*") }
            clipRow("Outro", "outro", outroFile()) { pickOutro.launch("video/*") }
        }
    }

    /** Kleiner Player für Intro/Outro. */
    private fun playClip(file: File, title: String) = playVideo(Uri.fromFile(file), title, dp(320))

    /** Kleiner Video-Player im Dialog (für Intro/Outro und die Galerie). */
    private fun playVideo(uri: Uri, title: String, height: Int = (resources.displayMetrics.heightPixels * 0.6f).toInt()) {
        val video = android.widget.VideoView(this)
        val box = android.widget.FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(video, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.CENTER))
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("▶ $title")
            .setView(box)
            .setPositiveButton("Schließen", null)
            .create()
        dlg.setOnDismissListener { video.stopPlayback() }
        video.setMediaController(android.widget.MediaController(this).also { it.setAnchorView(video) })
        video.setVideoURI(uri)
        video.setOnPreparedListener { video.start() }
        video.setOnErrorListener { _, _, _ -> toast("Video kann nicht abgespielt werden"); true }
        dlg.show()
    }

    /** Grundeinstellungen je Kamera: Frontkamera spiegeln, Ausrichtung von Logo und Videobild. */
    private fun openBaseEditor() {
        val front = isFrontCam
        openEditor("🔧 Grundeinstellungen") {
            editorContent.addView(uiText("Aktuell: " + (if (front) "Frontkamera" else "Rückkamera") +
                " – mit „⟲“ die andere Kamera einstellen.", 12f, Color.argb(200, 255, 255, 255)))
            editorContent.addView(uiChip("⟲ Kamera wechseln") {
                if (recording == null) {
                    switchCamera()
                    editorContent.postDelayed({ editorReturn = null; openBaseEditor() }, 300)
                }
            }, wrapLp(0))
            if (front) {
                editorContent.addView(SwitchCompat(this).apply {
                    text = "Automatisch spiegeln (wie ein Spiegel)"
                    setTextColor(Color.WHITE)
                    isChecked = settings.frontMirror
                    setOnCheckedChangeListener { _, checked ->
                        settings.frontMirror = checked
                        applyCameraFixes()
                        applyPreviewMirror()
                    }
                })
            }
            uiSection("Video-Ausrichtung")
            editorContent.addView(uiText(
                "Vorschau und Foto brauchen keine Einstellung. Fürs Video: Testvideo aufnehmen. Ist das Bild gegenüber der " +
                    "Vorschau gespiegelt → „Bild ⇋“. Steht das Logo anders als in der Vorschau → „Logo ↻ / ⇋“.",
                11f, Color.argb(170, 255, 255, 255)))
            val target = Settings.TARGET_VIDEO
            val fixText = uiText("Logo im Video: " + fixLabel(settings.getFix(front, target)), 13f).apply { setPadding(0, dp(6), 0, 0) }
            editorContent.addView(fixText)
            fun update(v: Int) {
                settings.setFix(front, target, v)
                applyCameraFixes()
                fixText.text = "Logo im Video: " + fixLabel(v)
            }
            val fixRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fixRow.addView(uiChip("Logo ↻ 90°") {
                val v = settings.getFix(front, target)
                update((v / 4) * 4 + (v % 4 + 1) % 4)
            }, wrapLp(6))
            fixRow.addView(uiChip("Logo ⇋") { update((settings.getFix(front, target) + 4) % 8) }, wrapLp(0))
            editorContent.addView(fixRow)
            val flipText = uiText("Video-Bild: " + if (settings.getVideoFlip(front)) "gespiegelt" else "normal", 13f)
                .apply { setPadding(0, dp(8), 0, 0) }
            editorContent.addView(flipText)
            editorContent.addView(uiChip("Bild ⇋") {
                val v = !settings.getVideoFlip(front)
                settings.setVideoFlip(front, v)
                applyCameraFixes()
                flipText.text = "Video-Bild: " + if (v) "gespiegelt" else "normal"
            }, wrapLp(0))
        }
    }

    // ---------------- Hintergrund ersetzen ----------------
    private val pickBgImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importBg(uri, Background.imageFile(this), true)
    }
    private val pickBgVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importBg(uri, Background.videoFile(this), false)
    }

    private fun importBg(uri: Uri, target: File, image: Boolean) {
        toast("Hintergrund wird übernommen …")
        bg.execute {
            val name = try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) { null } ?: if (image) "Foto" else "Video"
            val ok = try {
                contentResolver.openInputStream(uri)?.use { inp -> target.outputStream().use { inp.copyTo(it) } } != null
            } catch (e: Exception) { Log.w(TAG, "Hintergrund", e); false }
            runOnUiThread {
                if (!ok) { toast("Datei konnte nicht übernommen werden"); return@runOnUiThread }
                if (image) { settings.bgImageName = name; settings.bgType = 2 } else { settings.bgVideoName = name; settings.bgType = 3 }
                settings.bgEnabled = true
                applyBackground()
                updateUi()
                if (editorPanel.visibility == View.VISIBLE) openBackgroundEditor()
            }
        }
    }

    private fun setBackgroundEnabled(on: Boolean) {
        settings.bgEnabled = on
        if (on && !effectActive) toast("Live-Hintergrund wird in diesem Modus nicht unterstützt")
        applyBackground()
        updateUi()
    }

    /** Einstellungen an die Kamera-Pipeline übergeben (Foto/Video laden, Maske zurücksetzen). */
    private fun applyBackground() {
        fxState.bgBlur = settings.bgBlur
        fxState.maskFix = settings.maskFix
        val mode = settings.bgModeNow
        if (mode == 0) { fxState.bgMode = 0; return }
        if (mode == 2) {
            bg.execute {
                val b = Background.loadImage(Background.imageFile(this))
                runOnUiThread {
                    fxState.bgImage = b
                    fxState.bgImageGen++
                    fxState.bgMode = if (settings.bgModeNow == 2) 2 else settings.bgModeNow
                    if (b == null) toast("Noch kein Hintergrund-Foto gewählt")
                }
            }
        } else {
            if (mode == 3) {
                val f = Background.videoFile(this)
                fxState.bgVideoPath = if (f.exists()) f.path else null
                fxState.bgVideoGen++
                if (!f.exists()) toast("Noch kein Hintergrund-Video gewählt")
            }
            fxState.bgMode = mode
        }
    }

    private fun openBackgroundEditor() {
        openEditor("🖼 Hintergrund") {
            editorContent.addView(uiText(
                "Ersetzt den Hintergrund hinter dir – live in der Vorschau, im Video und im Foto. Funktioniert am besten " +
                    "mit einer Person nah an der Kamera (0,5–2 m) und gutem Licht.", 12f, Color.argb(200, 255, 255, 255)))
            editorContent.addView(SwitchCompat(this).apply {
                text = "Hintergrund ersetzen"
                setTextColor(Color.WHITE)
                isChecked = settings.bgEnabled
                setOnCheckedChangeListener { _, c -> setBackgroundEnabled(c) }
            })
            uiSection("Art")
            uiChoice(listOf("🌫 Weichzeichnen", "🖼 Foto", "🎞 Video"), settings.bgType - 1) { i ->
                settings.bgType = i + 1
                applyBackground(); updateUi()
                openBackgroundEditor()
            }
            when (settings.bgType) {
                1 -> uiSlider("Stärke", 0f, 1f, settings.bgBlur) { v -> settings.bgBlur = v; fxState.bgBlur = v }
                2 -> {
                    val has = Background.imageFile(this).exists()
                    editorContent.addView(uiText("Foto: " + if (has) settings.bgImageName.ifEmpty { "gewählt" } else "– keins gewählt –",
                        13f, if (has) Color.WHITE else Color.argb(160, 255, 255, 255)).apply { setPadding(0, dp(6), 0, 0) })
                    editorContent.addView(uiChip(if (has) "Foto ändern …" else "Foto wählen …") { pickBgImage.launch("image/*") }, wrapLp(0))
                }
                3 -> {
                    val f = Background.videoFile(this)
                    val has = f.exists()
                    editorContent.addView(uiText("Video: " + if (has) settings.bgVideoName.ifEmpty { "gewählt" } else "– keins gewählt –",
                        13f, if (has) Color.WHITE else Color.argb(160, 255, 255, 255)).apply { setPadding(0, dp(6), 0, 0) })
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, 0) }
                    if (has) row.addView(uiChip("▶ Abspielen") { playClip(f, "Hintergrund-Video") }, wrapLp(6))
                    row.addView(uiChip(if (has) "Video ändern …" else "Video wählen …") { pickBgVideo.launch("video/*") }, wrapLp(0))
                    editorContent.addView(row)
                    editorContent.addView(uiText("Läuft ohne Ton in Schleife hinter dir. Im Foto erscheint ein Standbild daraus.",
                        11f, Color.argb(170, 255, 255, 255)))
                }
            }
            uiSection("Falls der Hintergrund falsch sitzt")
            editorContent.addView(uiText("Liegt die Freistellung verdreht oder gespiegelt neben dir, hier die Ausrichtung der " +
                "Erkennung ändern (wie bei den Grundeinstellungen).", 11f, Color.argb(170, 255, 255, 255)))
            val fixText = uiText("Ausrichtung: " + fixLabel(settings.maskFix), 13f)
            editorContent.addView(fixText)
            val fr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun setFix(v: Int) { settings.maskFix = v; fxState.maskFix = v; fixText.text = "Ausrichtung: " + fixLabel(v) }
            fr.addView(uiChip("↻ 90°") { val v = settings.maskFix; setFix((v / 4) * 4 + (v % 4 + 1) % 4) }, wrapLp(6))
            fr.addView(uiChip("⇋ Spiegeln") { setFix((settings.maskFix + 4) % 8) }, wrapLp(6))
            fr.addView(uiChip("Zurücksetzen") { setFix(0) }, wrapLp(0))
            editorContent.addView(fr)
        }
    }

    // ---------------- Backup ----------------
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@registerForActivityResult
        toast("Backup wird erstellt …")
        bg.execute {
            val n = try {
                contentResolver.openOutputStream(uri)?.use { Backup.export(this, it) } ?: -1
            } catch (e: Exception) { Log.e(TAG, "Backup", e); -1 }
            runOnUiThread {
                if (n >= 0) {
                    settings.lastBackup = System.currentTimeMillis()
                    toast("✓ Backup gespeichert ($n Dateien + alle Einstellungen)")
                    if (editorPanel.visibility == View.VISIBLE) openBackupEditor()
                } else toast("Backup fehlgeschlagen")
            }
        }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup wiederherstellen?")
            .setMessage("Alle aktuellen Einstellungen, Projekte, Logos und Intro/Outro-Clips werden durch das Backup ersetzt. " +
                "Fotos und Videos in der Galerie bleiben unberührt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Wiederherstellen") { _, _ ->
                toast("Backup wird wiederhergestellt …")
                bg.execute {
                    val result = try {
                        contentResolver.openInputStream(uri)?.use { Backup.import(this, it) }?.let { "✓ Wiederhergestellt ($it Dateien) – App startet neu" }
                            ?: "Datei konnte nicht geöffnet werden"
                    } catch (e: IllegalArgumentException) { "Das ist keine MoCam-Sicherung" }
                    catch (e: Exception) { Log.e(TAG, "Restore", e); "Wiederherstellen fehlgeschlagen" }
                    runOnUiThread {
                        toast(result)
                        if (result.startsWith("✓")) window.decorView.postDelayed({ recreate() }, 800)
                    }
                }
            }
            .show()
    }

    private fun openBackupEditor() {
        openEditor("💾 Backup") {
            editorContent.addView(uiText(
                "Sichert die ganze App in einer ZIP-Datei: alle Einstellungen, alle Projekte mit Logos (auch GIF), " +
                    "Logo-Positionen, Bauchbinden, Wortlisten, Copyright und Intro/Outro-Clips.\n" +
                    "Nicht enthalten: Fotos und Videos (die liegen in der Galerie) und die Sprachmodelle (werden bei Bedarf neu geladen).",
                12f, Color.argb(210, 255, 255, 255)))
            editorContent.addView(uiText(
                if (settings.lastBackup == 0L) "Noch kein Backup erstellt."
                else "Letztes Backup: " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(settings.lastBackup)),
                12f, Color.rgb(255, 213, 79)).apply { setPadding(0, dp(8), 0, 0) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            row.addView(uiChip("💾 Backup erstellen …") {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(java.util.Date())
                createBackup.launch("MoCam-Backup-$date.zip")
            }, wrapLp(8))
            row.addView(uiChip("📂 Wiederherstellen …") {
                restoreBackup.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            }, wrapLp(0))
            editorContent.addView(row)
            editorContent.addView(uiText(
                "Tipp: Speichere das Backup z. B. in Google Drive, Huawei Cloud oder auf einen USB-Stick – so kannst du alles " +
                    "auf ein neues Handy übernehmen.", 11f, Color.argb(170, 255, 255, 255)).apply { setPadding(0, dp(8), 0, 0) })
        }
    }

    private fun openCopyrightEditor() {
        openEditor("©️ Copyright") {
            editorContent.addView(uiText("Gilt für das Projekt ${settings.selectedLogo?.let { projectName(it) } ?: "ohne Logo"}.",
                12f, Color.argb(200, 255, 255, 255)))
            editorContent.addView(SwitchCompat(this).apply {
                text = "Autor und Copyright in jedes Foto schreiben"
                setTextColor(Color.WHITE)
                isChecked = settings.copyrightEnabled
                setOnCheckedChangeListener { _, c -> settings.copyrightEnabled = c }
            })
            uiField("Autor", settings.copyrightAuthor) { settings.copyrightAuthor = it }
            uiField("Copyright", settings.copyrightText) { settings.copyrightText = it }
            editorContent.addView(uiText("Wird in die Foto-Metadaten (EXIF) geschrieben. Videos speichert Android ohne Autor-Feld.",
                11f, Color.argb(170, 255, 255, 255)))
        }
    }

    private fun openMicEditor() {
        openEditor("🎤 Mikrofon") {
            editorContent.addView(uiText("Erkannt: " + inputDevicesText(), 12f, Color.argb(200, 255, 255, 255)))
            uiSection("Quelle")
            uiChoice(listOf("Automatisch (intern / USB)", "Bluetooth-Headset"), if (settings.micMode == "bluetooth") 1 else 0) { i ->
                settings.micMode = if (i == 1) "bluetooth" else "auto"
            }
            editorContent.addView(uiText("USB-Mikrofone werden automatisch genutzt. Der Pegel erscheint während der Aufnahme oben (rot = zu laut).",
                11f, Color.argb(170, 255, 255, 255)))
        }
    }

    private fun openViewEditor() {
        openEditor("🧭 Ansicht") {
            uiSection("Obere Buttonleiste")
            uiChoice(listOf("Eine Zeile, scrollbar", "Alle Buttons sichtbar"), if (settings.topBarWrap) 1 else 0) { i ->
                settings.topBarWrap = i == 1
                applyTopBarMode()
            }
        }
    }

    private fun tryBind(provider: ProcessCameraProvider, selector: CameraSelector, group: UseCaseGroup): Camera? =
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, group)
        } catch (e: Exception) {
            Log.w(TAG, "Binden fehlgeschlagen", e)
            null
        }

    private fun applyRotation() {
        processor.rotationDegrees = sensorRotation
    }

    private fun setupCameraControls(cam: Camera) {
        val info = cam.cameraInfo

        // Zoom
        info.zoomState.observe(this) { zs ->
            zoomText.text = String.format("%.1fx", zs.zoomRatio)
            if (!zoomBarTracking) zoomBar.progress = (zs.linearZoom * 1000).roundToInt()
        }
        buildZoomChips(cam)

        // Belichtung
        val es = info.exposureState
        if (es.isExposureCompensationSupported) {
            exposureRow.visibility = View.VISIBLE
            val range = es.exposureCompensationRange
            exposureBar.max = range.upper - range.lower
            exposureBar.progress = es.exposureCompensationIndex - range.lower
        } else {
            exposureRow.visibility = View.GONE
        }

        // Taschenlampe im Videomodus
        if (videoMode && info.hasFlashUnit()) {
            cam.cameraControl.enableTorch(flashMode == FLASH_ON)
        }
    }

    private fun buildZoomChips(cam: Camera) {
        zoomChips.removeAllViews()
        val zs = cam.cameraInfo.zoomState.value ?: return
        val stops = mutableListOf<Float>()
        if (zs.minZoomRatio < 0.95f) stops += zs.minZoomRatio
        listOf(1f, 2f, 5f, 10f).filter { it in zs.minZoomRatio..zs.maxZoomRatio }.forEach { stops += it }
        stops.forEach { r ->
            val tv = TextView(this).apply {
                text = if (r < 1f) String.format("%.1f", r) else "${r.toInt()}x"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(dp(9), dp(5), dp(9), dp(5))
                setOnClickListener { camera?.cameraControl?.setZoomRatio(r) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4) }
            zoomChips.addView(tv, lp)
        }
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        if (overlay.lockText != null) {
            // Sperre aufheben
            overlay.lockText = null
            cam.cameraControl.cancelFocusAndMetering()
        }
        // PreviewView ist ggf. horizontal zurückgespiegelt -> Tippen entsprechend umrechnen
        val px = if (previewView.scaleX < 0f) previewView.width - x else x
        val point = previewView.meteringPointFactory.createPoint(px, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
        overlay.showFocus(x, y)
    }

    private fun switchCamera() {
        // Während einer Videoaufnahme läuft die Aufnahme weiter (Persistent Recording)
        if (recording != null && !videoMode) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        extensionMode = ExtensionMode.NONE
        if (logoPanel.visibility == View.VISIBLE) closeLogoPanel()
        bindCamera()
    }

    private fun setVideoMode(video: Boolean) {
        if (recording != null || videoMode == video) return
        videoMode = video
        settings.videoMode = video
        if (!video) {
            prompterView.playing = false
            prompterBox.visibility = View.GONE
        }
        flashMode = FLASH_OFF
        extensionMode = ExtensionMode.NONE
        updateUi()
        bindCamera()
    }

    private fun cycleFlash() {
        val cam = camera
        if (cam != null && !cam.cameraInfo.hasFlashUnit()) {
            toast("Diese Kamera hat keinen Blitz.")
            return
        }
        if (videoMode) {
            flashMode = if (flashMode == FLASH_ON) FLASH_OFF else FLASH_ON
            cam?.cameraControl?.enableTorch(flashMode == FLASH_ON)
        } else {
            flashMode = when (flashMode) { FLASH_OFF -> FLASH_ON; FLASH_ON -> FLASH_AUTO; else -> FLASH_OFF }
            imageCapture?.flashMode = imageFlashMode()
        }
        updateUi()
    }

    private fun imageFlashMode() = when (flashMode) {
        FLASH_ON -> ImageCapture.FLASH_MODE_ON
        FLASH_AUTO -> ImageCapture.FLASH_MODE_AUTO
        else -> ImageCapture.FLASH_MODE_OFF
    }


    // =====================================================================
    // Teleprompter (nur Anzeige, wird nicht aufgezeichnet)
    // =====================================================================

    private fun setupPrompter() {
        settings.migratePrompterText()
        prompterView.text = settings.prompterText
        prompterView.textSizeSp = settings.prompterTextSize
        prompterSpeed.progress = settings.prompterSpeed
        prompterView.speedDp = speedFromSlider(settings.prompterSpeed)

        prompterView.onPlayingChanged = { playing -> btnPrompterPlay.text = if (playing) "⏸" else "▶" }

        btnPrompter.setOnClickListener {
            if (prompterBox.visibility == View.VISIBLE) {
                prompterView.playing = false
                prompterBox.visibility = View.GONE
            } else {
                prompterBox.visibility = View.VISIBLE
                if (settings.prompterText.isBlank()) editPrompterText()
            }
            updateUi()
        }
        btnPrompterPlay.setOnClickListener { prompterView.playing = !prompterView.playing }
        btnPrompterPlay.setOnLongClickListener {
            prompterView.playing = false
            prompterView.reset()
            toast("Prompter an den Anfang gesetzt")
            true
        }
        prompterSpeed.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prompterView.speedDp = speedFromSlider(progress)
                if (fromUser) settings.prompterSpeed = progress
            }
        })
        findViewById<View>(R.id.btnPrompterSmaller).setOnClickListener { changePrompterSize(-4f) }
        findViewById<View>(R.id.btnPrompterBigger).setOnClickListener { changePrompterSize(4f) }
        findViewById<View>(R.id.btnPrompterEdit).setOnClickListener { editPrompterText() }
        // Auto-Start: Prompter läuft mit dem Start der Aufnahme los (bei Selbstauslöser nach dem Countdown)
        val autoChip = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.chip_bg)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        fun styleAuto() {
            autoChip.text = if (settings.prompterAutoStart) "⏯ Auto an" else "⏯ Auto aus"
            autoChip.setBackgroundResource(if (settings.prompterAutoStart) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            autoChip.setTextColor(if (settings.prompterAutoStart) Color.BLACK else Color.WHITE)
        }
        autoChip.setOnClickListener {
            settings.prompterAutoStart = !settings.prompterAutoStart
            styleAuto()
            toast(if (settings.prompterAutoStart) "Prompter startet automatisch mit der Aufnahme (bei Timer nach dem Countdown)"
                  else "Prompter startet nur über ▶")
        }
        styleAuto()
        (btnPrompterPlay.parent as? LinearLayout)?.addView(autoChip, 1,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })

        // Regler für die Durchsichtigkeit des Hintergrunds
        val bgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val bgLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            minWidth = dp(118)
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
        }
        val bgSeek = SeekBar(this).apply {
            max = 100
            progress = settings.prompterBgAlpha
            setOnSeekBarChangeListener(object : SimpleSeek() {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    applyPrompterBg(progress); bgLabel.text = "◐ Hintergrund $progress %"
                    if (fromUser) settings.prompterBgAlpha = progress
                }
            })
        }
        bgRow.addView(bgLabel)
        bgRow.addView(bgSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        (prompterBox as LinearLayout).addView(bgRow)
        bgLabel.text = "◐ Hintergrund ${settings.prompterBgAlpha} %"
        applyPrompterBg(settings.prompterBgAlpha)

        findViewById<View>(R.id.btnPrompterClose).setOnClickListener {
            prompterView.playing = false
            prompterBox.visibility = View.GONE
            updateUi()
        }
    }

    /** Hintergrund des Prompters: 0 % = durchsichtig, 100 % = wie bisher (abgedunkelt). Text bekommt Schatten. */
    private fun applyPrompterBg(percent: Int) {
        prompterBox.background?.mutate()?.alpha = (percent * 255 / 100).coerceIn(0, 255)
        prompterView.shadow = percent < 60
    }

    /** Prompter-Text des aktuellen Projekts anzeigen (nach Logo-Wechsel). */
    private fun loadProjectPrompter() {
        prompterView.playing = false
        prompterView.text = settings.prompterText
        prompterView.reset()
    }

    /**
     * Mit Zeitlimit: Prompter-Tempo so wählen, dass der ganze Text genau in die Aufnahmezeit passt
     * (Limit minus Intro/Outro, mit 5 % Reserve). Der Text startet dafür von vorn.
     */
    private fun applyPrompterAutoSpeed() {
        if (settings.timeLimit == 0) return
        val sec = effectiveLimit().toFloat()
        val dist = prompterView.scrollDistanceDp()
        if (sec <= 0f || dist <= 0f) return
        val speed = dist / (sec * 0.95f)
        prompterView.reset()
        // erst den Regler setzen (sein Listener setzt ein gerundetes Tempo), dann das genaue Tempo
        prompterSpeed.progress = ((speed - 5f) / 1.5f).roundToInt().coerceIn(0, 100)
        prompterView.speedDp = speed
        toast("Prompter-Tempo an ${effectiveLimit()} s Aufnahmezeit angepasst")
    }

    /** Regler 0..100 -> 5..155 dp pro Sekunde */
    private fun speedFromSlider(p: Int) = 5f + p * 1.5f

    private fun changePrompterSize(delta: Float) {
        prompterView.textSizeSp = prompterView.textSizeSp + delta
        settings.prompterTextSize = prompterView.textSizeSp
    }

    private fun editPrompterText(after: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            setText(settings.prompterText)
            hint = "Dein Text für die Aufnahme …"
            minLines = 6
            maxLines = 14
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Prompter-Text")
            .setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Übernehmen") { _, _ ->
                val t = input.text.toString()
                settings.prompterText = t
                prompterView.playing = false
                prompterView.text = t
                prompterView.reset()
                after?.invoke()
            }
            .show()
    }

    private fun prompterActive() = prompterBox.visibility == View.VISIBLE

    // =====================================================================
    // Aufnahme
    // =====================================================================

    private fun onShutter() {
        if (logoPanel.visibility == View.VISIBLE) closeLogoPanel()
        if (settingsPanel.visibility == View.VISIBLE) closeSettings()
        if (videoMode) {
            if (recording != null) stopRecording() else withTimer { startRecording() }
        } else when {
            intervalRunning -> stopInterval()
            settings.photoInterval > 0 -> withTimer { startInterval() }
            settings.bracket > 0 -> withTimer { runBracket() }
            else -> withTimer { takePhoto() }
        }
    }

    private fun withTimer(action: () -> Unit) {
        countdown?.let {
            it.cancel()
            countdown = null
            countdownText.visibility = View.GONE
            return
        }
        val sec = settings.timer
        if (sec == 0) {
            action()
            return
        }
        countdownText.visibility = View.VISIBLE
        countdown = object : CountDownTimer(sec * 1000L, 250L) {
            override fun onTick(ms: Long) {
                countdownText.text = ceil(ms / 1000.0).toInt().toString()
            }
            override fun onFinish() {
                countdown = null
                countdownText.visibility = View.GONE
                action()
            }
        }.start()
    }

    private fun takePhoto(quiet: Boolean = false, onSaved: (() -> Unit)? = null) {
        val ic = imageCapture ?: return
        MediaStoreUtil.subFolder = currentFolder()
        val filter = processor.filter
        val logo = if (settings.logoEnabled) logoBitmap else null
        // Drehen/Spiegeln macht MoCam selbst am fertigen Foto (Grundeinstellung + Frontkamera-Spiegel)
        val mirror = isFrontCam && settings.frontMirror
        val photoFix = 0
        val bgMode = settings.bgModeNow
        val bgBlur = settings.bgBlur
        val needsProcessing = !filter.isIdentity || logo != null || mirror || photoFix != 0 || bgMode > 0
        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = false }

        if (!quiet) flashScreen()
        sound.play(MediaActionSound.SHUTTER_CLICK)

        if (!needsProcessing) {
            val opts = ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStoreUtil.imageValues(MediaStoreUtil.fileName("MoCam"))
            ).setMetadata(meta).build()
            ic.takePicture(opts, mainExec, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { setLastMedia(it, false) }
                    onSaved?.invoke()
                }
                override fun onError(e: ImageCaptureException) {
                    toast("Foto fehlgeschlagen: ${e.message}")
                    onSaved?.invoke()
                }
            })
        } else {
            val tmp = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            val opts = ImageCapture.OutputFileOptions.Builder(tmp).setMetadata(meta).build()
            val cx = settings.logoCx
            val cy = settings.logoCy
            val w = settings.logoW
            val a = settings.logoAlpha
            ic.takePicture(opts, mainExec, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    bg.execute {
                        val uri = try {
                            MediaStoreUtil.processAndSavePhoto(this@MainActivity, tmp, filter, logo, cx, cy, w, a, mirror, photoFix,
                                post = if (bgMode > 0) { b -> Background.replaceInPhoto(this@MainActivity, b, bgMode, bgBlur) } else null)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Fotoverarbeitung", t)
                            null
                        } finally {
                            tmp.delete()
                        }
                        runOnUiThread {
                            if (uri != null) setLastMedia(uri, false)
                            else toast("Foto konnte nicht gespeichert werden.")
                            onSaved?.invoke()
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    tmp.delete()
                    toast("Foto fehlgeschlagen: ${e.message}")
                    onSaved?.invoke()
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    private fun startRecording() {
        val vc = videoCapture ?: return
        MediaStoreUtil.subFolder = currentFolder()
        recordingFolder = MediaStoreUtil.subFolder
        voice.stop() // Mikrofon für die Aufnahme freigeben
        markerTimes.clear()
        lastRecordedNs = 0L
        if (settings.audio && settings.micMode == "bluetooth") startBluetoothMic()
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(MediaStoreUtil.videoValues(MediaStoreUtil.fileName("MoCam")))
            .build()
        // Persistent: Aufnahme läuft beim Kamerawechsel (vorne/hinten) einfach weiter
        var pending = vc.output.prepareRecording(this, opts).asPersistentRecording()
        val timelapse = settings.timelapse
        fxState.timelapseFactor = timelapse
        fxState.timelapseGen++
        fxState.timelapseActive = timelapse > 1
        if (timelapse == 0 && settings.audio && granted(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }
        isPaused = false
        recording = pending.start(mainExec) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    lowerFired.clear()
                    gifFired.clear()
                    if (settings.lowerEnabled) startLowerThird()
                    playLogoAnimation() // animiertes Logo: einmal abspielen, dann Startbild
                    if (prompterActive() && settings.prompterAutoStart) {
                        applyPrompterAutoSpeed()
                        prompterView.playing = true
                    }
                    updateUi()
                }
                is VideoRecordEvent.Pause -> {
                    isPaused = true
                    prompterView.playing = false
                    updateUi()
                }
                is VideoRecordEvent.Resume -> {
                    isPaused = false
                    if (prompterActive() && settings.prompterAutoStart) prompterView.playing = true
                    updateUi()
                }
                is VideoRecordEvent.Status -> {
                    lastRecordedNs = event.recordingStats.recordedDurationNanos
                    checkLowerRepeats(lastRecordedNs / 1e9)
                    checkGifRepeats(lastRecordedNs / 1e9)
                    val s = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    val limit = effectiveLimit()
                    recIndicator.text = if (limit > 0) {
                        val left = (limit - s).coerceAtLeast(0)
                        String.format("%s %02d:%02d · noch %d s", if (isPaused) "❚❚" else "●", s / 60, s % 60, left)
                    } else {
                        String.format("%s %02d:%02d", if (isPaused) "❚❚" else "●", s / 60, s % 60)
                    }
                    if (limit > 0 && s >= limit) stopRecording()
                    // #8 Pegel
                    val amp = event.recordingStats.audioStats.audioAmplitude
                    audioLevel.progress = (amp * 100).roundToInt().coerceIn(0, 100)
                    audioLevel.progressTintList = android.content.res.ColorStateList.valueOf(
                        if (amp > 0.9) Color.rgb(229, 57, 53) else if (amp > 0.7) Color.rgb(255, 213, 79) else Color.rgb(76, 175, 80)
                    )
                }
                is VideoRecordEvent.Finalize -> {
                    if (settings.voiceTrigger) startVoice()
                    sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    recording = null
                    fxState.timelapseActive = false
                    stopLowerThird()
                    stopBluetoothMic()
                    isPaused = false
                    prompterView.playing = false
                    if (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                        setLastMedia(event.outputResults.outputUri, true)
                        saveMarkers(event.outputResults.outputUri)
                        if (!startPostProcessing(event.outputResults.outputUri)) toast("Video gespeichert")
                    } else {
                        Log.e(TAG, "Video-Fehler ${event.error}", event.cause)
                        toast("Videofehler (${event.error})")
                    }
                    updateUi()
                }
            }
        }
        updateUi()
    }

    private fun stopRecording() {
        recording?.stop()
    }

    private fun togglePause() {
        val r = recording ?: return
        if (isPaused) r.resume() else r.pause()
    }

    private fun flashScreen() {
        flashView.animate().cancel()
        flashView.alpha = 0.8f
        flashView.animate().alpha(0f).setDuration(220).start()
    }

    // =====================================================================
    // Logo
    // =====================================================================

    private var logoPanelFromSettings = false
    private var logoPanelToProject = false

    private fun openLogoPanel(fromSettings: Boolean = false) {
        logoPanelFromSettings = fromSettings
        settingsPanel.visibility = View.GONE
        bottomPanel.visibility = View.GONE
        logoPanel.visibility = View.VISIBLE
        refreshLogoPanel()
        applyDragFix()
        overlay.editLogo = true
        if (!effectActive) toast("In diesem Modus keine Live-Vorschau des Logos.")
        if (logoBitmap == null) pickLogo.launch("image/*")
    }

    private fun closeLogoPanel() {
        logoPanel.visibility = View.GONE
        overlay.editLogo = false
        if (logoPanelToProject) {
            logoPanelToProject = false
            logoPanelFromSettings = false
            openProjectEditor()
        } else if (logoPanelFromSettings) {
            logoPanelFromSettings = false
            openSettings()
        } else {
            bottomPanel.visibility = View.VISIBLE
        }
    }

    // ---------------- Logo-Einstellungen je Kamera und Modus ----------------

    private fun logoContextKey() = (if (isFrontCam) "front" else "rear") + "_" + (if (videoMode) "video" else "photo")

    private fun logoContextName() =
        (if (isFrontCam) "Frontkamera" else "Rückkamera") + " · " + (if (videoMode) "Video" else "Foto")

    /** Lädt Position, Größe, Deckkraft und Drehung des Logos für die aktuelle Kamera und den Modus. */
    /**
     * Anderes Logo = anderes Projekt: Position, Größe, Deckkraft und Drehung dieses Projekts laden,
     * statt die Werte des vorherigen Logos weiterzuverwenden (die sonst beim nächsten Speichern dort landen).
     */
    private fun onProjectChanged() {
        loadLogoContextValues()
        loadProjectPrompter()
        logoAlphaBar.progress = (settings.logoAlpha * 100).roundToInt()
    }

    private fun loadLogoContextValues() {
        processor.logoCx = settings.logoCx
        processor.logoCy = settings.logoCy
        processor.logoW = settings.logoW
        processor.logoAlpha = settings.logoAlpha
        overlay.logoCx = settings.logoCx
        overlay.logoCy = settings.logoCy
        overlay.logoW = settings.logoW
        overlay.logoAlpha = settings.logoAlpha
        overlay.showLogo = settings.logoEnabled
        // Vorschau-Logo liegt jetzt im Bildschirm – Finger und Logo bewegen sich immer gleich
        overlay.dragFix = 0
        loadContextLogoBitmap()
        applyLowerThird() // Bauchbinde gehört zum Projekt (Logo)
    }

    /** Lädt das Logo, das für dieses Register gewählt ist (jedes Register kann ein eigenes haben). */
    private var loadedLogoName: String? = null

    private fun loadContextLogoBitmap() {
        val name = settings.selectedLogo
        if (name == loadedLogoName && logoOriginal != null) {
            logoOriginal?.let { applyLogo(it) }
            return
        }
        val file = name?.let { File(logoDir(), it) }?.takeIf { it.exists() }
        if (file == null) {
            loadedLogoName = null
            logoOriginal = null
            logoBitmap = null
            processor.setLogo(null)
            videoProcessor.setLogo(null)
            overlay.logoBitmap = null
            overlay.hasLogo = false
            return
        }
        bg.execute {
            val bmp = BitmapFactory.decodeFile(file.path)
            runOnUiThread {
                if (bmp != null && settings.selectedLogo == file.name) {
                    loadedLogoName = file.name
                    applyLogo(bmp)
                    if (logoPanel.visibility == View.VISIBLE) refreshLogoLibrary()
                }
            }
        }
    }

    // ---------------- Register im Logo-Menü ----------------

    private fun buildLogoTabs() {
        val tabs = findViewById<LinearLayout>(R.id.logoTabs)
        tabs.removeAllViews()
        val current = logoContextKey()
        listOf(
            "rear_photo" to "📷 Foto · Rück",
            "front_photo" to "📷 Foto · Front",
            "rear_video" to "🎬 Video · Rück",
            "front_video" to "🎬 Video · Front"
        ).forEach { (key, label) ->
            val sel = key == current
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(if (sel) Color.BLACK else Color.WHITE)
                setBackgroundResource(if (sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener { selectLogoTab(key) }
            }
            tabs.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) })
        }
    }

    /** Register wählen = passende Kamera und Modus aktivieren, damit du live siehst, was du einstellst. */
    private fun selectLogoTab(key: String) {
        if (key == logoContextKey()) return
        if (recording != null) {
            toast("Während der Aufnahme nicht möglich.")
            return
        }
        val wantFront = key.startsWith("front")
        val wantVideo = key.endsWith("video")
        lensFacing = if (wantFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        if (videoMode != wantVideo) {
            videoMode = wantVideo
            settings.videoMode = wantVideo
            flashMode = FLASH_OFF
            if (!wantVideo) {
                prompterView.playing = false
                prompterBox.visibility = View.GONE
            }
        }
        extensionMode = ExtensionMode.NONE
        bindCamera()
        refreshLogoPanel()
        updateUi()
    }

    private fun refreshLogoPanel() {
        updateFolderLabel()
        findViewById<TextView>(R.id.logoPanelTitle).text = "Logo – " + logoContextName()
        logoAlphaBar.progress = (settings.logoAlpha * 100).roundToInt()
        buildLogoTabs()
        refreshLogoLibrary()
    }

    private fun switchLogoContext() {
        val key = logoContextKey()
        if (key == settings.logoContext) return
        settings.logoContext = key
        loadLogoContextValues()
    }

    private fun applyDragFix() {
        overlay.dragFix = 0
        val v = settings.logoDragFix
        findViewById<TextView>(R.id.dragFixLabel).text =
            "Fingerrichtung: ${(v % 4) * 90}°" + if (v >= 4) " · gespiegelt" else ""
    }

    // ---------------- Logo-Bibliothek ----------------

    private fun logoDir() = File(filesDir, "logos").apply { mkdirs() }

    private fun libraryFiles(): List<File> =
        (logoDir().listFiles { f -> f.name.endsWith(".png") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }

    /** Übernimmt das Logo aus Version 1.0 (filesDir/logo.png) in die Bibliothek. */
    private fun migrateOldLogo() {
        val old = File(filesDir, "logo.png")
        if (!old.exists()) return
        val dst = File(logoDir(), "logo_${System.currentTimeMillis()}.png")
        if (old.renameTo(dst) && settings.selectedLogo == null) settings.selectedLogo = dst.name
    }

    private fun loadSavedLogo() {
        bg.execute {
            migrateOldLogo()
            try { cleanupLegacyClips() } catch (e: Exception) { Log.w(TAG, "Clip-Aufräumen", e) }
            val file = settings.selectedLogo?.let { File(logoDir(), it) }?.takeIf { it.exists() }
                ?: libraryFiles().firstOrNull()
            val bmp = file?.let { BitmapFactory.decodeFile(it.path) }
            runOnUiThread {
                if (file != null && bmp != null) {
                    settings.selectedLogo = file.name
                    loadedLogoName = file.name
                    applyLogo(bmp)
                }
                refreshLogoLibrary()
            }
        }
    }

    private fun importLogo(uri: Uri, replace: File? = null, copyFrom: String? = null) {
        bg.execute {
            var savedName: String? = null
            val bmp = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded: Bitmap? = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (decoded == null) {
                    null
                } else {
                    var b: Bitmap = decoded
                    val longest = max(b.width, b.height)
                    if (longest > 1024) {
                        val s = 1024f / longest
                        val scaled = Bitmap.createScaledBitmap(
                            b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true
                        )
                        if (scaled !== b) b.recycle()
                        b = scaled
                    }
                    if (b.config != Bitmap.Config.ARGB_8888) {
                        val c = b.copy(Bitmap.Config.ARGB_8888, false)
                        b.recycle()
                        b = c
                    }
                    val result: Bitmap = b
                    val file = replace ?: File(logoDir(), "logo_${System.currentTimeMillis()}.png")
                    file.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    savedName = file.name
                    // Beim Austauschen alte Animation entfernen (neues Bild kann statisch sein)
                    if (replace != null) LogoAnim.EXTENSIONS.forEach { File(logoDir(), file.nameWithoutExtension + "." + it).delete() }
                    // Animation (GIF, animiertes WebP, APNG): Original zusätzlich aufheben (erstes Bild = PNG oben)
                    val head = ByteArray(32)
                    contentResolver.openInputStream(uri)?.use { it.read(head) }
                    val ext = LogoAnim.detect(head) { contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) }
                    if (ext != null) {
                        val anim = File(logoDir(), file.nameWithoutExtension + "." + ext)
                        contentResolver.openInputStream(uri)?.use { inp -> anim.outputStream().use { inp.copyTo(it) } }
                        if (LogoAnim.load(anim) == null) anim.delete()  // nur ein Einzelbild / nicht abspielbar
                    }
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logo-Import", e)
                null
            }
            runOnUiThread {
                if (bmp == null) {
                    toast("Logo konnte nicht geladen werden.")
                } else {
                    if (replace != null) { anim = null; animName = null }  // alte Animation nicht weiterverwenden
                    if (copyFrom != null) copyProjectData(copyFrom, savedName)
                    settings.selectedLogo = savedName
                    loadedLogoName = savedName
                    applyLogo(bmp)
                    onProjectChanged()
                    enableLogo()
                    refreshLogoLibrary()
                    if (replace != null && animFor(savedName) == null) {
                        toast("Logo ausgetauscht – Position, Bauchbinde, Intro/Outro und Ordner bleiben.")
                    } else if (animFor(savedName) != null) {
                        toast("Animiertes Logo erkannt: spielt beim Start jeder Videoaufnahme einmal ab, danach bleibt das Startbild.")
                        playLogoAnimation()
                    } else toast("Logo gespeichert – jetzt im Bild platzieren.")
                }
            }
        }
    }

    private fun selectLogo(file: File) {
        bg.execute {
            val bmp = BitmapFactory.decodeFile(file.path)
            runOnUiThread {
                if (bmp == null) {
                    toast("Logo konnte nicht geladen werden.")
                    return@runOnUiThread
                }
                settings.selectedLogo = file.name
                loadedLogoName = file.name
                applyLogo(bmp)
                onProjectChanged()
                playLogoAnimation()
                updateFolderLabel()
                enableLogo()
                refreshLogoLibrary()
            }
        }
    }

    /**
     * Projektdaten kopieren: Einstellungen und Intro/Outro-Clips von [from] an das neue Logo [to]
     * (das Logo-Bild selbst nicht). Der Ordner bleibt derselbe – im Speicherort-Menü änderbar.
     */
    private fun copyProjectData(from: String, to: String?) {
        if (to == null || from == to) return
        settings.copyProject(from, to)
        val fk = from.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val tk = to.replace(Regex("[^A-Za-z0-9._-]"), "_")
        for (kind in listOf("intro", "outro")) {
            val src = File(filesDir, "${kind}_$fk.mp4")
            if (src.exists()) try { src.copyTo(File(filesDir, "${kind}_$tk.mp4"), overwrite = true) } catch (_: Exception) { }
        }
        toast("Projekt kopiert: Einstellungen, Bauchbinde, Intro/Outro, Texte von „${projectName(from)}“ übernommen")
    }

    /** Langer Druck auf ein Logo: austauschen, Projekt kopieren oder löschen. */
    private fun logoOptions(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logo bearbeiten")
            .setItems(arrayOf("🔄 Logo austauschen (Projekt-Einstellungen bleiben)",
                "📋 Projekt kopieren – neues Logo wählen", "🗑 Logo löschen")) { _, which ->
                when (which) {
                    0 -> { replaceTarget = file; pickReplacement.launch("image/*") }
                    1 -> {
                        copyFromLogo = file.name
                        toast("Neues Logo wählen – alle Einstellungen von „${projectName(file.name)}“ werden übernommen")
                        pickCopyLogo.launch("image/*")
                    }
                    2 -> confirmDeleteLogo(file)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun confirmDeleteLogo(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logo löschen?")
            .setMessage("Das Logo wird aus der Schnellauswahl entfernt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Löschen") { _, _ ->
                val wasSelected = file.name == settings.selectedLogo
                file.delete()
                LogoAnim.EXTENSIONS.forEach { File(logoDir(), file.nameWithoutExtension + "." + it).delete() }
                if (wasSelected) {
                    val next = libraryFiles().firstOrNull()
                    if (next != null) {
                        selectLogo(next)
                    } else {
                        settings.selectedLogo = null
                        logoBitmap = null
                        logoOriginal = null
                        loadedLogoName = null
                        processor.setLogo(null)
                        videoProcessor.setLogo(null)
                        overlay.hasLogo = false
                        refreshLogoLibrary()
                    }
                } else {
                    refreshLogoLibrary()
                }
            }
            .show()
    }

    private fun enableLogo() {
        settings.logoEnabled = true
        processor.logoEnabled = true
        logoSwitch.isChecked = true
    }

    /** Baut die Vorschaubilder der gespeicherten Logos im Logo-Panel auf. */
    private fun refreshLogoLibrary() {
        bg.execute {
            val thumbs = libraryFiles().map { f ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
                f to BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            runOnUiThread {
                logoLibrary.removeAllViews()
                val size = dp(60)
                val selected = settings.selectedLogo
                thumbs.forEach { (file, thumb) ->
                    val iv = ImageView(this).apply {
                        setImageBitmap(thumb)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        setBackgroundResource(
                            if (file.name == selected) R.drawable.logo_tile_selected else R.drawable.logo_tile
                        )
                        setOnClickListener { selectLogo(file) }
                        setOnLongClickListener { logoOptions(file); true }
                    }
                    logoLibrary.addView(iv, LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) })
                }
                val add = TextView(this).apply {
                    text = "+"
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.logo_tile)
                    setOnClickListener { pickLogo.launch("image/*") }
                }
                logoLibrary.addView(add, LinearLayout.LayoutParams(size, size))
            }
        }
    }

    // ---------------- Animiertes Logo (GIF) ----------------
    // Beim Import eines GIF wird das erste Bild als PNG (normales Logo) und das GIF daneben gespeichert.
    // Zu Beginn jeder Videoaufnahme läuft die Animation einmal durch, danach bleibt das Startbild stehen.

    /** Gespeicherte Animation zum Logo (GIF, WebP oder APNG) – oder null. */
    private fun animFor(name: String?): File? = name?.let { n ->
        LogoAnim.EXTENSIONS.map { File(logoDir(), n.substringBeforeLast('.') + "." + it) }.firstOrNull { it.exists() }
    }

    private var anim: LogoAnim? = null
    private var animName: String? = null
    private val animBufs = arrayOfNulls<Bitmap>(3)
    private var animBufIdx = 0
    private val animHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val animTick = object : Runnable {
        override fun run() {
            val a = anim
            val base = logoOriginal
            if (a == null || base == null || animName != settings.selectedLogo || !settings.logoEnabled || a.isDone()) {
                stopLogoAnimation(); return
            }
            // Ziel-Größe: Seitenverhältnis des Startbilds, max. 512 px (schnelleres Hochladen)
            val sc = minOf(1f, 512f / max(base.width, base.height))
            val tw = (base.width * sc).roundToInt().coerceAtLeast(1)
            val th = (base.height * sc).roundToInt().coerceAtLeast(1)
            animBufIdx = (animBufIdx + 1) % animBufs.size
            var buf = animBufs[animBufIdx]
            if (buf == null || buf.width != tw || buf.height != th) {
                buf = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888); animBufs[animBufIdx] = buf
            }
            buf!!.eraseColor(Color.TRANSPARENT)
            a.draw(android.graphics.Canvas(buf), tw, th)
            val deg = settings.logoRotation
            val frame = if (deg == 0) buf else Bitmap.createBitmap(buf, 0, 0, tw, th,
                android.graphics.Matrix().apply { postRotate(deg.toFloat()) }, true)
            processor.setLogo(frame)
            videoProcessor.setLogo(frame)
            overlay.logoBitmap = frame
            animHandler.postDelayed(this, 40)
        }
    }

    /** Spielt das animierte Logo des Projekts einmal ab (GIF, WebP oder APNG; sonst passiert nichts). */
    private fun playLogoAnimation() {
        val name = settings.selectedLogo
        val file = animFor(name) ?: return
        if (!settings.logoEnabled || logoOriginal == null) return
        animHandler.removeCallbacks(animTick)
        anim?.stop()
        bg.execute {
            // WebP-Animationen lassen sich nicht zurückspulen → jedes Mal frisch laden; andere wiederverwenden
            val a = if (animName == name && anim != null && file.extension != "webp") anim else LogoAnim.load(file)
            runOnUiThread {
                if (a == null || settings.selectedLogo != name) return@runOnUiThread
                anim = a
                animName = name
                a.start()
                animTick.run()
            }
        }
    }

    private fun stopLogoAnimation() {
        animHandler.removeCallbacks(animTick)
        anim?.stop()
        // Startbild wieder anzeigen (volle Auflösung)
        logoOriginal?.let { applyLogo(it) }
    }

    /** Übernimmt ein Logo und dreht es um die gespeicherte Drehung (gilt für Video, Vorschau und Fotos). */
    private fun applyLogo(original: Bitmap) {
        logoOriginal = original
        val deg = settings.logoRotation
        val bmp = if (deg == 0) original else {
            val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true)
        }
        logoBitmap = bmp
        processor.setLogo(bmp)
        videoProcessor.setLogo(bmp)
        overlay.logoAspect = bmp.height.toFloat() / bmp.width
        overlay.logoBitmap = bmp
        overlay.hasLogo = true
    }

    // =====================================================================
    // Galerie
    // =====================================================================

    private fun setLastMedia(uri: Uri, isVideo: Boolean) {
        lastMediaUri = uri
        lastMediaIsVideo = isVideo
        if (!isVideo && settings.copyrightEnabled) {
            val a = settings.copyrightAuthor
            val c = settings.copyrightText
            bg.execute { MediaStoreUtil.writeCopyright(this, uri, a, c) }
        }
        updateStatus()
        showShareBar(uri, isVideo)
        bg.execute {
            val thumb = MediaStoreUtil.loadThumbnail(this, uri, isVideo)
            runOnUiThread { if (thumb != null) galleryThumb.setImageBitmap(thumb) }
        }
    }

    // =====================================================================
    // Vorschau-Knopf: gedrückt halten = Einstellungen ausblenden
    // =====================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun peekChip(panel: View): TextView = TextView(this).apply {
        text = "👁 Gedrückt halten = Vorschau"
        textSize = 13f
        setTextColor(Color.BLACK)
        setBackgroundResource(R.drawable.chip_bg_selected)
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    panel.animate().cancel(); panel.alpha = 0f
                    topBar.alpha = 0f
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    panel.alpha = 1f
                    topBar.alpha = 1f
                }
            }
            true
        }
    }

    // =====================================================================
    // Obere Buttonleiste: scrollbar oder alle sichtbar
    // =====================================================================

    private fun applyTopBarMode() {
        val wrap = settings.topBarWrap
        val from: android.view.ViewGroup = if (wrap) topChips else topFlow
        val to: android.view.ViewGroup = if (wrap) topFlow else topChips
        val views = (0 until from.childCount).map { from.getChildAt(it) }
        from.removeAllViews()
        views.forEach { to.addView(it) }
        topScroll.visibility = if (wrap) View.GONE else View.VISIBLE
        topFlow.visibility = if (wrap) View.VISIBLE else View.GONE
    }

    // =====================================================================
    // Letzte Aufnahmen (in der App) – erneut teilen
    // =====================================================================

    /** folder = Projektordner unter MoCam ("" = direkt in MoCam). */
    private data class RecentItem(val uri: Uri, val isVideo: Boolean, val date: Long, val folder: String = "")

    /** Projektordner aus dem Speicherpfad lesen: …/MoCam/<Ordner>/… */
    private fun folderOf(path: String?): String {
        if (path == null) return ""
        val i = path.indexOf(MediaStoreUtil.FOLDER + "/")
        if (i < 0) return ""
        val rest = path.substring(i + MediaStoreUtil.FOLDER.length + 1)
        val seg = rest.substringBefore('/')
        // Ohne weiteren Schrägstrich ist der Rest der Dateiname (vor Android 10) bzw. leer
        return if (rest.contains('/')) seg else ""
    }

    private var recentType = 0          // 0 = alle, 1 = Fotos, 2 = Videos
    private var recentFolder: String? = null  // null = alle Projekte

    private fun queryRecent(limit: Int = 300): List<RecentItem> {
        val out = mutableListOf<RecentItem>()
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true
        ).forEach { (collection, isVideo) ->
            val (sel, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%${MediaStoreUtil.FOLDER}%")
            else
                "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/${MediaStoreUtil.FOLDER}/%")
            try {
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
                    else MediaStore.MediaColumns.DATA
                contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, pathCol),
                    sel, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < limit) {
                        val path = c.getString(2).let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it else it }
                        // RELATIVE_PATH endet mit "/", daher dort einen Platzhalter anhängen
                        val folder = folderOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (path ?: "") + "x" else path)
                        out += RecentItem(android.content.ContentUris.withAppendedId(collection, c.getLong(0)), isVideo, c.getLong(1), folder)
                        n++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Letzte Aufnahmen", e)
            }
        }
        return out.sortedByDescending { it.date }.take(limit)
    }

    private fun openRecent() {
        if (recording != null) return
        recentContent.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(uiText("🗂 Letzte Aufnahmen", 17f, getColor(R.color.accent), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(uiChip("✕ Schließen") { recentPanel.visibility = View.GONE })
        recentContent.addView(header)

        val info = uiText("Tippe eine Aufnahme an, um sie zu teilen.", 12f, Color.argb(200, 255, 255, 255))
        recentContent.addView(info)
        val actions = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionRow)
        recentContent.addView(actions)

        // Filter: Art (Alle/Fotos/Videos) und Projekt (Ordner)
        val typeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        recentContent.addView(typeRow)
        val folderScroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val folderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        folderScroll.addView(folderRow)
        recentContent.addView(folderScroll)

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        recentContent.addView(grid)
        recentPanel.visibility = View.VISIBLE

        bg.execute {
            val all = queryRecent()
            runOnUiThread {
                if (all.isEmpty()) {
                    info.text = "Noch keine Aufnahmen in MoCam gefunden."
                    return@runOnUiThread
                }
                val folders = all.map { it.folder }.distinct().sortedBy { it.lowercase() }
                if (recentFolder != null && recentFolder !in folders) recentFolder = null
                fun styleChip(v: TextView, on: Boolean) {
                    v.setBackgroundResource(if (on) R.drawable.chip_bg_selected else R.drawable.chip_bg)
                    v.setTextColor(if (on) Color.BLACK else Color.WHITE)
                }
                lateinit var fill: () -> Unit
                fun buildFilters() {
                    typeRow.removeAllViews()
                    listOf("Alle", "📷 Fotos", "🎥 Videos").forEachIndexed { i, label ->
                        val c = uiChip(label) { recentType = i; buildFilters(); fill() }
                        styleChip(c, recentType == i)
                        typeRow.addView(c, wrapLp(6))
                    }
                    folderRow.removeAllViews()
                    folderScroll.visibility = if (folders.size > 1) View.VISIBLE else View.GONE
                    val allC = uiChip("Alle Projekte") { recentFolder = null; buildFilters(); fill() }
                    styleChip(allC, recentFolder == null)
                    folderRow.addView(allC, wrapLp(6))
                    folders.forEach { f ->
                        val c = uiChip("📁 " + f.ifEmpty { "MoCam (ohne Projekt)" }) { recentFolder = f; buildFilters(); fill() }
                        styleChip(c, recentFolder == f)
                        folderRow.addView(c, wrapLp(6))
                    }
                }
                fill = {
                    grid.removeAllViews()
                    actions.visibility = View.GONE
                    val items = all.filter {
                        (recentType == 0 || (recentType == 2) == it.isVideo) && (recentFolder == null || it.folder == recentFolder)
                    }.take(90)
                    info.text = if (items.isEmpty()) "Keine Aufnahmen für diesen Filter."
                        else "${items.size} Aufnahmen · Tippen = teilen, ▶ = abspielen"
                    fillRecentGrid(grid, items, info, actions, actionRow)
                }
                buildFilters()
                fill()
            }
        }
    }

    private fun fillRecentGrid(grid: LinearLayout, items: List<RecentItem>, info: TextView,
                               actions: View, actionRow: LinearLayout) {
            run {
                val cell = (resources.displayMetrics.widthPixels - dp(24) - dp(8) * 2) / 3
                var row: LinearLayout? = null
                val frames = mutableListOf<View>()
                items.forEachIndexed { i, item ->
                    if (i % 3 == 0) {
                        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        grid.addView(row)
                    }
                    val frame = android.widget.FrameLayout(this).apply { setPadding(dp(2), dp(2), dp(2), dp(2)) }
                    val iv = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(Color.DKGRAY)
                    }
                    frame.addView(iv, android.widget.FrameLayout.LayoutParams(cell, cell))
                    if (item.isVideo) {
                        // Aktiver Abspielknopf oben links
                        val play = TextView(this).apply {
                            text = "▶"
                            textSize = 16f
                            gravity = Gravity.CENTER
                            setTextColor(Color.WHITE)
                            setBackgroundResource(R.drawable.round_btn)
                            setOnClickListener { playVideo(item.uri, "Video abspielen") }
                        }
                        frame.addView(play, android.widget.FrameLayout.LayoutParams(dp(40), dp(40)).apply {
                            leftMargin = dp(4); topMargin = dp(4)
                        })
                    }
                    frame.setOnClickListener {
                        frames.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
                        frame.setBackgroundColor(getColor(R.color.accent))
                        shareUri = item.uri
                        shareIsVideo = item.isVideo
                        info.text = (if (item.isVideo) "Video" else "Foto") + " ausgewählt – wohin teilen?"
                        actionRow.removeAllViews()
                        listOf(
                            "🟢 WhatsApp Status" to listOf("com.whatsapp", "com.whatsapp.w4b"),
                            "📸 Instagram" to listOf("com.instagram.android"),
                            "💼 LinkedIn" to listOf("com.linkedin.android"),
                            "📌 Pinterest" to listOf("com.pinterest"),
                            "⋯ Mehr" to emptyList()
                        ).forEach { (label, pkgs) -> actionRow.addView(uiChip(label) { shareTo(pkgs) }, wrapLp()) }
                        actionRow.addView(uiChip("▶ Öffnen") {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (e: ActivityNotFoundException) { toast("Keine App zum Öffnen gefunden") }
                        }, wrapLp())
                        actions.visibility = View.VISIBLE
                    }
                    frames += frame
                    row?.addView(frame)
                    bg.execute {
                        val t = MediaStoreUtil.loadThumbnail(this, item.uri, item.isVideo)
                        runOnUiThread { if (t != null) iv.setImageBitmap(t) }
                    }
                }
            }
    }

    // =====================================================================
    // Eigene Einstellmenüs mit Live-Vorschau (Bauchbinde, Untertitel)
    // =====================================================================

    private var editorAtTop = false

    /** Menü auf max. 45 % der Höhe begrenzen und oben oder unten anordnen (Vorschau bleibt sichtbar). */
    private fun placeEditor() {
        val lp = editorPanel.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.height = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        lp.gravity = if (editorAtTop) Gravity.TOP else Gravity.BOTTOM
        lp.topMargin = if (editorAtTop) topBar.height else 0
        editorPanel.layoutParams = lp
    }

    private fun openEditor(title: String, build: () -> Unit) {
        settingsPanel.visibility = View.GONE
        bottomPanel.visibility = View.GONE
        placeEditor()
        editorContent.removeAllViews()
        editorContent.addView(headerRow(title) { closeEditor() })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        quick.addView(peekChip(editorPanel), wrapLp(8))
        quick.addView(uiChip("⇅ Menü oben/unten") { editorAtTop = !editorAtTop; placeEditor() }, wrapLp(8))
        editorContent.addView(quick)
        build()
        editorPanel.visibility = View.VISIBLE
    }

    private var editorReturnToSettings = true
    /** Wohin „←“/„✓“ zurückführt, wenn ein Untermenü aus einem anderen Untermenü geöffnet wurde. */
    private var editorReturn: (() -> Unit)? = null
    private var projectEditorOpen = false

    private fun closeEditor() {
        editorPanel.visibility = View.GONE
        overlay.lowerPinned = false
        overlay.subPreviewText = null
        projectEditorOpen = false
        projectRefresh = null
        val back = editorReturn
        editorReturn = null
        when {
            back != null -> back()
            editorReturnToSettings -> openSettings()
            else -> bottomPanel.visibility = View.VISIBLE
        }
        editorReturnToSettings = true
    }

    /** Kopfzeile: „←“ zurück, Titel, „✓ Fertig“ – beide speichern (Einstellungen sind sofort gespeichert) und schließen. */
    private fun headerRow(title: String, onClose: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(uiChip("←") { onClose() }, wrapLp(10))
        row.addView(uiText(title, 17f, getColor(R.color.accent), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(uiChip("✓ Fertig", selected = true) { onClose() }, wrapLp(0))
        return row
    }

    private fun uiText(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun uiChip(t: String, selected: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(if (selected) Color.BLACK else Color.WHITE)
        setBackgroundResource(if (selected) R.drawable.chip_bg_selected else R.drawable.chip_bg)
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setOnClickListener { onClick() }
    }

    private fun wrapLp(end: Int = 6) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginEnd = dp(end); topMargin = dp(4) }

    private fun uiSection(t: String) {
        editorContent.addView(uiText(t, 14f, getColor(R.color.accent), true).apply { setPadding(0, dp(14), 0, dp(2)) })
    }

    /** Schieberegler von min bis max; zeigt den Wert in Prozent. */
    private fun uiSlider(label: String, min: Float, max: Float, value: Float, onChange: (Float) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lbl = uiText("", 13f)
        fun show(v: Float) { lbl.text = "$label: ${(v * 100).roundToInt()} %" }
        show(value)
        row.addView(lbl, LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT))
        val sb = SeekBar(this).apply {
            this.max = 1000
            progress = (((value - min) / (max - min)) * 1000).roundToInt().coerceIn(0, 1000)
            setOnSeekBarChangeListener(object : SimpleSeek() {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = min + (max - min) * p / 1000f
                    show(v)
                    onChange(v)
                }
            })
        }
        row.addView(sb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        editorContent.addView(row)
    }

    /** Horizontale Auswahl-Leiste (Schriftarten, Farben, Modi). */
    private fun uiChoice(items: List<String>, selected: Int, colors: List<Int>? = null, onPick: (Int) -> Unit) {
        val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = mutableListOf<TextView>()
        items.forEachIndexed { i, name ->
            val tv = uiChip(name, i == selected) {}
            if (colors != null) {
                // Farbfeld vor dem Namen
                val sw = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors[i]); setStroke(dp(1), Color.GRAY); setSize(dp(14), dp(14))
                }
                tv.setCompoundDrawablesWithIntrinsicBounds(sw, null, null, null)
                tv.compoundDrawablePadding = dp(6)
            }
            tv.setOnClickListener {
                views.forEachIndexed { j, v ->
                    v.setBackgroundResource(if (j == i) R.drawable.chip_bg_selected else R.drawable.chip_bg)
                    v.setTextColor(if (j == i) Color.BLACK else Color.WHITE)
                }
                onPick(i)
            }
            views += tv
            row.addView(tv, wrapLp())
        }
        hs.addView(row)
        editorContent.addView(hs)
    }

    private fun uiField(hintText: String, value: String, onChange: (String) -> Unit) {
        editorContent.addView(EditText(this).apply {
            setText(value)
            hint = hintText
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { onChange(e?.toString() ?: "") }
            })
        })
    }

    private fun uiButtons(onReset: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        row.addView(peekChip(editorPanel), wrapLp(8))
        row.addView(uiChip("⇅ Menü oben/unten") { editorAtTop = !editorAtTop; placeEditor() }, wrapLp(8))
        row.addView(uiChip("Zurücksetzen") { onReset() }, wrapLp(8))
        row.addView(uiChip("✓ Fertig") { closeEditor() }, wrapLp(0))
        editorContent.addView(row)
    }

    // ---------------- #6 Bauchbinde gestalten ----------------

    private fun openLowerThirdEditor() {
        if (!videoMode) setVideoMode(true)
        applyLowerThird()
        overlay.lowerPinned = true
        fun update(change: (LowerThirdStyle) -> LowerThirdStyle) {
            settings.lowerStyle = change(settings.lowerStyle)
            applyLowerThird()
        }
        openEditor("🪪 Bauchbinde gestalten") {
            editorContent.addView(SwitchCompat(this).apply {
                text = "Bauchbinde bei Videos einblenden"
                setTextColor(Color.WHITE)
                isChecked = settings.lowerEnabled
                setOnCheckedChangeListener { _, c -> settings.lowerEnabled = c; updateUi() }
            })
            uiSection("Text")
            uiField("Name", settings.lowerName) { settings.lowerName = it; applyLowerThird() }
            uiField("Funktion / Firma", settings.lowerTitle) { settings.lowerTitle = it; applyLowerThird() }

            val st = settings.lowerStyle
            uiSection("Größe & Position")
            uiSlider("Breite", 0.30f, 0.95f, st.width) { v -> update { it.copy(width = v) } }
            uiSlider("Abstand links", 0f, 0.60f, st.x) { v -> update { it.copy(x = v) } }
            uiSlider("Höhe im Bild", 0.20f, 0.98f, st.y) { v -> update { it.copy(y = v) } }

            uiSection("Schrift")
            uiSlider("Größe Name", 0.03f, 0.12f, st.nameSize) { v -> update { it.copy(nameSize = v) } }
            uiSlider("Größe Funktion", 0.02f, 0.08f, st.titleSize) { v -> update { it.copy(titleSize = v) } }
            uiChoice(LowerThird.FONTS.map { it.first }, st.font) { i -> update { it.copy(font = i) } }

            val names = LowerThird.COLORS.map { it.first }
            val cols = LowerThird.COLORS.map { it.second }
            fun idx(c: Int) = cols.indexOf(c).coerceAtLeast(-1)
            uiSection("Hintergrundfarbe")
            uiChoice(names, idx(st.bgColor), cols) { i -> update { it.copy(bgColor = cols[i]) } }
            uiSlider("Deckkraft", 0f, 1f, st.bgAlpha) { v -> update { it.copy(bgAlpha = v) } }
            uiSection("Zierstreifen")
            uiChoice(names, idx(st.accentColor), cols) { i -> update { it.copy(accentColor = cols[i]) } }
            uiSection("Schriftfarbe Name")
            uiChoice(names, idx(st.nameColor), cols) { i -> update { it.copy(nameColor = cols[i]) } }
            uiSection("Schriftfarbe Funktion")
            uiChoice(names, idx(st.titleColor), cols) { i -> update { it.copy(titleColor = cols[i]) } }

            uiSection("🔁 Weitere Einblendungen")
            editorContent.addView(uiText(
                if (settings.timeLimit > 0)
                    "Zeitpunkt in % der Aufnahmezeit (ohne Intro/Outro). Mit Zeitlimit wird live eingeblendet – " +
                        "Aufnahmezeit jetzt ${effectiveLimit()} s."
                else "Zeitpunkt in % der Aufnahmezeit (ohne Intro/Outro). Ohne Zeitlimit steht die Länge erst nach der " +
                    "Aufnahme fest – dann wird in der Nachbearbeitung eingeblendet (das Video wird dafür neu berechnet).",
                11f, Color.argb(170, 255, 255, 255)))
            fun repeatRow(n: Int, on: Boolean, pct: Int, setOn: (Boolean) -> Unit, setPct: (Int) -> Unit) {
                editorContent.addView(SwitchCompat(this).apply {
                    text = "$n. Einblendung"
                    setTextColor(Color.WHITE)
                    isChecked = on
                    setOnCheckedChangeListener { _, c -> setOn(c) }
                })
                uiSlider("$n. bei", 0f, 1f, pct / 100f) { v -> setPct((v * 100).roundToInt()) }
            }
            repeatRow(2, settings.lower2On, settings.lower2Pct, { settings.lower2On = it }, { settings.lower2Pct = it })
            repeatRow(3, settings.lower3On, settings.lower3Pct, { settings.lower3On = it }, { settings.lower3Pct = it })

            uiSection("Dauer am Videoanfang")
            val durs = listOf(3, 5, 8, 10, 15)
            uiChoice(durs.map { "$it s" }, durs.indexOf(settings.lowerSeconds)) { i ->
                settings.lowerSeconds = durs[i]; applyLowerThird()
            }
            uiButtons {
                settings.lowerStyle = LowerThirdStyle()
                applyLowerThird()
                openLowerThirdEditor()
            }
        }
    }

    // ---------------- #9 Untertitel einstellen ----------------

    private fun subModeName(m: String) = when (m) { "srt" -> "nur .srt-Datei"; "both" -> "eingebrannt + .srt"; else -> "eingebrannt" }

    private fun openSubtitleEditor() {
        overlay.subStyle = settings.subtitleStyle
        overlay.subPreviewText = "So sehen deine Untertitel im Video aus"
        fun update(change: (SubtitleStyle) -> SubtitleStyle) {
            settings.subtitleStyle = change(settings.subtitleStyle)
            overlay.subStyle = settings.subtitleStyle
        }
        openEditor("💬 Untertitel einstellen") {
            editorContent.addView(SwitchCompat(this).apply {
                text = "Untertitel automatisch erkennen"
                setTextColor(Color.WHITE)
                isChecked = settings.subtitlesEnabled
                setOnCheckedChangeListener { _, c ->
                    settings.subtitlesEnabled = c
                    if (c && !Subtitles.isModelReady(this@MainActivity, settings.subtitleLang, settings.subtitleEngine)) toast("Bitte zuerst das Sprachmodell laden")
                }
            })
            uiSection("Sprache")
            val langs = Subtitles.LANGUAGES
            uiChoice(langs.map { it.second }, langs.indexOfFirst { it.first == settings.subtitleLang }) { i ->
                settings.subtitleLang = langs[i].first
                openSubtitleEditor()
            }
            uiSection("Speichern als")
            val modes = listOf("burn", "srt", "both")
            uiChoice(listOf("Eingebrannt", ".srt-Datei", "Beides"), modes.indexOf(settings.subtitleMode)) { i ->
                settings.subtitleMode = modes[i]
            }
            editorContent.addView(uiText(".srt-Dateien landen in „Download/MoCam/<Ordner>“ mit gleichem Namen wie das Video.",
                11f, Color.argb(170, 255, 255, 255)))

            val st = settings.subtitleStyle
            uiSection("Position & Größe (eingebrannt)")
            uiSlider("Höhe im Bild", 0.10f, 0.95f, st.posY) { v -> update { it.copy(posY = v) } }
            uiSlider("Schriftgröße", 0.03f, 0.10f, st.size) { v -> update { it.copy(size = v) } }
            uiSlider("Hintergrund", 0f, 1f, st.bgAlpha) { v -> update { it.copy(bgAlpha = v) } }

            uiSection("Erkennung")
            val engines = Subtitles.ENGINES
            uiChoice(engines.map { it.second }, engines.indexOfFirst { it.first == settings.subtitleEngine }) { i ->
                settings.subtitleEngine = engines[i].first
                openSubtitleEditor()
            }
            val engine = settings.subtitleEngine
            editorContent.addView(uiText(when (engine) {
                "whisper-base" -> "Whisper Schnell: deutlich genauer als Vosk, mit Satzzeichen. Dauert etwa so lange wie das Video."
                "whisper-small" -> "Whisper Genau: beste Erkennung, mit Satzzeichen und Großschreibung. Dauert etwa 2–4× so lange wie das Video."
                else -> "Vosk: schnell, aber ungenauer und ohne Satzzeichen."
            }, 11f, Color.argb(170, 255, 255, 255)))
            if (engine.startsWith("whisper")) {
                editorContent.addView(SwitchCompat(this).apply {
                    text = "⏩ Sprechpausen überspringen (schneller)"
                    setTextColor(Color.WHITE)
                    isChecked = settings.subtitleSkipPauses
                    setOnCheckedChangeListener { _, c -> settings.subtitleSkipPauses = c }
                })
                editorContent.addView(uiText("Stille und Pausen werden vor der Erkennung herausgeschnitten – je mehr Pausen, " +
                    "desto schneller. Die Zeiten der Untertitel bleiben passend. Braucht einmalig ein kleines Zusatzmodell (ca. 1 MB).",
                    11f, Color.argb(170, 255, 255, 255)))
            }

            // Dauer: gemessene Werte der letzten Bearbeitung
            run {
                val st = getSharedPreferences("mocam_stats", MODE_PRIVATE)
                val v = st.getFloat("videoSec", 0f)
                val rec = st.getLong("recognizeMs", 0L) / 1000
                val exp = st.getLong("exportMs", 0L) / 1000
                val measured = if (v > 0f && (rec > 0 || exp > 0))
                    "Letzte Messung: Video ${v.roundToInt()} s → Erkennung $rec s, Einbrennen/Bearbeiten $exp s."
                else "Noch keine Messung – nach dem nächsten Video mit Untertiteln steht hier, wie lange es gedauert hat."
                editorContent.addView(uiText(
                    "⏱ Nach der Aufnahme laufen zwei Schritte: 1. Spracherkennung, 2. Einbrennen – dabei wird das ganze Video " +
                        "neu berechnet. Beides zusammen kann etwa so lange dauern wie das Video selbst oder länger. $measured\n" +
                        "Schneller: „⚡ Whisper Schnell“ wählen oder nur „.srt-Datei“ (ohne Einbrennen). Während der Aufnahme geht es " +
                        "leider nicht, weil Android das Mikrofon dann exklusiv der Aufnahme gibt.",
                    11f, Color.rgb(255, 213, 79)))
            }
            if (engine.startsWith("whisper") && !Whisper.available) editorContent.addView(uiText(
                "⚠ Whisper läuft auf diesem Gerät nicht – bitte Vosk wählen.", 12f, Color.rgb(255, 160, 80)))

            uiSection("Sprachmodell")
            val lang = settings.subtitleLang
            val langName = Subtitles.LANGUAGES.firstOrNull { it.first == lang }?.second ?: lang
            val ready = Subtitles.isModelReady(this, lang, engine)
            val modelText = uiText(if (ready) "Bereit ✓ (" + (if (engine.startsWith("whisper")) "Deutsch & Englisch" else langName) + ", offline)"
                else "Noch nicht geladen (${Subtitles.modelSizeText(lang, engine)}, einmalig – am besten im WLAN)",
                12f, Color.argb(200, 255, 255, 255))
            editorContent.addView(modelText)
            if (!ready) editorContent.addView(uiChip("⬇ Sprachmodell laden") { downloadModel(modelText) }, wrapLp())
            if (lang.startsWith("en")) editorContent.addView(uiText(
                "Bei English (UK) stellt MoCam die Schreibweise auf britisch um (colour, centre, organise …).",
                11f, Color.argb(170, 255, 255, 255)))

            uiSection("📝 Wortliste")
            editorContent.addView(uiText(
                "Namen und Fachbegriffe des Projekts ${settings.selectedLogo?.let { projectName(it) } ?: "(ohne Logo)"}, " +
                    "durch Komma getrennt. Whisper schreibt sie dann richtig, z. B.: MoManufaktur, MMES, Steinbergkirche",
                11f, Color.argb(170, 255, 255, 255)))
            uiField("Wortliste (Komma-getrennt)", settings.subtitleWords) { settings.subtitleWords = it }

            uiButtons {
                settings.subtitleStyle = SubtitleStyle()
                openSubtitleEditor()
            }
        }
    }

    // =====================================================================
    // #9 / #10 Nachbearbeitung
    // =====================================================================

    private val clipDurCache = HashMap<String, Pair<Long, Int>>()

    /** Länge eines Clips in ganzen Sekunden (zwischengespeichert, bis sich die Datei ändert). */
    private fun clipSeconds(f: File): Int {
        if (!f.exists()) return 0
        clipDurCache[f.path]?.let { (mod, sec) -> if (mod == f.lastModified()) return sec }
        val sec = try {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(f.path)
            val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            Math.round(ms / 1000.0).toInt()
        } catch (e: Exception) { 0 }
        clipDurCache[f.path] = f.lastModified() to sec
        return sec
    }

    /** Sekunden, die Intro und Outro des Projekts zum fertigen Video beitragen. */
    private fun introOutroSeconds(): Int =
        if (!settings.introOutroEnabled) 0 else clipSeconds(introFile()) + clipSeconds(outroFile())

    /** Aufnahme-Limit so, dass das fertige Video (mit Intro/Outro) genau das eingestellte Limit hat. */
    private fun effectiveLimit(): Int {
        val l = settings.timeLimit
        if (l <= 0) return 0
        return (l - introOutroSeconds()).coerceAtLeast(3)
    }

    private fun introFile() = projectClip("intro")
    private fun outroFile() = projectClip("outro")

    /** Intro/Outro je Projekt; der alte gemeinsame Clip wird beim ersten Zugriff einmal übernommen. */
    private fun projectClip(kind: String): File = File(filesDir, "${kind}_${settings.projectFileKey}.mp4")

    /**
     * Einmalig aufräumen: Früher gab es ein gemeinsames Intro/Outro (intro.mp4/outro.mp4), das jedes neue
     * Projekt beim ersten Öffnen kopiert hat. Es gehört nur dem ältesten Projekt (erstes Logo);
     * Kopien in anderen Projekten, die nie selbst gewählt wurden, werden entfernt.
     */
    private fun cleanupLegacyClips() {
        if (settings.flag("clipCleanup1")) return
        val logos = (logoDir().listFiles { f -> f.name.endsWith(".png") } ?: emptyArray())
            .sortedBy { it.name.removePrefix("logo_").removeSuffix(".png").toLongOrNull() ?: it.lastModified() }
        val owner = logos.firstOrNull()?.name
        owner?.let { settings.migrateIntroOutroTo(it) }
        for (kind in listOf("intro", "outro")) {
            val legacy = File(filesDir, "$kind.mp4")
            if (!legacy.exists()) continue
            val ownerKey = owner?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (ownerKey != null) {
                val ownerFile = File(filesDir, "${kind}_$ownerKey.mp4")
                if (!ownerFile.exists()) try { legacy.copyTo(ownerFile) } catch (_: Exception) { }
            }
            logos.drop(1).forEach { logo ->
                val key = logo.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val f = File(filesDir, "${kind}_$key.mp4")
                // nur automatisch kopierte Clips (kein eigener Dateiname gemerkt, gleiche Größe) löschen
                if (f.exists() && settings.clipNameFor(kind, logo.name) == null && f.length() == legacy.length()) f.delete()
            }
            File(filesDir, "${kind}_ohne_logo.mp4").let { f ->
                if (f.exists() && f.length() == legacy.length()) f.delete()
            }
            legacy.delete()
        }
        settings.setFlag("clipCleanup1")
    }

    private fun importClip(uri: Uri, target: File, kind: String) {
        toast("Clip wird übernommen …")
        val name = try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null }
        settings.setClipName(kind, name ?: "Clip")
        bg.execute {
            val ok = try {
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
            } catch (e: Exception) { false }
            runOnUiThread {
                toast(if (ok) "Gespeichert" else "Clip konnte nicht geladen werden")
                if (settingsPanel.visibility == View.VISIBLE) buildSettings()
                projectRefresh?.invoke()
            }
        }
    }

    /** Startet Untertitel und/oder Intro/Outro für ein fertiges Video. @return true wenn gestartet. */
    private fun startPostProcessing(uri: Uri): Boolean {
        val subs = settings.subtitlesEnabled && Subtitles.isModelReady(this, settings.subtitleLang, settings.subtitleEngine) && settings.timelapse == 0
        val intro = if (settings.introOutroEnabled) introFile().takeIf { it.exists() } else null
        val outro = if (settings.introOutroEnabled) outroFile().takeIf { it.exists() } else null
        // Zusatz-Einblendungen der Bauchbinde: mit Zeitlimit live, ohne Limit erst hier (Länge steht jetzt fest)
        val lowerPost = if (settings.lowerEnabled && settings.timeLimit == 0 && settings.timelapse == 0)
            settings.lowerRepeats else emptyList()
        if (!subs && intro == null && outro == null && lowerPost.isEmpty()) return false
        post.enqueue(PostProcessor.Job(uri, recordingFolder, subs, intro, outro,
            settings.subtitleMode, settings.subtitleStyle, settings.subtitleLang,
            settings.subtitleEngine, settings.subtitleWords, settings.subtitleSkipPauses,
            lowerPost, settings.lowerName, settings.lowerTitle, settings.lowerStyle, settings.lowerSeconds))
        toast("Video gespeichert – wird jetzt bearbeitet")
        return true
    }

    private fun downloadModel(status: TextView) {
        status.text = "Sprachmodell wird geladen … 0 %"
        bg.execute {
            val ok = try {
                Subtitles.downloadModel(this, settings.subtitleLang, settings.subtitleEngine) { p -> runOnUiThread { status.text = "Sprachmodell wird geladen … $p %" } }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Modell-Download", e)
                false
            }
            runOnUiThread {
                status.text = if (ok) "Sprachmodell: bereit ✓" else "Download fehlgeschlagen – Internet prüfen"
                if (ok && settingsPanel.visibility == View.VISIBLE) buildSettings()
            }
        }
    }

    // =====================================================================
    // Spenden-Hinweis: zufällig bei jedem 5. bis 10. App-Start
    // =====================================================================

    private fun maybeShowDonateHint() {
        val prefs = getSharedPreferences("mocam_donate", MODE_PRIVATE)
        if (prefs.getBoolean("disabled", false)) return
        // Allererster Start: Popup einmal sofort zeigen, danach beginnt der Zufallszähler
        if (!prefs.getBoolean("firstShown", false)) {
            prefs.edit().putBoolean("firstShown", true).putInt("opens", 0)
                .putInt("target", kotlin.random.Random.nextInt(5, 11)).apply()
            window.decorView.postDelayed({ if (!isFinishing) showDonateDialog() }, 1500)
            return
        }
        val opens = prefs.getInt("opens", 0) + 1
        var target = prefs.getInt("target", 0)
        if (target == 0) target = kotlin.random.Random.nextInt(5, 11)
        if (opens < target) {
            prefs.edit().putInt("opens", opens).putInt("target", target).apply()
            return
        }
        prefs.edit().putInt("opens", 0).putInt("target", kotlin.random.Random.nextInt(5, 11)).apply()
        window.decorView.postDelayed({ if (!isFinishing) showDonateDialog() }, 1200)
    }

    /**
     * Spenden-Button: normales Tippen öffnet PayPal.
     * Versteckt: 10 s gedrückt halten, danach innerhalb von 6 s dreimal kurz tippen
     * -> Spenden-Popup beim Start aus (bzw. wieder an). Der Button selbst funktioniert immer.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDonateGesture(v: View) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        var downAt = 0L
        var armedAt = 0L      // Zeitpunkt, an dem die 10 s erreicht wurden
        var armedNow = false  // in diesem Druck scharf geschaltet
        var taps = 0
        // Wird nach 10 s Halten ausgelöst – noch während der Finger liegt, damit man spürt, wann man loslassen kann
        val arm = Runnable {
            armedNow = true
            armedAt = android.os.SystemClock.uptimeMillis()
            taps = 0
            vibrate(longArrayOf(0, 60, 80, 60)) // doppeltes Summen = scharf
        }
        v.setOnClickListener(null)
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downAt = e.eventTime
                    armedNow = false
                    view.parent?.requestDisallowInterceptTouchEvent(true) // Scrollen darf das Halten nicht abbrechen
                    view.isPressed = true
                    h.postDelayed(arm, 10_000L)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    h.removeCallbacks(arm)
                    view.isPressed = false
                    val held = e.eventTime - downAt
                    val now = e.eventTime
                    when {
                        armedNow -> Unit // gerade scharf geschaltet: nichts öffnen
                        e.actionMasked == android.view.MotionEvent.ACTION_CANCEL -> Unit
                        armedAt > 0L && now - armedAt <= 8_000L && held < 800L -> {
                            taps++
                            vibrate(longArrayOf(0, 35))
                            if (taps >= 3) {
                                armedAt = 0L; taps = 0
                                vibrate(longArrayOf(0, 200))
                                toggleDonatePopup()
                            }
                        }
                        else -> {
                            armedAt = 0L; taps = 0
                            view.performClick()
                            openUrl(DONATE_URL)
                        }
                    }
                }
            }
            true
        }
    }

    /** Vibration direkt über den Vibrationsmotor – unabhängig von der Huawei-Einstellung „Vibration bei Berührung“. */
    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= 31)
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            else (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator)
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            else vib.vibrate(pattern, -1)
        } catch (e: Exception) {
            Log.w(TAG, "Vibration", e)
        }
    }

    private fun toggleDonatePopup() {
        val prefs = getSharedPreferences("mocam_donate", MODE_PRIVATE)
        val disabled = !prefs.getBoolean("disabled", false)
        prefs.edit().putBoolean("disabled", disabled).putInt("opens", 0).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle(if (disabled) "🔕 Spenden-Popup deaktiviert" else "🔔 Spenden-Popup aktiviert")
            .setMessage(
                if (disabled) "Das Spenden-Popup beim App-Start wurde deaktiviert. Der Spenden-Button in den Einstellungen funktioniert weiterhin."
                else "Das Spenden-Popup erscheint wieder zufällig bei jedem 5. bis 10. App-Start."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("Kein Browser gefunden")
        }
    }

    private fun showDonateDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("💛 MoCam ist Open Source")
            .setMessage(
                "MoCam ist kostenlos, ohne Werbung und ohne Tracking. Der komplette Quellcode " +
                    "liegt offen auf GitHub.\n\nDas Projekt lebt von Spenden – jeder kleine Betrag " +
                    "hilft, neue Funktionen zu entwickeln und die App zu pflegen. Danke!"
            )
            .setPositiveButton("Spenden") { _, _ -> openUrl(DONATE_URL) }
            .setNeutralButton("GitHub") { _, _ -> openUrl(GITHUB_URL) }
            .setNegativeButton("Später", null)
            .show()
    }

    // =====================================================================
    // #13 Serienbild / Intervall, #14 Belichtungsreihe
    // =====================================================================

    private var burstActive = false
    private var burstCount = 0
    private var intervalRunning = false
    private var intervalCount = 0
    private val intervalHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun burstNext() {
        if (!burstActive) return
        takePhoto(quiet = true) {
            burstCount++
            recIndicator.visibility = View.VISIBLE
            recIndicator.text = "📸 $burstCount"
            if (burstActive) burstNext()
        }
    }

    private val intervalShot = object : Runnable {
        override fun run() {
            if (!intervalRunning) return
            takePhoto(quiet = true) {
                intervalCount++
                recIndicator.visibility = View.VISIBLE
                recIndicator.text = "⏲ $intervalCount Fotos · alle ${settings.photoInterval} s"
            }
            intervalHandler.postDelayed(this, settings.photoInterval * 1000L)
        }
    }

    private fun startInterval() {
        intervalRunning = true
        intervalCount = 0
        shutter.setBackgroundResource(R.drawable.shutter_recording)
        intervalShot.run()
    }

    private fun stopInterval() {
        intervalRunning = false
        intervalHandler.removeCallbacks(intervalShot)
        recIndicator.visibility = View.GONE
        toast("Intervall beendet: $intervalCount Fotos")
        updateUi()
    }

    /** Belichtungsreihe: 3 oder 5 Fotos mit je 1 EV Abstand (über die Belichtungskorrektur). */
    private fun runBracket() {
        val cam = camera ?: return
        val es = cam.cameraInfo.exposureState
        if (!es.isExposureCompensationSupported || proManualExposure()) {
            toast("Belichtungsreihe braucht automatische Belichtung")
            takePhoto()
            return
        }
        val step = es.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: return
        val base = es.exposureCompensationIndex
        val range = es.exposureCompensationRange
        val evs = if (settings.bracket == 5) listOf(-2, -1, 0, 1, 2) else listOf(-1, 0, 1)
        val indices = evs.map { (base + Math.round(it / step)).coerceIn(range.lower, range.upper) }
        toast("Belichtungsreihe: ${indices.size} Fotos …")
        fun shot(i: Int) {
            if (i >= indices.size) {
                cam.cameraControl.setExposureCompensationIndex(base)
                toast("Belichtungsreihe fertig")
                return
            }
            cam.cameraControl.setExposureCompensationIndex(indices[i]).addListener({
                // kurz warten, bis sich die Belichtung eingestellt hat
                intervalHandler.postDelayed({ takePhoto(quiet = i > 0) { shot(i + 1) } }, 450)
            }, mainExec)
        }
        shot(0)
    }

    // =====================================================================
    // #1–#3 Pro-Modus (Camera2)
    // =====================================================================

    private val wbPresets = listOf(
        "Auto" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO,
        "☀ Tageslicht" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
        "☁ Bewölkt" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        "🌳 Schatten" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_SHADE,
        "💡 Glühlampe" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
        "🔆 Leuchtstoff" to android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
    )

    private fun proManualExposure() = settings.proIso > 0 || settings.proShutterNs > 0L
    private fun proActive() = proManualExposure() || settings.proWb != 0 || settings.proFocus >= 0f

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun <T> characteristic(key: android.hardware.camera2.CameraCharacteristics.Key<T>): T? {
        val cam = camera ?: return null
        return try {
            androidx.camera.camera2.interop.Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(key)
        } catch (e: Exception) { null }
    }

    private fun hasCapability(cap: Int): Boolean =
        characteristic(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(cap) == true

    /** Farbtemperatur (Kelvin) -> RGB nach Tanner Helland, für die Weißabgleich-Verstärkung. */
    private fun kelvinToRgb(k: Int): FloatArray {
        val t = k / 100.0
        val r = if (t <= 66) 255.0 else (329.698727446 * Math.pow(t - 60, -0.1332047592)).coerceIn(0.0, 255.0)
        val g = if (t <= 66) (99.4708025861 * Math.log(t) - 161.1195681661).coerceIn(0.0, 255.0)
                else (288.1221695283 * Math.pow(t - 60, -0.0755148492)).coerceIn(0.0, 255.0)
        val b = when { t >= 66 -> 255.0; t <= 19 -> 0.0
            else -> (138.5177312231 * Math.log(t - 10) - 305.0447927307).coerceIn(0.0, 255.0) }
        return floatArrayOf(r.toFloat().coerceAtLeast(1f), g.toFloat().coerceAtLeast(1f), b.toFloat().coerceAtLeast(1f))
    }

    /** Überträgt ISO, Verschlusszeit, Weißabgleich und Fokus an die Kamera (Camera2-Schnittstelle). */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun applyProSettings() {
        val cam = camera ?: return
        val b = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
        if (proManualExposure()) {
            val isoRange = characteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val expRange = characteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val iso = (if (settings.proIso > 0) settings.proIso else 200).let { v -> isoRange?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            val exp = (if (settings.proShutterNs > 0) settings.proShutterNs else 16_666_666L).let { v -> expRange?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_FRAME_DURATION, maxOf(exp, 33_333_333L))
        }
        val wb = settings.proWb
        if (wb in 1 until wbPresets.size) {
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE, wbPresets[wb].second)
        } else if (wb >= 1000) {
            val rgb = kelvinToRgb(wb)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE, android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_OFF)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE, android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_GAINS,
                android.hardware.camera2.params.RggbChannelVector(rgb[1] / rgb[0], 1f, 1f, rgb[1] / rgb[2]))
        }
        if (settings.proFocus >= 0f) {
            val minDist = characteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            if (minDist > 0f) {
                b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE, android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF)
                b.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE, settings.proFocus * minDist)
            }
        }
        try {
            androidx.camera.camera2.interop.Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
        } catch (e: Exception) {
            Log.w(TAG, "Pro-Einstellungen", e)
        }
    }

    private fun shutterLabel(ns: Long): String =
        if (ns >= 1_000_000_000L) String.format("%.1f s", ns / 1e9) else "1/${Math.round(1e9 / ns)}"

    private fun openProEditor() {
        editorReturnToSettings = false
        openEditor("🎛 Pro-Modus") {
            val manualSensor = hasCapability(android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val manualPost = hasCapability(android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
            val minDist = characteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            editorContent.addView(uiText(
                "Diese Kamera erlaubt: " + listOfNotNull(
                    if (manualSensor) "ISO/Verschluss" else null,
                    if (manualPost) "Kelvin-Weißabgleich" else null,
                    if (minDist > 0f) "manueller Fokus" else null,
                    "Weißabgleich-Presets"
                ).joinToString(", "), 12f, Color.argb(200, 255, 255, 255)))

            // ---- #1 ISO & Verschluss ----
            uiSection("ISO")
            if (manualSensor) {
                val r = characteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val isos = listOf(0, 50, 100, 200, 400, 800, 1600, 3200, 6400).filter { it == 0 || r == null || it in r.lower..r.upper }
                uiChoice(isos.map { if (it == 0) "Auto" else "$it" }, isos.indexOf(settings.proIso).coerceAtLeast(0)) { i ->
                    settings.proIso = isos[i]; applyProSettings(); updateUi()
                }
                uiSection("Verschlusszeit")
                val er = characteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val times = listOf(0L, 125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L,
                    10_000_000L, 16_666_666L, 20_000_000L, 33_333_333L, 66_666_666L, 125_000_000L, 250_000_000L)
                    .filter { it == 0L || er == null || it in er.lower..er.upper }
                uiChoice(times.map { if (it == 0L) "Auto" else shutterLabel(it) }, times.indexOf(settings.proShutterNs).coerceAtLeast(0)) { i ->
                    settings.proShutterNs = times[i]; applyProSettings(); updateUi()
                }
                editorContent.addView(uiText("Tipp Film-Look: 1/50 bei 25 fps, 1/60 bei 30 fps. Länger als 1/30 ruckelt im Video.",
                    11f, Color.argb(170, 255, 255, 255)))
            } else {
                editorContent.addView(uiText("Huawei gibt ISO und Verschlusszeit für diese Kamera nicht frei. Belichtung über ☀ (EV) steuern.",
                    12f, Color.argb(200, 255, 255, 255)))
            }

            // ---- #2 Weißabgleich ----
            uiSection("Weißabgleich")
            uiChoice(wbPresets.map { it.first }, if (settings.proWb in wbPresets.indices) settings.proWb else -1) { i ->
                settings.proWb = i; applyProSettings(); updateUi()
            }
            if (manualPost) {
                val kelvin = if (settings.proWb >= 1000) settings.proWb else 5500
                uiSlider("Kelvin ${kelvin} K", 0f, 1f, (kelvin - 2000) / 8000f) { v ->
                    settings.proWb = (2000 + v * 8000).toInt() / 100 * 100
                    applyProSettings(); updateUi()
                }
            }

            // ---- #3 Fokus ----
            uiSection("Fokus")
            if (minDist > 0f) {
                uiChoice(listOf("Autofokus", "Manuell"), if (settings.proFocus < 0f) 0 else 1) { i ->
                    settings.proFocus = if (i == 0) -1f else 0.5f
                    applyProSettings(); updateUi()
                    openProEditor()
                }
                if (settings.proFocus >= 0f) {
                    uiSlider("Unendlich ↔ Nah", 0f, 1f, settings.proFocus) { v ->
                        settings.proFocus = v; applyProSettings()
                    }
                }
            } else {
                editorContent.addView(uiText("Manueller Fokus wird von dieser Kamera nicht unterstützt.", 12f, Color.argb(200, 255, 255, 255)))
            }
            editorContent.addView(SwitchCompat(this).apply {
                text = "Fokus-Peaking (scharfe Kanten rot)"
                setTextColor(Color.WHITE)
                isChecked = settings.peaking
                setOnCheckedChangeListener { _, c -> settings.peaking = c; fxState.peaking = c }
            })

            // ---- Ringlicht (Frontkamera) ----
            uiSection("💡 Ringlicht (Frontkamera)")
            uiChoice(listOf("Automatisch", "Manuell"), if (settings.lightAuto) 0 else 1) { i ->
                if (lightMode) setLightAuto(i == 0) else settings.lightAuto = i == 0
            }
            editorContent.addView(uiText(
                "Automatisch: Das Display wird so hell wie nötig, damit die Kamera mit niedrigem ISO arbeitet " +
                    "(rauscharmes Bild). Bei manuellem ISO regelt es nach der Helligkeit in der Bildmitte. " +
                    "Tipp: 📊 Histogramm einschalten – Berg ganz rechts = Gesicht zu hell.",
                11f, Color.argb(170, 255, 255, 255)))

            uiButtons {
                settings.proIso = 0; settings.proShutterNs = 0L; settings.proWb = 0; settings.proFocus = -1f
                applyProSettings(); updateUi()
                openProEditor()
            }
        }
    }

    // =====================================================================
    // #4 AE/AF-Sperre, #6 Histogramm, #7 Wasserwaage, #8 Marker
    // =====================================================================

    private fun lockAt(x: Float, y: Float) {
        val cam = camera ?: return
        val px = if (previewView.scaleX < 0f) previewView.width - x else x
        val point = previewView.meteringPointFactory.createPoint(px, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        ).disableAutoCancel().build()
        cam.cameraControl.startFocusAndMetering(action)
        overlay.showLock(x, y)
        toast("AE/AF gesperrt – einmal tippen hebt die Sperre auf")
    }

    private val histoTicker = object : Runnable {
        override fun run() {
            if (!settings.histogram) return
            val bmp = try { previewView.bitmap } catch (e: Exception) { null }
            if (bmp != null) bg.execute {
                val small = Bitmap.createScaledBitmap(bmp, 96, (96f * bmp.height / bmp.width).toInt().coerceAtLeast(1), false)
                bmp.recycle()
                val px = IntArray(small.width * small.height)
                small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                small.recycle()
                val bins = FloatArray(64)
                for (c in px) {
                    val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 256f
                    bins[(lum * 64).toInt().coerceIn(0, 63)] += 1f
                }
                val max = bins.maxOrNull()?.takeIf { it > 0f } ?: 1f
                for (i in bins.indices) bins[i] = bins[i] / max
                runOnUiThread { if (settings.histogram) overlay.histogram = bins }
            }
            overlay.postDelayed(this, 400)
        }
    }

    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager }
    private var smoothAngle = 0f
    private val levelListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val a = Math.toDegrees(kotlin.math.atan2(-e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
            smoothAngle = smoothAngle * 0.8f + a * 0.2f
            overlay.levelAngle = smoothAngle
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }

    private fun applyLevelSensor() {
        sensorManager.unregisterListener(levelListener)
        if (settings.level) {
            val s = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            if (s != null) sensorManager.registerListener(levelListener, s, android.hardware.SensorManager.SENSOR_DELAY_UI)
        } else {
            overlay.levelAngle = null
        }
    }

    private fun addMarker() {
        markerTimes += lastRecordedNs
        val s = lastRecordedNs / 1_000_000_000.0
        toast(String.format("🔖 Marker %d bei %d:%04.1f", markerTimes.size, (s / 60).toInt(), s % 60))
    }

    /** Marker als Textdatei neben das Video (Download/MoCam/<Ordner>, Name wie das Video). */
    private fun saveMarkers(video: Uri) {
        if (markerTimes.isEmpty()) return
        val times = markerTimes.toList()
        val folder = recordingFolder
        bg.execute {
            val name = try {
                contentResolver.query(video, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: Exception) { null } ?: MediaStoreUtil.fileName("MoCam")
            val text = buildString {
                append("Marker für ").append(name).append('\n')
                times.forEachIndexed { i, ns ->
                    val s = ns / 1_000_000_000.0
                    append(String.format(java.util.Locale.GERMANY, "%d. %02d:%04.1f%n", i + 1, (s / 60).toInt(), s % 60))
                }
            }
            val ok = MediaStoreUtil.saveTextToDownloads(this, name.substringBeforeLast('.') + "_Marker.txt", folder, text)
            runOnUiThread { if (ok) toast("${times.size} Marker gespeichert (Download/MoCam)") }
        }
    }

    // =====================================================================
    // #16 Sprachauslöser, #17 Restaufnahmezeit / Akku
    // =====================================================================

    private fun startVoice(): Boolean {
        if (!granted(Manifest.permission.RECORD_AUDIO)) return false
        return voice.start()
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            updateStatus()
            statusText.postDelayed(this, 15000)
        }
    }

    /** Freier Speicher in Aufnahmezeit (Video) bzw. Fotoanzahl, dazu Akkustand. */
    private fun updateStatus() {
        if (!::statusText.isInitialized) return
        val free = try {
            android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        } catch (e: Exception) { 0L }
        val battery = (getSystemService(BATTERY_SERVICE) as android.os.BatteryManager)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val space = if (videoMode) {
            val mbps = when {
                settings.bitrateMbps > 0 -> settings.bitrateMbps.toDouble()
                else -> when (settings.qualityIndex) { 0 -> 45.0; 1 -> 17.0; 2 -> 9.0; else -> 4.0 }
            } + 0.2
            val minutes = (free * 8 / (mbps * 1_000_000) / 60).toLong()
            val factor = if (settings.timelapse > 1) settings.timelapse else 1
            val real = minutes * factor
            "💾 ≈ " + (if (real >= 60) "${real / 60} h ${real % 60} min" else "$real min") +
                (if (factor > 1) " (Zeitraffer)" else "")
        } else {
            "💾 ≈ ${String.format("%,d", free / (4L * 1024 * 1024)).replace(',', '.')} Fotos"
        }
        statusText.text = "$space · 🔋 $battery %"
    }

    // =====================================================================
    // #6 Bauchbinde
    // =====================================================================

    private fun applyLowerThird() {
        val st = settings.lowerStyle
        fxState.lowerX = st.x
        fxState.lowerY = st.y
        fxState.lowerW = st.width
        overlay.lowerStyle = st
        val bmp = LowerThird.render(settings.lowerName, settings.lowerTitle, st)
        videoProcessor.setLowerThird(bmp)
        overlay.lowerBitmap = bmp
        val dur = settings.lowerSeconds * 1000L
        fxState.lowerDurationMs = dur
        overlay.lowerDurationMs = dur
    }

    private fun startLowerThird() {
        val now = android.os.SystemClock.uptimeMillis()
        fxState.lowerStartMs = now
        overlay.lowerStartMs = now
    }

    private val lowerFired = HashSet<Int>()

    /** Mit Zeitlimit: Bauchbinde live bei x % der Aufnahmezeit erneut einblenden (Pausen werden berücksichtigt). */
    private fun checkLowerRepeats(recSec: Double) {
        if (!settings.lowerEnabled || settings.timeLimit == 0) return
        val total = effectiveLimit().toDouble()
        val dur = settings.lowerSeconds.toDouble()
        settings.lowerRepeats.forEachIndexed { i, pct ->
            val start = (pct / 100.0 * total).coerceIn(0.0, maxOf(0.0, total - dur))
            if (i !in lowerFired && recSec >= start && start > 0.5) {
                lowerFired += i
                startLowerThird()
            }
        }
    }

    private val gifFired = HashSet<Int>()

    /** Animiertes Logo erneut abspielen bei x % der Aufnahmezeit (nur mit Zeitlimit, Länge muss feststehen). */
    private fun checkGifRepeats(recSec: Double) {
        if (settings.timeLimit == 0 || animFor(settings.selectedLogo) == null) return
        val total = effectiveLimit().toDouble()
        settings.gifRepeats.forEachIndexed { i, pct ->
            val start = (pct / 100.0 * total).coerceIn(0.0, maxOf(0.0, total - 1.0))
            if (i !in gifFired && recSec >= start && start > 0.5) {
                gifFired += i
                playLogoAnimation()
            }
        }
    }

    private fun stopLowerThird() {
        fxState.lowerStartMs = 0L
        overlay.lowerStartMs = 0L
    }

    /** Nur Anzeige zum Ausprobieren – wird nicht aufgenommen. */
    private fun previewLowerThird() {
        overlay.lowerStartMs = android.os.SystemClock.uptimeMillis()
    }

    // =====================================================================
    // #8 Mikrofon
    // =====================================================================

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as android.media.AudioManager }
    private var btMicActive = false

    private fun inputDevicesText(): String {
        val names = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).map {
            when (it.type) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Intern"
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kabel-Headset"
                else -> null
            }
        }.filterNotNull().distinct()
        return if (names.isEmpty()) "Intern" else names.joinToString(", ")
    }

    @Suppress("DEPRECATION")
    private fun startBluetoothMic() {
        try {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            btMicActive = true
        } catch (e: Exception) {
            toast("Bluetooth-Mikrofon nicht verfügbar – internes Mikrofon")
        }
    }

    @Suppress("DEPRECATION")
    private fun stopBluetoothMic() {
        if (!btMicActive) return
        try {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        } catch (e: Exception) { }
        btMicActive = false
    }

    // =====================================================================
    // #2 Projektordner (pro Logo)
    // =====================================================================

    /** Ordner der aktuellen Aufnahme: der des gewählten Logos, wenn das Logo an ist. */
    private fun currentFolder(): String =
        if (settings.logoEnabled && logoBitmap != null) settings.folderForLogo(settings.selectedLogo) else ""

    private fun folderLabel(f: String) = if (f.isBlank()) "MoCam" else "MoCam/$f"

    private fun updateFolderLabel() {
        val f = settings.folderForLogo(settings.selectedLogo)
        findViewById<TextView>(R.id.logoFolderLabel).text = "📁 Ordner: " + folderLabel(f)
        projectRefresh?.invoke()
    }

    private fun chooseFolderForLogo() {
        val logo = settings.selectedLogo
        if (logo == null) {
            toast("Bitte zuerst ein Logo wählen.")
            return
        }
        val folders = settings.folders.sorted()
        val items = (listOf("MoCam (kein Unterordner)") + folders.map { "📁 $it" } + listOf("+ Neuer Ordner …")).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Ordner für dieses Logo")
            .setItems(items) { _, which ->
                when {
                    which == 0 -> { settings.setFolderForLogo(logo, ""); updateFolderLabel() }
                    which <= folders.size -> { settings.setFolderForLogo(logo, folders[which - 1]); updateFolderLabel() }
                    else -> createFolder(logo)
                }
            }
            .show()
    }

    private fun createFolder(logo: String) {
        val input = EditText(this).apply {
            hint = "z. B. MMES oder Kunde Müller"
            setSingleLine()
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Neuer Ordner")
            .setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Anlegen") { _, _ ->
                val name = MediaStoreUtil.sanitizeFolder(input.text.toString())
                if (name.isBlank()) {
                    toast("Bitte einen Namen eingeben.")
                } else {
                    settings.folders = settings.folders + name
                    settings.setFolderForLogo(logo, name)
                    updateFolderLabel()
                    toast("Ordner „$name“ angelegt – Aufnahmen mit diesem Logo landen in MoCam/$name")
                }
            }
            .show()
    }

    // =====================================================================
    // #3 Direkt teilen
    // =====================================================================

    private var shareUri: Uri? = null
    private var shareIsVideo = false
    private val hideShareBar = Runnable { shareBar.visibility = View.GONE }

    private fun buildShareButtons() {
        shareButtons.removeAllViews()
        listOf(
            "🟢 WhatsApp Status" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "📸 Instagram" to listOf("com.instagram.android"),
            "💼 LinkedIn" to listOf("com.linkedin.android"),
            "📌 Pinterest" to listOf("com.pinterest"),
            "⋯ Mehr" to emptyList()
        ).forEach { (label, pkgs) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener { shareTo(pkgs) }
            }
            shareButtons.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) })
        }
    }

    private fun showShareBar(uri: Uri, isVideo: Boolean) {
        shareUri = uri
        shareIsVideo = isVideo
        shareBar.visibility = View.VISIBLE
        shareBar.removeCallbacks(hideShareBar)
        shareBar.postDelayed(hideShareBar, 12000)
    }

    private fun shareTo(packages: List<String>) {
        val uri = shareUri ?: return
        val base = Intent(Intent.ACTION_SEND).apply {
            type = if (shareIsVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareBar.visibility = View.GONE
        if (packages.isEmpty()) {
            startActivity(Intent.createChooser(base, "Teilen"))
            return
        }
        for (pkg in packages) {
            try {
                startActivity(Intent(base).setPackage(pkg))
                return
            } catch (e: ActivityNotFoundException) {
                // nächstes Paket probieren
            }
        }
        toast("App nicht installiert – öffne Auswahl")
        startActivity(Intent.createChooser(base, "Teilen"))
    }

    private fun openLastMedia() {
        val uri = lastMediaUri
        val intent = if (uri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (lastMediaIsVideo) "video/*" else "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Keine Galerie-App gefunden.")
        }
    }

    // =====================================================================
    // UI-Zustand
    // =====================================================================

    // =====================================================================
    // Ringlicht (Frontkamera): Display hell, Flächen weiß, Knöpfe mit Kontrast
    // =====================================================================

    private var lightMode = false
    @Volatile private var currentIso = 0
    private var lightSeek: SeekBar? = null
    private var lightLabel: TextView? = null
    private var lightAutoChip: TextView? = null
    private var lightLevelNow = 100

    /**
     * Ringlicht-Automatik: hält den ISO-Wert der Kamera niedrig (sauberes, rauscharmes Bild) mit so wenig
     * Display-Licht wie nötig. Bei manueller Belichtung (fester ISO) regelt sie stattdessen nach der
     * Bildhelligkeit in der Bildmitte (Gesicht).
     */
    private val lightAutoTicker = object : Runnable {
        override fun run() {
            if (!lightMode || !settings.lightAuto) return
            var step = 0
            if (!proManualExposure() && currentIso > 0) {
                step = when {
                    currentIso > 400 -> +6
                    currentIso > 250 -> +3
                    currentIso < 100 -> -4
                    currentIso < 150 -> -2
                    else -> 0
                }
                applyLightStep(step)
            } else {
                val bmp = try { previewView.bitmap } catch (e: Exception) { null }
                if (bmp != null) bg.execute {
                    val w = bmp.width; val h = bmp.height
                    var sum = 0.0; var n = 0
                    var y = h / 4
                    while (y < h * 3 / 4) {
                        var x = w / 4
                        while (x < w * 3 / 4) {
                            val c = bmp.getPixel(x, y)
                            sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
                            n++; x += 8
                        }
                        y += 8
                    }
                    bmp.recycle()
                    val lum = if (n > 0) sum / n / 255.0 else 0.5
                    val st = when { lum < 0.35 -> +5; lum < 0.42 -> +2; lum > 0.62 -> -5; lum > 0.55 -> -2; else -> 0 }
                    runOnUiThread { applyLightStep(st) }
                }
            }
            previewView.postDelayed(this, 600)
        }
    }

    private fun applyLightStep(step: Int) {
        if (step == 0 || !lightMode) return
        val level = (lightLevelNow + step).coerceIn(20, 100)
        if (level == lightLevelNow) return
        setLightLevelUi(level)
    }

    private fun setLightLevelUi(level: Int) {
        lightLevelNow = level
        applyBrightness(level)
        lightSeek?.progress = level - 5
        lightLabel?.text = (if (settings.lightAuto) "💡 Auto " else "💡 ") + "$level %"
    }

    private fun setLightAuto(auto: Boolean) {
        settings.lightAuto = auto
        lightAutoChip?.text = if (auto) "Auto" else "Manuell"
        lightSeek?.alpha = if (auto) 0.45f else 1f
        if (auto) {
            previewView.removeCallbacks(lightAutoTicker)
            lightAutoTicker.run()
        } else {
            previewView.removeCallbacks(lightAutoTicker)
            setLightLevelUi(settings.lightLevel)
        }
        setLightLevelUi(lightLevelNow)
    }
    private val lightSaved = HashMap<View, Pair<android.graphics.drawable.Drawable?, Int?>>()
    private var lightBar: LinearLayout? = null

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun attachIsoReader(b: Preview.Builder) {
        androidx.camera.camera2.interop.Camera2Interop.Extender(b).setSessionCaptureCallback(
            object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: android.hardware.camera2.CameraCaptureSession,
                    request: android.hardware.camera2.CaptureRequest,
                    result: android.hardware.camera2.TotalCaptureResult
                ) {
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)?.let { currentIso = it }
                }
            })
    }

    private fun setLightMode(on: Boolean) {
        if (on == lightMode) return
        lightMode = on
        val root = findViewById<View>(R.id.root)
        val topBar = findViewById<View>(R.id.topBar)
        if (on) {
            // Original-Hintergründe als Kopie sichern (setBackgroundColor würde das Original überschreiben)
            for (v in listOf(root, topBar, bottomPanel)) if (!lightSaved.containsKey(v))
                lightSaved[v] = v.background?.constantState?.newDrawable()?.mutate() to null
            root.background = android.graphics.drawable.ColorDrawable(Color.WHITE)
            topBar.background = android.graphics.drawable.ColorDrawable(Color.argb(245, 255, 255, 255))
            bottomPanel.background = android.graphics.drawable.ColorDrawable(Color.argb(245, 255, 255, 255))
            styleLight(topBar)
            styleLight(bottomPanel)
            lightLevelNow = settings.lightLevel
            showLightBar()
            setLightAuto(settings.lightAuto)
        } else {
            for ((v, saved) in lightSaved) {
                v.background = saved.first
                if (v is TextView && saved.second != null) v.setTextColor(saved.second!!)
            }
            lightSaved.clear()
            previewView.removeCallbacks(lightAutoTicker)
            lightBar?.let { (it.parent as? ViewGroup)?.removeView(it) }
            lightBar = null
            lightSeek = null; lightLabel = null; lightAutoChip = null
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        updateUi()
    }

    private val chipState by lazy { getDrawable(R.drawable.chip_bg)?.constantState }
    private val roundState by lazy { getDrawable(R.drawable.round_btn)?.constantState }
    private val chipLightState by lazy { getDrawable(R.drawable.chip_light)?.constantState }
    private val roundLightState by lazy { getDrawable(R.drawable.round_light)?.constantState }

    /** Färbt alle Knöpfe und Texte eines Bereichs für den hellen Ringlicht-Modus um. */
    private fun styleLight(v: View) {
        if (v === lightBar || v.id == R.id.shutter || v.id == R.id.galleryThumb) return
        if (v is ViewGroup) for (i in 0 until v.childCount) styleLight(v.getChildAt(i))
        if (v !is TextView) return
        val bg = v.background?.constantState
        val dark = Color.rgb(17, 17, 17)
        when {
            bg == chipState || bg == roundState -> {
                if (!lightSaved.containsKey(v)) lightSaved[v] = v.background to v.currentTextColor
                v.setBackgroundResource(if (bg == roundState) R.drawable.round_light else R.drawable.chip_light)
                v.setTextColor(dark)
            }
            bg == chipLightState || bg == roundLightState -> v.setTextColor(dark)
            v.background == null -> {
                if (!lightSaved.containsKey(v)) lightSaved[v] = null to v.currentTextColor
                val c = v.currentTextColor
                val accent = Color.red(c) > 200 && Color.green(c) > 150 && Color.blue(c) < 140
                v.setTextColor(if (accent) Color.rgb(200, 80, 0) else dark)
            }
        }
    }

    private fun applyBrightness(level: Int) {
        window.attributes = window.attributes.apply { screenBrightness = (level.coerceIn(5, 100)) / 100f }
    }

    /** Schieberegler für die Display-Helligkeit, direkt über der unteren Leiste. */
    private fun showLightBar() {
        val root = findViewById<FrameLayout>(R.id.root)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.chip_light)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            // keine Erhöhung: der Regler liegt unter den Menüs (Einstellungen usw.)
        }
        val label = TextView(this).apply {
            text = "💡 ${settings.lightLevel} %"
            textSize = 14f
            setTextColor(Color.rgb(17, 17, 17))
            minWidth = dp(96)
        }
        val seek = SeekBar(this).apply {
            max = 95
            progress = settings.lightLevel - 5
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(40, 40, 40))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 160, 0))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    // Regler anfassen = manuell
                    if (settings.lightAuto) setLightAuto(false)
                    settings.lightLevel = p + 5
                    setLightLevelUi(p + 5)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        val close = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.rgb(17, 17, 17))
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { setLightMode(false) }
        }
        val autoChip = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(17, 17, 17))
            setBackgroundResource(R.drawable.chip_light)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setOnClickListener { setLightAuto(!settings.lightAuto) }
        }
        bar.addView(autoChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        bar.addView(label)
        bar.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        lightSeek = seek; lightLabel = label; lightAutoChip = autoChip
        bar.addView(close)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(8)) }
        // direkt unter der unteren Leiste einsortieren → alle Menüs/Panels liegen darüber
        root.addView(bar, root.indexOfChild(bottomPanel).let { if (it < 0) root.childCount else it }, lp)
        lightBar = bar
        // über der unteren Leiste platzieren (deren Höhe ändert sich je nach Modus)
        val place = { lp.bottomMargin = bottomPanel.height + dp(8); bar.layoutParams = lp }
        bottomPanel.post(place)
        bottomPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (lightBar === bar) bar.post(place) }
    }

    private fun updateUi() {
        val rec = recording != null

        modePhoto.setTextColor(if (!videoMode) getColor(R.color.accent) else Color.WHITE)
        modeVideo.setTextColor(if (videoMode) getColor(R.color.accent) else Color.WHITE)

        shutter.setBackgroundResource(
            when {
                rec || intervalRunning -> R.drawable.shutter_recording
                videoMode -> R.drawable.shutter_video
                else -> R.drawable.shutter_photo
            }
        )

        btnFlash.text = when {
            videoMode -> if (flashMode == FLASH_ON) "🔦 Licht an" else "🔦 Licht aus"
            flashMode == FLASH_ON -> "⚡ Blitz an"
            flashMode == FLASH_AUTO -> "⚡ Auto"
            else -> "⚡ Blitz aus"
        }
        btnTimer.text = if (settings.timer == 0) "⏱ Timer aus" else "⏱ ${settings.timer}s"
        btnLimit.text = when {
            settings.timeLimit == 0 -> "⏳ Limit aus"
            introOutroSeconds() > 0 -> "⏳ ${settings.timeLimit}s (Aufnahme ${effectiveLimit()}s)"
            else -> "⏳ ${settings.timeLimit}s"
        }
        btnLimit.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnFps.text = if (settings.fps == 0) "🎞 fps auto" else "🎞 ${settings.fps} fps"
        btnFps.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnBitrate.text = if (settings.bitrateMbps == 0) "📶 Bitrate auto" else "📶 ${settings.bitrateMbps} Mbit/s"
        btnBitrate.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnVoice.text = if (settings.voiceTrigger) "🗣 Sprache an" else "🗣 Sprache aus"
        updateStatus()
        btnLower.text = if (settings.lowerEnabled) "🪪 Bauchbinde an" else "🪪 Bauchbinde aus"
        btnLower.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnTimelapse.text = if (settings.timelapse == 0) "⏩ Zeitraffer aus" else "⏩ Zeitraffer ${settings.timelapse}x"
        btnTimelapse.visibility = if (videoMode) View.VISIBLE else View.GONE
        audioLevel.visibility = if (rec && settings.audio && settings.timelapse == 0) View.VISIBLE else View.GONE
        btnGrid.text = if (settings.grid) "▦ Raster an" else "▦ Raster aus"
        btnZebra.text = if (settings.zebra) "🦓 Zebra an" else "🦓 Zebra aus"
        btnPro.text = if (proActive()) "🎛 Pro aktiv" else "🎛 Pro"
        btnInterval.text = if (settings.photoInterval == 0) "⏲ Intervall aus" else "⏲ alle ${settings.photoInterval} s"
        btnInterval.visibility = if (videoMode) View.GONE else View.VISIBLE
        btnBracket.text = if (settings.bracket == 0) "🎚 Reihe aus" else "🎚 Reihe ${settings.bracket}×"
        btnBracket.visibility = if (videoMode) View.GONE else View.VISIBLE
        btnHisto.text = if (settings.histogram) "📊 Histogramm an" else "📊 Histogramm aus"
        btnLevel.text = if (settings.level) "📐 Wasserwaage an" else "📐 Wasserwaage aus"
        btnMarkers.text = if (settings.markers) "🔖 Marker an" else "🔖 Marker aus"
        btnMarkers.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnMarker.visibility = if (rec && settings.markers) View.VISIBLE else View.GONE
        btnQuality.text = "🎞 " + qualities[settings.qualityIndex.coerceIn(0, qualities.size - 1)].second
        btnAudio.text = if (settings.audio) "🎤 Ton an" else "🔇 Ton aus"
        btnExtension.text = "✨ " + extensionName(extensionMode)
        btnLogo.text = if (settings.logoEnabled) "🏷 Logo an" else "🏷 Logo aus"
        btnFormat.text = "▭ " + currentFormat().label
        btnSettings.text = "⚙️"
        btnPrompter.text = if (prompterActive()) "📜 Prompter an" else "📜 Prompter"
        btnPrompter.visibility = if (videoMode) View.VISIBLE else View.GONE

        btnQuality.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnAudio.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnExtension.visibility =
            if (!videoMode && availableExtensions.size > 1) View.VISIBLE else View.GONE

        recIndicator.visibility = if (rec) View.VISIBLE else View.GONE
        btnPause.visibility = if (rec && settings.timelapse == 0) View.VISIBLE else View.GONE
        btnPause.text = if (isPaused) "▶" else "⏸"
        galleryThumb.visibility = if (rec) View.GONE else View.VISIBLE
        btnSwitch.alpha = 1f
        modePhoto.alpha = if (rec) 0.3f else 1f
        modeVideo.alpha = if (rec) 0.3f else 1f
        topBar.alpha = if (rec) 0.6f else 1f

        // Ringlicht nur bei der Frontkamera
        // Frontkamera: nur 💡 Licht (Display als Ringlicht) · Rückkamera: nur ⚡ Blitz / 🔦 Taschenlampe
        // Frontkamera: 💡 Ringlicht (Display) · Rückkamera: 🔦 Kameralicht + ⚡ Blitz (nur Foto)
        btnLight.visibility = if (isFrontCam || hasTorch()) View.VISIBLE else View.GONE
        btnBackground.text = if (settings.bgEnabled) "🖼 Hintergrund an" else "🖼 Hintergrund aus"
        btnFlash.visibility = if (isFrontCam || videoMode) View.GONE else View.VISIBLE
        btnLight.text = if (isFrontCam) { if (lightMode) "💡 Licht an" else "💡 Licht" }
            else if (torchOn) "🔦 Licht an" else "🔦 Licht aus"
        if (lightMode && !isFrontCam) { setLightMode(false); return }
        if (lightMode) { styleLight(topBar); styleLight(bottomPanel) }
    }

    private fun extensionName(mode: Int) = when (mode) {
        ExtensionMode.AUTO -> "Auto-Szene"
        ExtensionMode.HDR -> "HDR"
        ExtensionMode.NIGHT -> "Nacht"
        ExtensionMode.BOKEH -> "Porträt"
        ExtensionMode.FACE_RETOUCH -> "Beauty"
        else -> "Standard"
    }

    // =====================================================================
    // Hilfen
    // =====================================================================

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        return list.toTypedArray()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasAllPermissions() = requiredPermissions().all { granted(it) }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).roundToInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private abstract class SimpleSeek : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    companion object {
        const val DONATE_URL = "https://www.paypal.com/donate/?hosted_button_id=PFE8KWFRX3F7S"
        const val GITHUB_URL = "https://github.com/MoManufaktur/MoCam"
        private const val TAG = "MoCam"
        private const val FLASH_OFF = 0
        private const val FLASH_ON = 1
        private const val FLASH_AUTO = 2
    }
}
