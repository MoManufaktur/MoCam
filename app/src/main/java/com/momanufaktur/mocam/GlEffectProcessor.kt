package com.momanufaktur.mocam

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import kotlin.math.abs

/**
 * OpenGL-Pipeline zwischen Kamera und Ausgaben (Vorschau + Videoencoder).
 * Wendet den Farbfilter an und brennt das Logo direkt in jedes Bild ein –
 * die Vorschau zeigt also exakt das, was aufgenommen wird.
 *
 * Das Logo wird im Koordinatensystem des aufrechten Bildes platziert
 * (0..1, Ursprung oben links) und für jede Ausgabe über die Sensorkoordinaten
 * zurückgerechnet. Dadurch landet es in Vorschau und Video an derselben Stelle.
 */
class GlEffectProcessor : SurfaceProcessor {

    private val thread = HandlerThread("MoCamGL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    /** Wird (auf dem GL-Thread) mit der Eingangsauflösung aufgerufen. */
    @Volatile var onInputSize: ((Int, Int) -> Unit)? = null

    // ---- Zustand, wird vom UI-Thread gesetzt ----
    @Volatile var filter: Filter = Filters.ALL[0]
    @Volatile var logoEnabled = false
    @Volatile var logoCx = 0.82f
    @Volatile var logoCy = 0.08f
    @Volatile var logoW = 0.28f
    @Volatile var logoAlpha = 1f
    /** Drehung Sensor -> aufrechtes Bild (meist 90 hinten, 270 vorne). */
    @Volatile var rotationDegrees = 90
    /** Frontkamera aktiv (Vorschau ist dann gespiegelt). */
    @Volatile var isFront = false
    /** Vorzeichen der Abbildung für ungespiegelte Ausgaben – wird an der Rückkamera gelernt. */
    private var unmirroredSign = -1f

    @Volatile private var logoAspect = 1f // Höhe / Breite
    @Volatile private var hasLogo = false

    // ---- EGL / GL ----
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var glReady = false
    private var released = false

    private var program = 0
    private var aPos = 0
    private var aUv = 0
    private var uAdd = 0
    private var uTex = 0
    private var uCam = 0
    private var uColor = 0
    private var uOff = 0
    private var uLogo = 0
    private var uHasLogo = 0
    private var uRot = 0
    private var uRect = 0
    private var uAlpha = 0
    private var uPMin = 0
    private var uMirror = 0
    private var uPRange = 0
    private var logoTex = 0

    private class Input(val st: SurfaceTexture, val surface: Surface, val tex: Int, val w: Int, val h: Int)

    private var input: Input? = null
    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()

    private val stMatrix = FloatArray(16)
    private val addMatrix = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val colorCm = FloatArray(9)

    private val quadPos = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val quadUv = floatBuffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

    init {
        handler.post { initGl() }
    }

    // ------------------------------------------------------------------
    // SurfaceProcessor
    // ------------------------------------------------------------------

    override fun onInputSurface(request: SurfaceRequest) {
        if (released || !glReady) {
            request.willNotProvideSurface()
            return
        }
        makeCurrentPbuffer()
        val tex = createOesTexture()
        val st = SurfaceTexture(tex)
        val res = request.resolution
        st.setDefaultBufferSize(res.width, res.height)
        val surface = Surface(st)
        val holder = Input(st, surface, tex, res.width, res.height)

        request.provideSurface(surface, executor) {
            st.setOnFrameAvailableListener(null)
            st.release()
            surface.release()
            if (!released) {
                makeCurrentPbuffer()
                GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            }
            if (input === holder) input = null
        }
        st.setOnFrameAvailableListener({ onFrame(holder) }, handler)
        input = holder
        onInputSize?.invoke(res.width, res.height)
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released || !glReady) {
            surfaceOutput.close()
            return
        }
        val surface = surfaceOutput.getSurface(executor) { event ->
            val out = event.surfaceOutput
            val egl = outputs.remove(out)
            if (egl != null && !released) {
                makeCurrentPbuffer()
                EGL14.eglDestroySurface(display, egl)
            }
            out.close()
        }
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (egl == null || egl == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface fehlgeschlagen: 0x" + Integer.toHexString(EGL14.eglGetError()))
            surfaceOutput.close()
            return
        }
        outputs[surfaceOutput] = egl
    }

    // ------------------------------------------------------------------
    // Öffentliche API
    // ------------------------------------------------------------------

