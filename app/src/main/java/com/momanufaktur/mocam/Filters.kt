package com.momanufaktur.mocam

import android.graphics.ColorMatrix

/**
 * Farbfilter als 3x3-Matrix (zeilenweise) + Offset (0..1).
 * Dieselbe Definition wird live im OpenGL-Shader (Vorschau + Video)
 * und beim Speichern von Fotos (Android ColorMatrix) verwendet.
 */
class Filter(val name: String, val m: FloatArray, val off: FloatArray = floatArrayOf(0f, 0f, 0f)) {

    val isIdentity: Boolean
        get() = m.contentEquals(IDENTITY) && off.all { it == 0f }

    /** OpenGL erwartet spaltenweise Matrizen. */
    fun columnMajor(out: FloatArray) {
        for (r in 0..2) for (c in 0..2) out[c * 3 + r] = m[r * 3 + c]
    }

    fun toColorMatrix(): ColorMatrix = ColorMatrix(
        floatArrayOf(
            m[0], m[1], m[2], 0f, off[0] * 255f,
            m[3], m[4], m[5], 0f, off[1] * 255f,
            m[6], m[7], m[8], 0f, off[2] * 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    companion object {
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}

object Filters {

    private fun saturation(s: Float): FloatArray {
        val lr = 0.2126f * (1 - s)
        val lg = 0.7152f * (1 - s)
        val lb = 0.0722f * (1 - s)
        return floatArrayOf(
            lr + s, lg, lb,
            lr, lg + s, lb,
            lr, lg, lb + s
        )
    }

    private fun scale(m: FloatArray, k: Float) = FloatArray(9) { m[it] * k }

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(9) { a[it] * (1 - t) + b[it] * t }

    private val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f,
        0.349f, 0.686f, 0.168f,
        0.272f, 0.534f, 0.131f
    )

    val ALL: List<Filter> = listOf(
        Filter("Original", Filter.IDENTITY),
        Filter("Lebendig", saturation(1.45f)),
        Filter("S/W", saturation(0f)),
        Filter("Noir", scale(saturation(0f), 1.45f), floatArrayOf(-0.22f, -0.22f, -0.22f)),
        Filter("Sepia", SEPIA),
        Filter("Vintage", scale(mix(Filter.IDENTITY, SEPIA, 0.45f), 0.9f), floatArrayOf(0.06f, 0.04f, 0.02f)),
        Filter(
            "Warm", floatArrayOf(
                1.10f, 0f, 0f,
                0f, 1.02f, 0f,
                0f, 0f, 0.85f
            ), floatArrayOf(0.02f, 0.01f, 0f)
        ),
        Filter(
            "Kalt", floatArrayOf(
                0.88f, 0f, 0f,
                0f, 1.0f, 0f,
                0f, 0f, 1.12f
            ), floatArrayOf(0f, 0.01f, 0.03f)
        ),
        Filter("Kontrast", scale(Filter.IDENTITY, 1.3f), floatArrayOf(-0.15f, -0.15f, -0.15f)),
        Filter("Matt", scale(saturation(0.85f), 0.82f), floatArrayOf(0.09f, 0.09f, 0.09f)),
        Filter(
            "Kino", floatArrayOf(
                1.12f, -0.04f, -0.02f,
                -0.02f, 1.02f, 0.04f,
                -0.04f, 0.10f, 0.92f
            ), floatArrayOf(0f, 0.01f, 0.04f)
        ),
        Filter(
            "Nordisch", floatArrayOf(
                0.80f, 0.10f, 0.05f,
                0.05f, 0.90f, 0.08f,
                0.05f, 0.10f, 0.95f
            ), floatArrayOf(0.04f, 0.05f, 0.07f)
        ),
        Filter(
            "Invertiert", floatArrayOf(
                -1f, 0f, 0f,
                0f, -1f, 0f,
                0f, 0f, -1f
            ), floatArrayOf(1f, 1f, 1f)
        )
    )
}
