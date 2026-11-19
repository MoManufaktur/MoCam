package com.momanufaktur.mocam

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Dekodiert die Tonspur eines Videos zu 16-kHz-Mono-PCM (16 Bit) für die Spracherkennung.
 * Liefert die Daten in Blöcken an [onChunk], damit auch lange Videos wenig Speicher brauchen.
 * @return false, wenn das Video keine Tonspur hat.
 */
object AudioPcm {

    private const val TARGET_RATE = 16000

    fun decode(ctx: Context, uri: Uri, onChunk: (ByteArray, Int) -> Unit): Boolean {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { track = i; break }
        }
        if (track < 0) {
            extractor.release()
            return false
        }
        extractor.selectTrack(track)
        val inFormat = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        var srcRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        // Resampling-Zustand (lineare Interpolation, fortlaufend über Blöcke)
        var pos = 0.0          // Position im Quellsignal (in Mono-Samples) relativ zu prev
        var prev = 0f
        var havePrev = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                outIdx >= 0 -> {
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(outIdx)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = shorts.remaining() / channels.coerceAtLeast(1)
                        val step = srcRate.toDouble() / TARGET_RATE
                        val outBytes = ByteArray(((frames / step) + 4).toInt() * 2)
                        var n = 0
                        for (fIdx in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get()
                            val cur = sum.toFloat() / channels
                            if (!havePrev) { prev = cur; havePrev = true; continue }
                            // Ausgabeproben zwischen prev (pos 0) und cur (pos 1) erzeugen
                            while (pos <= 1.0) {
                                val v = prev + (cur - prev) * pos.toFloat()
                                val s = v.toInt().coerceIn(-32768, 32767)
                                if (n + 1 < outBytes.size) {
                                    outBytes[n++] = (s and 0xFF).toByte()
                                    outBytes[n++] = ((s shr 8) and 0xFF).toByte()
                                }
                                pos += step
                            }
                            pos -= 1.0
                            prev = cur
                        }
                        if (n > 0) onChunk(outBytes, n)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return true
    }
}