    fun setLogo(bitmap: Bitmap?) {
        handler.post {
            if (released || !glReady) return@post
            makeCurrentPbuffer()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, logoTex)
            if (bitmap != null && !bitmap.isRecycled) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                logoAspect = bitmap.height.toFloat() / bitmap.width.toFloat()
                hasLogo = true
            } else {
                uploadTransparentPixel()
                hasLogo = false
            }
        }
    }

    fun release() {
        handler.post {
            if (released) return@post
            released = true
            if (glReady) {
                makeCurrentPbuffer()
                for ((out, egl) in outputs) {
                    EGL14.eglDestroySurface(display, egl)
                    out.close()
                }
                outputs.clear()
                input?.let {
                    it.st.setOnFrameAvailableListener(null)
                    it.st.release()
                    it.surface.release()
                }
                input = null
                GLES20.glDeleteProgram(program)
                GLES20.glDeleteTextures(1, intArrayOf(logoTex), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            }
            thread.quitSafely()
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private fun onFrame(holder: Input) {
        if (released || input !== holder) return
        makeCurrentPbuffer()
        try {
            holder.st.updateTexImage()
        } catch (e: Exception) {
            Log.w(TAG, "updateTexImage", e)
            return
        }
        holder.st.getTransformMatrix(stMatrix)
        val timestamp = holder.st.timestamp

        for ((out, egl) in outputs) {
            if (!EGL14.eglMakeCurrent(display, egl, egl, context)) continue
            out.updateTransformMatrix(addMatrix, identity)
            GLES20.glViewport(0, 0, out.size.width, out.size.height)
            draw(holder, out)
            EGLExt.eglPresentationTimeANDROID(display, egl, timestamp)
            EGL14.eglSwapBuffers(display, egl)
        }
    }

    private fun draw(holder: Input, out: SurfaceOutput) {
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, holder.tex)
        GLES20.glUniform1i(uCam, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, logoTex)
        GLES20.glUniform1i(uLogo, 1)

        GLES20.glUniformMatrix4fv(uTex, 1, false, stMatrix, 0)
        GLES20.glUniformMatrix4fv(uAdd, 1, false, addMatrix, 0)

        val f = filter
        f.columnMajor(colorCm)
        GLES20.glUniformMatrix3fv(uColor, 1, false, colorCm, 0)
        GLES20.glUniform3f(uOff, f.off[0], f.off[1], f.off[2])

        val rot = ((rotationDegrees % 360) + 360) % 360
        GLES20.glUniform1f(uRot, rot.toFloat())

        val show = logoEnabled && hasLogo
        GLES20.glUniform1f(uHasLogo, if (show) 1f else 0f)
        if (show) {
            // Sichtbaren Bildausschnitt dieser Ausgabe im aufrechten Koordinatensystem bestimmen.
            // Damit bezieht sich die Logo-Position immer auf das Format (9:16, 1:1 …),
            // und das Seitenverhältnis wird in echten Pixeln der Ausgabe berechnet.
            val p00 = uprightAt(0f, 0f, rot)
            val p10 = uprightAt(1f, 0f, rot)
            val p01 = uprightAt(0f, 1f, rot)
            val p11x = p10[0] + p01[0] - p00[0]
            val p11y = p10[1] + p01[1] - p00[1]
            val minX = minOf(minOf(p00[0], p10[0]), minOf(p01[0], p11x))
            val maxX = maxOf(maxOf(p00[0], p10[0]), maxOf(p01[0], p11x))
            val minY = minOf(minOf(p00[1], p10[1]), minOf(p01[1], p11y))
            val maxY = maxOf(maxOf(p00[1], p10[1]), maxOf(p01[1], p11y))
            // Gespiegelte Ausgabe (Selfie-Vorschau)? -> Logo im angezeigten Bild platzieren,
            // damit es überall an derselben Stelle und richtig herum erscheint.
            val det = (p10[0] - p00[0]) * (p01[1] - p00[1]) - (p10[1] - p00[1]) * (p01[0] - p00[0])
            val sign = if (det >= 0f) 1f else -1f
            if (!isFront) unmirroredSign = sign
            val mirrored = isFront && sign != unmirroredSign
            GLES20.glUniform1f(uMirror, if (mirrored) 1f else 0f)
            val rangeX = (maxX - minX).coerceAtLeast(1e-4f)
            val rangeY = (maxY - minY).coerceAtLeast(1e-4f)

            // Läuft die Ausgabe-Breite (u) entlang der aufrechten x-Achse?
            val uAlongX = abs(p10[0] - p00[0]) >= abs(p10[1] - p00[1])
            val upW = if (uAlongX) out.size.width else out.size.height
            val upH = if (uAlongX) out.size.height else out.size.width
            val upright = upW.toFloat() / upH.toFloat()

            val w = logoW
            val h = w * logoAspect * upright
            GLES20.glUniform2f(uPMin, minX, minY)
            GLES20.glUniform2f(uPRange, rangeX, rangeY)
            GLES20.glUniform4f(uRect, logoCx - w / 2f, logoCy - h / 2f, w, h)
            GLES20.glUniform1f(uAlpha, logoAlpha)
        }

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadPos)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, quadUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    /**
     * Gleiche Abbildung wie im Shader: Ausgabe-Koordinate (u, v) -> aufrechtes Bild (0..1).
     */
    private fun uprightAt(u: Float, v: Float, rot: Int): FloatArray {
        val sx = addMatrix[0] * u + addMatrix[4] * v + addMatrix[12]
        val sy = addMatrix[1] * u + addMatrix[5] * v + addMatrix[13]
        val ix = 1f - sx
        val iy = sy
        return when {
            rot in 46..134 -> floatArrayOf(1f - iy, ix)
            rot in 135..224 -> floatArrayOf(1f - ix, 1f - iy)
            rot >= 225 -> floatArrayOf(iy, 1f - ix)
            else -> floatArrayOf(ix, iy)
        }
    }

    // ------------------------------------------------------------------
    // GL-Setup
    // ------------------------------------------------------------------

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize fehlgeschlagen")
            return
        }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
            Log.e(TAG, "Keine passende EGL-Konfiguration")
            return
        }
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        makeCurrentPbuffer()

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uAdd = GLES20.glGetUniformLocation(program, "uAdd")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uCam = GLES20.glGetUniformLocation(program, "uCam")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uOff = GLES20.glGetUniformLocation(program, "uOff")
        uLogo = GLES20.glGetUniformLocation(program, "uLogo")
        uHasLogo = GLES20.glGetUniformLocation(program, "uHasLogo")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uRect = GLES20.glGetUniformLocation(program, "uRect")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uPMin = GLES20.glGetUniformLocation(program, "uPMin")
        uMirror = GLES20.glGetUniformLocation(program, "uMirror")
        uPRange = GLES20.glGetUniformLocation(program, "uPRange")

        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        logoTex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, logoTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        uploadTransparentPixel()
        glReady = program != 0
    }

    private fun uploadTransparentPixel() {
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px
        )
    }

    private fun makeCurrentPbuffer() {
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
    }

    private fun createOesTexture(): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "Link-Fehler: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return 0
        }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "Shader-Fehler: " + GLES20.glGetShaderInfoLog(s))
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }

    companion object {
        private const val TAG = "MoCamGL"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private fun floatBuffer(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v); position(0)
            }

        private const val VERTEX_SHADER = """
attribute vec4 aPos;
attribute vec2 aUv;
uniform mat4 uAdd;
varying vec2 vS;
void main() {
    gl_Position = aPos;
    vS = (uAdd * vec4(aUv, 0.0, 1.0)).xy;
}
"""

        private const val FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vS;
