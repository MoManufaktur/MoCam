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
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
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
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
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
    private lateinit var btnGrid: TextView
    private lateinit var btnQuality: TextView
    private lateinit var btnAudio: TextView
    private lateinit var btnExtension: TextView
    private lateinit var btnLogo: TextView
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

    // ---------------- Zustand ----------------
    private lateinit var settings: Settings
    private lateinit var processor: GlEffectProcessor
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
    private var lastMediaUri: Uri? = null
    private var lastMediaIsVideo = false
    private var countdown: CountDownTimer? = null
    private var zoomBarTracking = false
    private var filterViews = mutableListOf<TextView>()

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
        processor = GlEffectProcessor()

        bindViews()
        restoreSettings()
        setupUi()
        loadSavedLogo()
        sound.load(MediaActionSound.SHUTTER_CLICK)

        processor.onInputSize = { w, h ->
            runOnUiThread {
                overlay.contentAspect =
                    if (sensorRotation % 180 != 0) h.toFloat() / w else w.toFloat() / h
            }
        }

        if (hasAllPermissions()) startCamera() else permissionLauncher.launch(requiredPermissions())
    }

    override fun onStop() {
        super.onStop()
        countdown?.cancel()
        countdownText.visibility = View.GONE
        recording?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.close()
        cameraProvider?.unbindAll()
        processor.release()
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
        btnGrid = findViewById(R.id.btnGrid)
        btnQuality = findViewById(R.id.btnQuality)
        btnAudio = findViewById(R.id.btnAudio)
        btnExtension = findViewById(R.id.btnExtension)
        btnLogo = findViewById(R.id.btnLogo)
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
    }

    private fun restoreSettings() {
        videoMode = settings.videoMode
        processor.filter = Filters.ALL.getOrElse(settings.filterIndex) { Filters.ALL[0] }
        processor.logoEnabled = settings.logoEnabled
        processor.logoCx = settings.logoCx
        processor.logoCy = settings.logoCy
        processor.logoW = settings.logoW
        processor.logoAlpha = settings.logoAlpha
        overlay.logoCx = settings.logoCx
        overlay.logoCy = settings.logoCy
        overlay.logoW = settings.logoW
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
        btnLogo.setOnClickListener { openLogoPanel() }

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
            processor.logoEnabled = checked
        }
        logoPhotosSwitch.isChecked = settings.logoOnPhotos
        logoPhotosSwitch.setOnCheckedChangeListener { _, checked -> settings.logoOnPhotos = checked }
        logoAlphaBar.progress = (settings.logoAlpha * 100).roundToInt()
        logoAlphaBar.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val a = max(progress, 5) / 100f
                processor.logoAlpha = a
                settings.logoAlpha = a
            }
        })
        findViewById<View>(R.id.btnPickLogo).setOnClickListener { pickLogo.launch("image/*") }
        findViewById<View>(R.id.btnLogoDone).setOnClickListener { closeLogoPanel() }
        findViewById<View>(R.id.btnLogoReset).setOnClickListener {
            overlay.logoCx = 0.80f
            overlay.logoCy = 0.10f
            overlay.logoW = 0.30f
            overlay.onLogoChanged?.invoke(0.80f, 0.10f, 0.30f)
            overlay.invalidate()
        }
        findViewById<View>(R.id.btnLogoRotate).setOnClickListener {
            settings.logoRotOffset = (settings.logoRotOffset + 90) % 360
            applyRotation()
            toast("Video-Ausrichtung des Logos: +${settings.logoRotOffset}°")
        }

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
        if (recording != null) return

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
        overlay.mirrorX = lensFacing == CameraSelector.LENS_FACING_FRONT
        overlay.contentAspect = if (videoMode) 9f / 16f else 3f / 4f
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

        val ratioStrategy = if (videoMode)
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build())
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        var bound: Camera? = null
        if (videoMode) {
            val q = qualities[settings.qualityIndex.coerceIn(0, qualities.size - 1)].first
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                .setAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            val vc = VideoCapture.withOutput(recorder)
            videoCapture = vc

            val withEffect = UseCaseGroup.Builder()
                .addUseCase(preview).addUseCase(vc)
                .addEffect(FxEffect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE, processor))
                .build()
            bound = tryBind(provider, base, withEffect)
            effectActive = bound != null
            if (bound == null) {
                toast("Filter/Logo-Effekt wird von diesem Gerät nicht unterstützt.")
                bound = tryBind(provider, base,
                    UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc).build())
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

            bound = tryBind(provider, selector, UseCaseGroup.Builder()
                .addUseCase(preview).addUseCase(ic)
                .addEffect(FxEffect(CameraEffect.PREVIEW, processor))
                .build())
            effectActive = bound != null
            if (bound == null) {
                bound = tryBind(provider, selector,
                    UseCaseGroup.Builder().addUseCase(preview).addUseCase(ic).build())
                if (bound != null && extensionMode != ExtensionMode.NONE) {
                    toast("${extensionName(extensionMode)}: Live-Filter aus, Filter/Logo kommen beim Speichern dazu.")
                }
            }
            if (bound == null && selector != base) {
                extensionMode = ExtensionMode.NONE
                bound = tryBind(provider, base,
                    UseCaseGroup.Builder().addUseCase(preview).addUseCase(ic).build())
            }
        }

        camera = bound
        if (bound == null) {
            toast("Kamera konnte nicht verbunden werden.")
            return
        }
        setupCameraControls(bound)
        updateUi()
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
        processor.rotationDegrees = (sensorRotation + settings.logoRotOffset) % 360
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
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
        overlay.showFocus(x, y)
    }

    private fun switchCamera() {
        if (recording != null) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        extensionMode = ExtensionMode.NONE
        bindCamera()
    }

    private fun setVideoMode(video: Boolean) {
        if (recording != null || videoMode == video) return
        videoMode = video
        settings.videoMode = video
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
    // Aufnahme
    // =====================================================================

    private fun onShutter() {
        if (logoPanel.visibility == View.VISIBLE) closeLogoPanel()
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
        val filter = processor.filter
        val logo = if (settings.logoEnabled && settings.logoOnPhotos) logoBitmap else null
        val needsProcessing = !filter.isIdentity || logo != null
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
                            MediaStoreUtil.processAndSavePhoto(this@MainActivity, tmp, filter, logo, cx, cy, w, a)
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
    private fun startRecording() {
        val vc = videoCapture ?: return
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(MediaStoreUtil.videoValues(MediaStoreUtil.fileName("MoCam")))
            .build()
        var pending = vc.output.prepareRecording(this, opts)
        if (settings.audio && granted(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }
        isPaused = false
        recording = pending.start(mainExec) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    updateUi()
                }
                is VideoRecordEvent.Pause -> { isPaused = true; updateUi() }
                is VideoRecordEvent.Resume -> { isPaused = false; updateUi() }
                is VideoRecordEvent.Status -> {
                    val s = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    recIndicator.text = String.format("%s %02d:%02d", if (isPaused) "❚❚" else "●", s / 60, s % 60)
                }
                is VideoRecordEvent.Finalize -> {
                    sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    recording = null
                    isPaused = false
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

    private fun openLogoPanel() {
        bottomPanel.visibility = View.GONE
        logoPanel.visibility = View.VISIBLE
        overlay.editLogo = true
        if (!effectActive) toast("In diesem Modus keine Live-Vorschau des Logos.")
        if (logoBitmap == null) pickLogo.launch("image/*")
    }

    private fun closeLogoPanel() {
        logoPanel.visibility = View.GONE
        bottomPanel.visibility = View.VISIBLE
        overlay.editLogo = false
    }

    private fun logoFile() = File(filesDir, "logo.png")

    private fun loadSavedLogo() {
        val f = logoFile()
        if (!f.exists()) return
        bg.execute {
            val bmp = BitmapFactory.decodeFile(f.path)
            runOnUiThread { if (bmp != null) applyLogo(bmp) }
        }
    }

    private fun importLogo(uri: Uri) {
        bg.execute {
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
                    logoFile().outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
                    applyLogo(bmp)
                    settings.logoEnabled = true
                    processor.logoEnabled = true
                    logoSwitch.isChecked = true
                    toast("Logo geladen – jetzt im Bild platzieren.")
                }
            }
        }
    }

    private fun applyLogo(bmp: Bitmap) {
        logoBitmap = bmp
        processor.setLogo(bmp)
        overlay.logoAspect = bmp.height.toFloat() / bmp.width
        overlay.hasLogo = true
    }

    // =====================================================================
    // Galerie
    // =====================================================================

    private fun setLastMedia(uri: Uri, isVideo: Boolean) {
        lastMediaUri = uri
        lastMediaIsVideo = isVideo
        bg.execute {
            val thumb = MediaStoreUtil.loadThumbnail(this, uri, isVideo)
            runOnUiThread { if (thumb != null) galleryThumb.setImageBitmap(thumb) }
        }
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
        btnGrid.text = if (settings.grid) "▦ Raster an" else "▦ Raster aus"
        btnQuality.text = "🎞 " + qualities[settings.qualityIndex.coerceIn(0, qualities.size - 1)].second
        btnAudio.text = if (settings.audio) "🎤 Ton an" else "🔇 Ton aus"
        btnExtension.text = "✨ " + extensionName(extensionMode)
        btnLogo.text = "🏷 Logo"

        btnQuality.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnAudio.visibility = if (videoMode) View.VISIBLE else View.GONE
        btnExtension.visibility =
            if (!videoMode && availableExtensions.size > 1) View.VISIBLE else View.GONE

        recIndicator.visibility = if (rec) View.VISIBLE else View.GONE
        btnPause.visibility = if (rec) View.VISIBLE else View.GONE
        btnPause.text = if (isPaused) "▶" else "⏸"
        galleryThumb.visibility = if (rec) View.GONE else View.VISIBLE
        btnSwitch.alpha = if (rec) 0.3f else 1f
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
