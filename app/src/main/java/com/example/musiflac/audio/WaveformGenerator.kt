package com.laizycoder.musiflac.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object WaveformGenerator {

    /*
     * Number of points used to represent the entire song.
     *
     * 600 gives us enough detail while keeping the
     * waveform lightweight for Compose.
     */
    private const val DEFAULT_SAMPLES = 600

    /*
     * Analyze the actual audio file and create a waveform.
     *
     * Result:
     *
     * FloatArray(600)
     *
     * Each value represents the loudness of that
     * portion of the song.
     */
    suspend fun generate(
        filePath: String,
        samples: Int = DEFAULT_SAMPLES,
        onProgress: ((FloatArray) -> Unit)? = null
    ): FloatArray = withContext(Dispatchers.IO) {

        val result =
            FloatArray(samples)

        val file =
            File(filePath)

        if (!file.exists()) {
            return@withContext result
        }

        val extractor =
            MediaExtractor()

        var codec: MediaCodec? = null

        try {

            extractor.setDataSource(filePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            /*
             * Find the first audio track.
             */
            for (i in 0 until extractor.trackCount) {

                val format =
                    extractor.getTrackFormat(i)

                val mime =
                    format.getString(
                        MediaFormat.KEY_MIME
                    )

                if (
                    mime != null &&
                    mime.startsWith("audio/")
                ) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (
                audioTrackIndex == -1 ||
                audioFormat == null
            ) {
                return@withContext result
            }

            extractor.selectTrack(
                audioTrackIndex
            )

            val mime =
                audioFormat.getString(
                    MediaFormat.KEY_MIME
                )

            if (mime == null) {
                return@withContext result
            }

            /*
             * Total duration of the song.
             */
            val durationUs =
                if (
                    audioFormat.containsKey(
                        MediaFormat.KEY_DURATION
                    )
                ) {
                    audioFormat.getLong(
                        MediaFormat.KEY_DURATION
                    )
                } else {
                    0L
                }

            /*
             * Start decoder.
             */
            codec =
                MediaCodec.createDecoderByType(
                    mime
                )

            codec.configure(
                audioFormat,
                null,
                null,
                0
            )

            codec.start()

            val bufferInfo =
                MediaCodec.BufferInfo()

            var inputFinished = false
            var outputFinished = false

            /*
             * Number of PCM frames processed.
             */
            var totalFramesProcessed = 0L

            /*
             * Report the waveform progressively instead of waiting for the
             * complete file to finish decoding. About 100 updates is enough
             * for a smooth visual fill without flooding Compose with work.
             */
            var lastReportedBucket = -1L

            /*
             * Expected number of PCM frames.
             *
             * This lets us place each audio peak at
             * the correct location in the complete song.
             */
            var expectedFrames = 0L

            val sampleRate =
                if (
                    audioFormat.containsKey(
                        MediaFormat.KEY_SAMPLE_RATE
                    )
                ) {
                    audioFormat.getInteger(
                        MediaFormat.KEY_SAMPLE_RATE
                    )
                } else {
                    44100
                }

            if (durationUs > 0L) {

                expectedFrames =
                    (
                            durationUs *
                                    sampleRate
                            ) / 1_000_000L
            }

            /*
             * Prevent invalid bucket calculations.
             */
            if (expectedFrames <= 0L) {
                expectedFrames =
                    samples.toLong()
            }

            while (!outputFinished) {

                /*
                 * Feed compressed audio into decoder.
                 */
                if (!inputFinished) {

                    val inputIndex =
                        codec.dequeueInputBuffer(
                            10_000
                        )

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(
                                inputIndex
                            )

                        if (inputBuffer != null) {

                            inputBuffer.clear()

                            val sampleSize =
                                extractor.readSampleData(
                                    inputBuffer,
                                    0
                                )

                            if (sampleSize < 0) {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputFinished = true

                            } else {

                                val presentationTimeUs =
                                    extractor.sampleTime

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )

                                extractor.advance()
                            }
                        }
                    }
                }

                /*
                 * Get decoded PCM.
                 */
                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                when {

                    outputIndex >= 0 -> {

                        val outputBuffer =
                            codec.getOutputBuffer(
                                outputIndex
                            )

                        if (
                            outputBuffer != null &&
                            bufferInfo.size > 0
                        ) {

                            /*
                             * The decoder's actual output
                             * format.
                             */
                            val outputFormat =
                                codec.outputFormat

                            val channels =
                                if (
                                    outputFormat.containsKey(
                                        MediaFormat.KEY_CHANNEL_COUNT
                                    )
                                ) {
                                    outputFormat.getInteger(
                                        MediaFormat.KEY_CHANNEL_COUNT
                                    )
                                } else {
                                    2
                                }

                            val encoding =
                                if (
                                    outputFormat.containsKey(
                                        MediaFormat.KEY_PCM_ENCODING
                                    )
                                ) {
                                    outputFormat.getInteger(
                                        MediaFormat.KEY_PCM_ENCODING
                                    )
                                } else {
                                    AudioFormat.ENCODING_PCM_16BIT
                                }

                            outputBuffer.position(
                                bufferInfo.offset
                            )

                            outputBuffer.limit(
                                bufferInfo.offset +
                                        bufferInfo.size
                            )

                            /*
                             * Analyze the decoded PCM.
                             */
                            when (encoding) {

                                AudioFormat.ENCODING_PCM_FLOAT -> {

                                    totalFramesProcessed =
                                        processFloatPcm(
                                            outputBuffer,
                                            channels,
                                            samples,
                                            expectedFrames,
                                            result,
                                            totalFramesProcessed
                                        )

                                    val frameSize =
                                        channels * 4

                                    // processFloatPcm() already advances the
                                    // frame counter.
                                }

                                AudioFormat.ENCODING_PCM_16BIT -> {

                                    totalFramesProcessed =
                                        process16BitPcm(
                                            outputBuffer,
                                            channels,
                                            samples,
                                            expectedFrames,
                                            result,
                                            totalFramesProcessed
                                        )
                                }

                                else -> {

                                    /*
                                     * Unsupported PCM format.
                                     *
                                     * We simply skip this buffer
                                     * instead of crashing playback.
                                     */
                                }
                            }

                            if (onProgress != null && totalFramesProcessed > 0L) {
                                val bucketSize =
                                    max(1L, expectedFrames / 100L)
                                val bucket =
                                    totalFramesProcessed / bucketSize

                                if (bucket != lastReportedBucket) {
                                    lastReportedBucket = bucket
                                    onProgress(
                                        normalizedCopy(result)
                                    )
                                }
                            }
                        }

                        codec.releaseOutputBuffer(
                            outputIndex,
                            false
                        )

                        if (
                            bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM !=
                            0
                        ) {
                            outputFinished = true
                        }
                    }

                    outputIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                        /*
                         * The decoder changed its output format.
                         * The next output buffer will use the
                         * new format.
                         */
                    }
                }
            }

        } catch (_: Exception) {

            /*
             * If a particular audio codec/file cannot be
             * decoded, return whatever waveform data has
             * already been generated instead of crashing
             * the application.
             */

        } finally {

            try {
                codec?.stop()
            } catch (_: Exception) {
            }

            try {
                codec?.release()
            } catch (_: Exception) {
            }

            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        /*
         * Normalize the waveform.
         *
         * This makes quiet songs visible while preserving
         * the relative loudness differences within the song.
         */
        normalize(result)

        result
    }


    /*
     * Process 16-bit PCM audio.
     */
    private fun process16BitPcm(
        buffer: ByteBuffer,
        channels: Int,
        samples: Int,
        expectedFrames: Long,
        waveform: FloatArray,
        startingFrame: Long
    ): Long {

        val safeChannels =
            max(1, channels)

        val bytesPerFrame =
            safeChannels * 2

        if (bytesPerFrame <= 0) {
            return startingFrame
        }

        val frameCount =
            buffer.remaining() /
                    bytesPerFrame

        var frame =
            0

        var currentFrame =
            startingFrame

        while (frame < frameCount) {

            var peak = 0f

            var channel = 0

            while (channel < safeChannels) {

                if (buffer.remaining() < 2) {
                    break
                }

                val sample =
                    buffer.short.toInt()

                val amplitude =
                    abs(sample) / 32768f

                peak =
                    max(
                        peak,
                        amplitude
                    )

                channel++
            }

            /*
             * Find the position of this frame
             * inside the complete song.
             */
            val index =
                waveformIndex(
                    currentFrame,
                    expectedFrames,
                    samples
                )

            waveform[index] =
                max(
                    waveform[index],
                    peak
                )

            currentFrame++
            frame++
        }

        return currentFrame
    }


    /*
     * Process floating-point PCM audio.
     */
    private fun processFloatPcm(
        buffer: ByteBuffer,
        channels: Int,
        samples: Int,
        expectedFrames: Long,
        waveform: FloatArray,
        startingFrame: Long
    ): Long {

        val safeChannels =
            max(1, channels)

        val bytesPerFrame =
            safeChannels * 4

        if (bytesPerFrame <= 0) {
            return startingFrame
        }

        val frameCount =
            buffer.remaining() /
                    bytesPerFrame

        var frame =
            0

        var currentFrame =
            startingFrame

        while (frame < frameCount) {

            var peak = 0f

            var channel = 0

            while (channel < safeChannels) {

                if (buffer.remaining() < 4) {
                    break
                }

                val sample =
                    buffer.float

                val amplitude =
                    abs(sample)

                peak =
                    max(
                        peak,
                        amplitude
                    )

                channel++
            }

            val index =
                waveformIndex(
                    currentFrame,
                    expectedFrames,
                    samples
                )

            waveform[index] =
                max(
                    waveform[index],
                    peak
                )

            currentFrame++
            frame++
        }

        return currentFrame
    }


    /*
     * Convert a PCM frame number into one of
     * our waveform points.
     */
    private fun waveformIndex(
        frame: Long,
        expectedFrames: Long,
        samples: Int
    ): Int {

        if (samples <= 1) {
            return 0
        }

        if (expectedFrames <= 0L) {
            return 0
        }

        val ratio =
            frame.toDouble() /
                    expectedFrames.toDouble()

        val index =
            (
                    ratio *
                            (samples - 1)
                    ).toInt()

        return index.coerceIn(
            0,
            samples - 1
        )
    }


    /*
     * Create a normalized snapshot for progressive UI updates without
     * modifying the waveform that is still being generated.
     */
    private fun normalizedCopy(
        waveform: FloatArray
    ): FloatArray {

        val copy = waveform.copyOf()

        if (copy.isEmpty()) {
            return copy
        }

        var maximum = 0f

        for (value in copy) {
            maximum = max(maximum, value)
        }

        if (maximum <= 0f) {
            return copy
        }

        for (i in copy.indices) {
            val normalized = copy[i] / maximum

            copy[i] = sqrt(
                normalized.coerceIn(0f, 1f)
            )
        }

        return copy
    }


    /*
     * Normalize the waveform.
     *
     * We use RMS-style scaling rather than simply
     * multiplying everything to 1.0.
     */
    private fun normalize(
        waveform: FloatArray
    ) {

        if (waveform.isEmpty()) {
            return
        }

        var maximum =
            0f

        for (value in waveform) {

            maximum =
                max(
                    maximum,
                    value
                )
        }

        if (maximum <= 0f) {
            return
        }

        /*
         * Use a square-root curve so quieter details
         * become visible without making everything flat.
         */
        for (i in waveform.indices) {

            val normalized =
                waveform[i] /
                        maximum

            waveform[i] =
                sqrt(
                    normalized
                        .coerceIn(0f, 1f)
                )
        }
    }
}