uniform samplerExternalOES uCam;
uniform sampler2D uLogo;
uniform mat4 uTex;
uniform mat3 uColor;
uniform vec3 uOff;
uniform float uHasLogo;
uniform float uRot;
uniform vec4 uRect;
uniform vec2 uPMin;
uniform float uMirror;
uniform vec2 uPRange;
uniform float uAlpha;
void main() {
    vec2 tc = (uTex * vec4(vS, 0.0, 1.0)).xy;
    vec3 c = texture2D(uCam, tc).rgb;
    c = clamp(uColor * c + uOff, 0.0, 1.0);
    if (uHasLogo > 0.5) {
        // Sensor-Bildkoordinaten -> aufrechtes Bild
        // (auf dem Mate 20 Pro getestet: x läuft entgegengesetzt, y bereits nach unten)
        vec2 img = vec2(1.0 - vS.x, vS.y);
        vec2 p = img;
        if (uRot > 45.0 && uRot < 135.0) {
            p = vec2(1.0 - img.y, img.x);
        } else if (uRot >= 135.0 && uRot < 225.0) {
            p = vec2(1.0 - img.x, 1.0 - img.y);
        } else if (uRot >= 225.0) {
            p = vec2(img.y, 1.0 - img.x);
        }
        vec2 q = (p - uPMin) / uPRange;
        if (uMirror > 0.5) q.x = 1.0 - q.x;
        vec2 luv = (q - uRect.xy) / uRect.zw;
        if (luv.x >= 0.0 && luv.x <= 1.0 && luv.y >= 0.0 && luv.y <= 1.0) {
            vec4 l = texture2D(uLogo, luv);
            float a = l.a * uAlpha;
            c = c * (1.0 - a) + l.rgb * uAlpha;
        }
    }
    gl_FragColor = vec4(c, 1.0);
}
"""
    }
}
