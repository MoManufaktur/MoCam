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
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var audioLevel: android.widget.ProgressBar
    private lateinit var shareBar: View
    private lateinit var shareButtons: LinearLayout
    private lateinit var btnGrid: TextView
    private lateinit var btnQuality: TextView
    private lateinit var btnAudio: TextView
    private lateinit var btnExtension: TextView
    private lateinit var btnLogo: TextView
    private lateinit var btnFormat: TextView
    private lateinit var btnSettings: TextView
    private lateinit var settingsPanel: View
    private lateinit var settingsContent: LinearLayout
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

    private val pickLogo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importLogo(uri)
        }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        mainExec = ContextCompat.getMainExecutor(this)
        settings = Settings(this)
        processor = GlEffectProcessor(fxState, burnLogo = false)
        videoProcessor = GlEffectProcessor(fxState, burnLogo = true)

        bindViews()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.close()
        cameraProvider?.unbindAll()
        processor.release()
        videoProcessor.release()
        sound.release()
        bg.shutdown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
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
        audioLevel = findViewById(R.id.audioLevel)
        shareBar = findViewById(R.id.shareBar)
        shareButtons = findViewById(R.id.shareButtons)
        btnGrid = findViewById(R.id.btnGrid)
        btnQuality = findViewById(R.id.btnQuality)
        btnAudio = findViewById(R.id.btnAudio)
        btnExtension = findViewById(R.id.btnExtension)
        btnLogo = findViewById(R.id.btnLogo)
        btnFormat = findViewById(R.id.btnFormat)
        btnSettings = findViewById(R.id.btnSettings)
        settingsPanel = findViewById(R.id.settingsPanel)
        settingsContent = findViewById(R.id.settingsContent)
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
            if (settings.timeLimit > 0) toast("Video stoppt automatisch nach ${settings.timeLimit} s")
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
            if (settingsPanel.visibility == View.VISIBLE) closeSettings() else openSettings()
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

        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build())
            .build()
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
            val vc = runningVc ?: run {
                val q = qualities[settings.qualityIndex.coerceIn(0, qualities.size - 1)].first
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                    .setAspectRatio(if (fmt.wide) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3)
                    .build()
                VideoCapture.withOutput(recorder)
            }
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
        applyPreviewMirror()
        updateUi()
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
        val front = isFrontCam

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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("⚙️ Grundeinstellungen – " + logoContextName(), 17f, getColor(R.color.accent), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(chip("⟲ Kamera wechseln") {
            if (recording == null) {
                switchCamera()
                settingsContent.postDelayed({ buildSettings() }, 300)
            }
        })
        settingsContent.addView(header)

        settingsContent.addView(text(
            "Logo-Position, Größe und Drehung gelten je Kamera und Modus.",
            12f, Color.argb(200, 255, 255, 255)
        ).apply { setPadding(0, dp(6), 0, dp(8)) })

        if (front) {
            val sw = SwitchCompat(this).apply {
                text = "Automatisch spiegeln (wie ein Spiegel)"
                setTextColor(Color.WHITE)
                isChecked = settings.frontMirror
                setOnCheckedChangeListener { _, checked ->
                    settings.frontMirror = checked
                    applyCameraFixes()
                    applyPreviewMirror()
                }
            }
            settingsContent.addView(sw)
        }

        settingsContent.addView(chip("🏷 Logo einstellen – " + logoContextName()) {
            openLogoPanel(fromSettings = true)
        }.apply { setPadding(dp(14), dp(10), dp(14), dp(10)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        settingsContent.addView(text("Video-Ausrichtung", 14f, getColor(R.color.accent), true).apply { setPadding(0, dp(12), 0, 0) })

        settingsContent.addView(text(
            "Vorschau und Foto brauchen keine Einstellung – das Logo liegt dort als Ebene über dem Bild. " +
            "Fürs Video: Testvideo aufnehmen. Ist das Bild gegenüber der Vorschau gespiegelt → „Bild ⇋“. " +
            "Steht das Logo anders als in der Vorschau → „Logo ↻ / ⇋“, bis es passt.",
            12f, Color.argb(200, 255, 255, 255)
        ).apply { setPadding(0, dp(4), 0, 0) })

        listOf(
            Settings.TARGET_VIDEO to "Video-Aufnahme"
        ).forEach { (target, name) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            val value = text(name + "\n" + fixLabel(settings.getFix(front, target)), 13f)
            row.addView(value, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            fun update(v: Int) {
                settings.setFix(front, target, v)
                applyCameraFixes()
                value.text = name + "\n" + fixLabel(v)
            }
            row.addView(chip("Logo ↻ 90°") {
                val v = settings.getFix(front, target)
                update((v / 4) * 4 + (v % 4 + 1) % 4)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
            row.addView(chip("Logo ⇋") {
                val v = settings.getFix(front, target)
                update((v + 4) % 8)
            })
            settingsContent.addView(row)
        }

        // Bild der Video-Aufnahme links/rechts tauschen (falls das Video gegenüber der Vorschau gespiegelt ist)
        val flipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val flipText = text("Video-Bild\n" + if (settings.getVideoFlip(front)) "gespiegelt" else "normal", 13f)
        flipRow.addView(flipText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        flipRow.addView(chip("Bild ⇋") {
            val v = !settings.getVideoFlip(front)
            settings.setVideoFlip(front, v)
            applyCameraFixes()
            flipText.text = "Video-Bild\n" + if (v) "gespiegelt" else "normal"
        })
        settingsContent.addView(flipRow)

        // ---------- #6 Bauchbinde ----------
        settingsContent.addView(text("🪪 Bauchbinde (Video)", 14f, getColor(R.color.accent), true).apply { setPadding(0, dp(16), 0, 0) })
        fun field(hintText: String, value: String, onChange: (String) -> Unit) = EditText(this).apply {
            setText(value)
            hint = hintText
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) {
                    onChange(e?.toString() ?: "")
                    applyLowerThird()
                }
            })
        }
        settingsContent.addView(field("Name", settings.lowerName) { settings.lowerName = it })
        settingsContent.addView(field("Funktion / Firma", settings.lowerTitle) { settings.lowerTitle = it })
        val lowerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        lowerRow.addView(chip("Dauer: ${settings.lowerSeconds} s") {}.also { d ->
            d.setOnClickListener {
                settings.lowerSeconds = when (settings.lowerSeconds) { 3 -> 5; 5 -> 8; 8 -> 10; else -> 3 }
                d.text = "Dauer: ${settings.lowerSeconds} s"
                applyLowerThird()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        lowerRow.addView(chip("👁 Vorschau") { previewLowerThird() })
        settingsContent.addView(lowerRow)

        // ---------- #8 Mikrofon ----------
        settingsContent.addView(text("🎤 Mikrofon", 14f, getColor(R.color.accent), true).apply { setPadding(0, dp(16), 0, 0) })
        settingsContent.addView(text("Erkannt: " + inputDevicesText(), 12f, Color.argb(200, 255, 255, 255)))
        val micRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        fun micLabel() = if (settings.micMode == "bluetooth") "Quelle: Bluetooth-Headset" else "Quelle: Automatisch (intern / USB)"
        micRow.addView(chip(micLabel()) {}.also { m ->
            m.setOnClickListener {
                settings.micMode = if (settings.micMode == "bluetooth") "auto" else "bluetooth"
                m.text = micLabel()
            }
        })
        settingsContent.addView(micRow)
        settingsContent.addView(text("USB-Mikrofone werden automatisch genutzt. Der Pegel erscheint während der Aufnahme oben (rot = zu laut).",
            11f, Color.argb(170, 255, 255, 255)).apply { setPadding(0, dp(4), 0, 0) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        buttons.addView(chip("Zurücksetzen") {
            settings.setFix(front, Settings.TARGET_VIDEO, 0)
            settings.setVideoFlip(front, front)
            applyCameraFixes()
            buildSettings()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        buttons.addView(chip("🖼 Letzte Aufnahme ansehen") { openLastMedia() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        buttons.addView(chip("✓ Fertig") { closeSettings() })
        settingsContent.addView(buttons)
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
        findViewById<View>(R.id.btnPrompterClose).setOnClickListener {
            prompterView.playing = false
            prompterBox.visibility = View.GONE
            updateUi()
        }
    }

    /** Regler 0..100 -> 5..155 dp pro Sekunde */
    private fun speedFromSlider(p: Int) = 5f + p * 1.5f

    private fun changePrompterSize(delta: Float) {
        prompterView.textSizeSp = prompterView.textSizeSp + delta
        settings.prompterTextSize = prompterView.textSizeSp
    }

    private fun editPrompterText() {
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
        } else {
            withTimer { takePhoto() }
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

    private fun takePhoto() {
        val ic = imageCapture ?: return
        MediaStoreUtil.subFolder = currentFolder()
        val filter = processor.filter
        val logo = if (settings.logoEnabled) logoBitmap else null
        // Drehen/Spiegeln macht MoCam selbst am fertigen Foto (Grundeinstellung + Frontkamera-Spiegel)
        val mirror = isFrontCam && settings.frontMirror
        val photoFix = 0
        val needsProcessing = !filter.isIdentity || logo != null || mirror || photoFix != 0
        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = false }

        flashScreen()
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
                }
                override fun onError(e: ImageCaptureException) {
                    toast("Foto fehlgeschlagen: ${e.message}")
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
                            MediaStoreUtil.processAndSavePhoto(this@MainActivity, tmp, filter, logo, cx, cy, w, a, mirror, photoFix)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Fotoverarbeitung", t)
                            null
                        } finally {
                            tmp.delete()
                        }
                        runOnUiThread {
                            if (uri != null) setLastMedia(uri, false)
                            else toast("Foto konnte nicht gespeichert werden.")
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    tmp.delete()
                    toast("Foto fehlgeschlagen: ${e.message}")
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    private fun startRecording() {
        val vc = videoCapture ?: return
        MediaStoreUtil.subFolder = currentFolder()
        if (settings.audio && settings.micMode == "bluetooth") startBluetoothMic()
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(MediaStoreUtil.videoValues(MediaStoreUtil.fileName("MoCam")))
            .build()
        // Persistent: Aufnahme läuft beim Kamerawechsel (vorne/hinten) einfach weiter
        var pending = vc.output.prepareRecording(this, opts).asPersistentRecording()
        if (settings.audio && granted(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }
        isPaused = false
        recording = pending.start(mainExec) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    if (settings.lowerEnabled) startLowerThird()
                    if (prompterActive()) prompterView.playing = true
                    updateUi()
                }
                is VideoRecordEvent.Pause -> {
                    isPaused = true
                    prompterView.playing = false
                    updateUi()
                }
                is VideoRecordEvent.Resume -> {
                    isPaused = false
                    if (prompterActive()) prompterView.playing = true
                    updateUi()
                }
                is VideoRecordEvent.Status -> {
                    val s = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    val limit = settings.timeLimit
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
                    sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    recording = null
                    stopLowerThird()
                    stopBluetoothMic()
                    isPaused = false
                    prompterView.playing = false
                    if (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                        setLastMedia(event.outputResults.outputUri, true)
                        toast("Video gespeichert")
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
        if (logoPanelFromSettings) {
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

    private fun importLogo(uri: Uri) {
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
                    val file = File(logoDir(), "logo_${System.currentTimeMillis()}.png")
                    file.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    savedName = file.name
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
                    settings.selectedLogo = savedName
                    loadedLogoName = savedName
                    applyLogo(bmp)
                    enableLogo()
                    refreshLogoLibrary()
                    toast("Logo gespeichert – jetzt im Bild platzieren.")
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
                updateFolderLabel()
                enableLogo()
                refreshLogoLibrary()
            }
        }
    }

    private fun confirmDeleteLogo(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logo löschen?")
            .setMessage("Das Logo wird aus der Schnellauswahl entfernt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Löschen") { _, _ ->
                val wasSelected = file.name == settings.selectedLogo
                file.delete()
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
                        setOnLongClickListener { confirmDeleteLogo(file); true }
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
        showShareBar(uri, isVideo)
        bg.execute {
            val thumb = MediaStoreUtil.loadThumbnail(this, uri, isVideo)
            runOnUiThread { if (thumb != null) galleryThumb.setImageBitmap(thumb) }
        }
    }

    // =====================================================================
    // #6 Bauchbinde
    // =====================================================================

    private fun applyLowerThird() {
        val bmp = LowerThird.render(settings.lowerName, settings.lowerTitle)
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

    private fun updateUi() {
        val rec = recording != null

        modePhoto.setTextColor(if (!videoMode) getColor(R.color.accent) else Color.WHITE)
        modeVideo.setTextColor(if (videoMode) getColor(R.color.accent) else Color.WHITE)

        shutter.setBackgroundResource(
            when {
                rec -> R.drawable.shutter_recording
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
        btnLimit.text = if (settings.timeLimit == 0) "⏳ Limit aus" else "⏳ ${settings.timeLimit}s"
        btnLimit.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnLower.text = if (settings.lowerEnabled) "🪪 Bauchbinde an" else "🪪 Bauchbinde aus"
        btnLower.visibility = if (videoMode) View.VISIBLE else View.GONE
        audioLevel.visibility = if (rec && settings.audio) View.VISIBLE else View.GONE
        btnGrid.text = if (settings.grid) "▦ Raster an" else "▦ Raster aus"
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
        btnPause.visibility = if (rec) View.VISIBLE else View.GONE
        btnPause.text = if (isPaused) "▶" else "⏸"
        galleryThumb.visibility = if (rec) View.GONE else View.VISIBLE
        btnSwitch.alpha = 1f
        modePhoto.alpha = if (rec) 0.3f else 1f
        modeVideo.alpha = if (rec) 0.3f else 1f
        topBar.alpha = if (rec) 0.6f else 1f
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
        private const val TAG = "MoCam"
        private const val FLASH_OFF = 0
        private const val FLASH_ON = 1
        private const val FLASH_AUTO = 2
    }
}
