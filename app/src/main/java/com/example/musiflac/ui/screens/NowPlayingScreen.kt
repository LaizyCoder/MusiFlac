package com.laizycoder.musiflac.ui.screens

import android.os.SystemClock

import com.laizycoder.musiflac.audio.WaveformGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.laizycoder.musiflac.data.MusicTrack
import com.laizycoder.musiflac.player.MusicPlayer
import kotlinx.coroutines.delay


@Composable
fun NowPlayingScreen(
    track: MusicTrack,
    musicPlayer: MusicPlayer,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {

    var rotation by remember {
        mutableStateOf(0f)
    }

    var isPlaying by remember {
        mutableStateOf(
            musicPlayer.isPlaying()
        )
    }

    /*
     * Current playback position in milliseconds.
     */
    var currentPosition by remember {
        mutableStateOf(
            musicPlayer.getCurrentPosition()
        )
    }

    /*
     * Visual playback position used only for the waveform reveal.
     *
     * This is advanced from the frame clock instead of following the
     * player's reported position directly. That makes the reveal move
     * continuously between player position updates.
     */
    var smoothPosition by remember(track.id) {
        mutableStateOf(
            musicPlayer.getCurrentPosition().toFloat()
        )
    }

    var lastFrameTime by remember(track.id) {
        mutableStateOf(
            SystemClock.elapsedRealtime()
        )
    }

    var isSeeking by remember(track.id) {
        mutableStateOf(false)
    }

    var repeatMode by remember(track.id) {
        mutableStateOf(
            musicPlayer.getRepeatMode()
        )
    }

    var shuffleEnabled by remember(track.id) {
        mutableStateOf(
            musicPlayer.isShuffleEnabled()
        )
    }

    /*
     * Actual waveform generated from the current song.
     *
     * This is generated once for the selected track and
     * kept in memory while Now Playing is open.
     */
    var waveform by remember(track.id) {
        mutableStateOf(FloatArray(0))
    }

    LaunchedEffect(track.id) {
        waveform =
            withContext(Dispatchers.Default) {
                WaveformGenerator.generate(
                    filePath = track.filePath,
                    onProgress = { partialWaveform ->
                        waveform = partialWaveform
                    }
                )
            }
    }

    /*
     * Update playback state, position and vinyl
     * rotation continuously.
     */
    LaunchedEffect(track.id) {

        while (true) {

            isPlaying =
                musicPlayer.isPlaying()

            repeatMode =
                musicPlayer.getRepeatMode()

            shuffleEnabled =
                musicPlayer.isShuffleEnabled()

            val now =
                SystemClock.elapsedRealtime()

            val frameDelta =
                (now - lastFrameTime)
                    .coerceIn(0L, 50L)

            lastFrameTime = now

            if (!isSeeking) {
                val playerPosition =
                    musicPlayer.getCurrentPosition()

                currentPosition =
                    playerPosition

                if (isPlaying) {
                    /*
                     * Advance using real elapsed time so the reveal itself
                     * moves smoothly every frame instead of waiting for
                     * the player's position value to change.
                     */
                    smoothPosition +=
                        frameDelta.toFloat()

                    /*
                     * Keep the visual position synchronized with the real
                     * player without introducing visible jumps.
                     */
                    val difference =
                        playerPosition.toFloat() - smoothPosition

                    if (kotlin.math.abs(difference) > 150f) {
                        smoothPosition =
                            playerPosition.toFloat()
                    } else {
                        smoothPosition +=
                            difference * 0.08f
                    }

                    smoothPosition =
                        smoothPosition.coerceIn(
                            0f,
                            track.duration.toFloat()
                        )
                } else {
                    /*
                     * When paused, lock the visual reveal exactly to the
                     * actual playback position.
                     */
                    smoothPosition =
                        playerPosition.toFloat()
                }
            }

            if (isPlaying) {
                rotation =
                    (rotation + 1f) % 360f
            }

            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 22.dp
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 8.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                /*
                 * Wide Spotify-style down chevron.
                 */
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            onBack()
                        },
                    contentAlignment =
                        Alignment.Center
                ) {

                    Canvas(
                        modifier = Modifier
                            .size(
                                width = 26.dp,
                                height = 16.dp
                            )
                    ) {

                        val strokeWidth =
                            2.5.dp.toPx()

                        drawLine(
                            color = Color.White,
                            start =
                                androidx.compose.ui.geometry.Offset(
                                    x = 2.dp.toPx(),
                                    y = 3.dp.toPx()
                                ),
                            end =
                                androidx.compose.ui.geometry.Offset(
                                    x = size.width / 2f,
                                    y = size.height -
                                            3.dp.toPx()
                                ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )

                        drawLine(
                            color = Color.White,
                            start =
                                androidx.compose.ui.geometry.Offset(
                                    x = size.width / 2f,
                                    y = size.height -
                                            3.dp.toPx()
                                ),
                            end =
                                androidx.compose.ui.geometry.Offset(
                                    x = size.width -
                                            2.dp.toPx(),
                                    y = 3.dp.toPx()
                                ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                AlbumHeader(
                    album = track.album
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "⋮",
                    color = Color.White,
                    fontSize = 28.sp
                )
            }

            /*
             * Vinyl record area.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment =
                    Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(330.dp)
                        .rotate(rotation)
                ) {

                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        drawCircle(
                            color = Color(0xFF1D1D1D)
                        )

                        val grooveRadii =
                            listOf(
                                142f,
                                151f,
                                160f,
                                169f,
                                178f,
                                187f,
                                196f,
                                205f,
                                214f,
                                223f,
                                232f,
                                241f,
                                250f,
                                259f,
                                268f,
                                277f,
                                286f,
                                295f
                            )

                        grooveRadii.forEach { radius ->

                            drawCircle(
                                color =
                                    Color.White.copy(
                                        alpha = 0.055f
                                    ),
                                radius = radius,
                                style = Stroke(
                                    width = 1.5f
                                )
                            )
                        }

                        drawCircle(
                            color =
                                Color.White.copy(
                                    alpha = 0.035f
                                ),
                            radius = 125f,
                            style = Stroke(
                                width = 2f
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                    ) {

                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription =
                                "Album artwork",
                            modifier =
                                Modifier.fillMaxSize(),
                            contentScale =
                                ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFF111111)
                                )
                        )
                    }
                }
            }

            /*
             * Song information.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 21.sp,
                        maxLines = 1
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = track.artist,
                        color = Color(0xFFAAAAAA),
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(
                                alpha = 0.12f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "+",
                        color = Color.White,
                        fontSize = 29.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            /*
             * ECG / seismograph-style playback
             * visualizer.
             */
            val progress =
                if (track.duration > 0L) {
                    (
                            smoothPosition /
                                    track.duration.toFloat()
                            ).coerceIn(0f, 1f)
                } else {
                    0f
                }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .pointerInput(track.id, track.duration) {
                        detectTapGestures { offset ->
                            if (track.duration > 0L) {
                                val fraction =
                                    (offset.x / size.width)
                                        .coerceIn(0f, 1f)

                                val position =
                                    (track.duration * fraction)
                                        .toLong()

                                musicPlayer.seekTo(position)
                                currentPosition = position
                                smoothPosition = position.toFloat()
                                lastFrameTime = SystemClock.elapsedRealtime()
                            }
                        }
                    }
                    .pointerInput(track.id, track.duration) {
                        detectDragGestures(
                            onDragStart = {
                                isSeeking = true
                            },
                            onDrag = { change, _ ->
                                change.consume()

                                if (track.duration > 0L) {
                                    val fraction =
                                        (change.position.x / size.width)
                                            .coerceIn(0f, 1f)

                                    val position =
                                        (track.duration * fraction)
                                            .toLong()

                                    currentPosition = position
                                    smoothPosition = position.toFloat()
                                    lastFrameTime = SystemClock.elapsedRealtime()
                                }
                            },
                            onDragEnd = {
                                if (track.duration > 0L) {
                                    musicPlayer.seekTo(
                                        currentPosition
                                            .coerceIn(
                                                0L,
                                                track.duration
                                            )
                                    )
                                }

                                isSeeking = false
                            },
                            onDragCancel = {
                                isSeeking = false
                            }
                        )
                    }
            ) {

                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {

                    val centerY =
                        size.height / 2f

                    val width =
                        size.width

                    /*
                     * Block waveform.
                     *
                     * Each block represents a small section of the actual
                     * song waveform. Quiet sections stay short while louder
                     * sections become taller, including occasional large
                     * peaks extending above and below the center line.
                     */
                    val waveformData = waveform

                    if (waveformData.isNotEmpty()) {

                        val blockCount =
                            minOf(waveformData.size, 140)

                        val gap =
                            2.5f

                        val blockWidth =
                            (width -
                                    (gap * (blockCount - 1))) /
                                    blockCount

                        val safeBlockWidth =
                            blockWidth.coerceAtLeast(1.5f)

                        val revealPosition =
                            progress *
                                    blockCount.toFloat()

                        for (index in 0 until blockCount) {

                            val sampleIndex =
                                (
                                        index.toFloat() /
                                                blockCount.toFloat() *
                                                waveform.size.toFloat()
                                        )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        waveform.lastIndex
                                    )

                            val amplitude =
                                waveform[sampleIndex]
                                    .coerceIn(0f, 1f)

                            /*
                             * Keep quieter sections compact while allowing
                             * loud peaks to become long on both sides.
                             */
                            val normalizedHeight =
                                5f +
                                        (
                                                amplitude *
                                                        amplitude *
                                                        30f
                                                )

                            val left =
                                index *
                                        (safeBlockWidth + gap)

                            val right =
                                left + safeBlockWidth

                            val top =
                                centerY -
                                        normalizedHeight / 2f

                            val bottom =
                                centerY +
                                        normalizedHeight / 2f

                            /*
                             * Smooth fill for the block currently being
                             * reached. Completed blocks are fully filled,
                             * upcoming blocks remain invisible.
                             */
                            val blockProgress =
                                (
                                        revealPosition -
                                                index.toFloat()
                                        )
                                    .coerceIn(0f, 1f)

                            /*
                             * Only reveal the waveform up to the current
                             * playback position. Even if the decoder has
                             * already generated the whole waveform, it must
                             * not be visible ahead of the playback head.
                             *
                             * smoothPosition controls this reveal, keeping
                             * the moving edge smooth.
                             */
                            val visibleBlockCount =
                                kotlin.math.ceil(revealPosition)
                                    .toInt()
                                    .coerceIn(0, blockCount)

                            if (index >= visibleBlockCount) {
                                continue
                            }

                            if (amplitude <= 0.0001f) {
                                continue
                            }

                            val fillRight =
                                left +
                                        (
                                                safeBlockWidth *
                                                        blockProgress
                                                )

                            drawRoundRect(
                                color = Color.White,
                                topLeft =
                                    androidx.compose.ui.geometry.Offset(
                                        left,
                                        top
                                    ),
                                size =
                                    androidx.compose.ui.geometry.Size(
                                        (
                                                fillRight - left
                                                ).coerceAtLeast(0f),
                                        bottom - top
                                    ),
                                cornerRadius =
                                    androidx.compose.ui.geometry.CornerRadius(
                                        2.5f,
                                        2.5f
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                /*
                 * REAL current playback time.
                 */
                Text(
                    text = formatDuration(
                        currentPosition
                    ),
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )

                /*
                 * Total track duration.
                 */
                Text(
                    text = formatDuration(
                        track.duration
                    ),
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            /*
             * Main playback controls.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            shuffleEnabled =
                                musicPlayer.toggleShuffle()
                        },
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text = "🔀",
                        color =
                            if (shuffleEnabled) {
                                Color.White
                            } else {
                                Color.White.copy(
                                    alpha = 0.45f
                                )
                            },
                        fontSize = 24.sp
                    )
                }

                Text(
                    text = "◀",
                    color = Color.White,
                    fontSize = 30.sp,
                    modifier = Modifier.clickable {
                        onPrevious()
                    }
                )

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {

                            if (musicPlayer.isPlaying()) {

                                musicPlayer.pause()

                            } else {

                                musicPlayer.resume()
                            }

                            isPlaying =
                                musicPlayer.isPlaying()
                        },
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            if (isPlaying) {
                                "Ⅱ"
                            } else {
                                "▶"
                            },
                        color = Color.Black,
                        fontSize = 29.sp
                    )
                }

                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 30.sp,
                    modifier = Modifier.clickable {
                        onNext()
                    }
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            repeatMode =
                                musicPlayer.cycleRepeatMode()
                        },
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text =
                            when (repeatMode) {
                                MusicPlayer.REPEAT_ONE ->
                                    "↻¹"

                                MusicPlayer.REPEAT_ALL ->
                                    "↻"

                                else ->
                                    "↻"
                            },
                        color =
                            if (
                                repeatMode ==
                                MusicPlayer.REPEAT_OFF
                            ) {
                                Color.White.copy(
                                    alpha = 0.45f
                                )
                            } else {
                                Color.White
                            },
                        fontSize = 27.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            /*
             * Secondary controls.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "▣",
                    color = Color.White,
                    fontSize = 25.sp
                )

                Text(
                    text = "↗",
                    color = Color.White,
                    fontSize = 27.sp
                )

                Text(
                    text = "☰",
                    color = Color.White,
                    fontSize = 25.sp
                )
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            /*
             * Lyrics preview.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp
                        )
                    )
                    .background(
                        Color(0xFF9C075D)
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    )
            ) {

                Text(
                    text = "Lyrics preview",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}


/*
 * Album title marquee.
 *
 * The text starts completely outside the RIGHT
 * side of the box.
 *
 * Example:
 *
 * |                    |
 * |              One...|
 * |           One More |
 * |      One More Time |
 * |One More Time...    |
 *
 * Then it disappears to the LEFT and starts
 * again from the RIGHT.
 */
@Composable
private fun AlbumHeader(
    album: String
) {

    var containerWidthPx by remember {
        mutableStateOf(0)
    }

    var textWidthPx by remember {
        mutableStateOf(0)
    }

    var scrollPosition by remember {
        mutableStateOf(0f)
    }

    val needsMarquee =
        textWidthPx > containerWidthPx &&
                containerWidthPx > 0

    /*
     * Current marquee speed.
     */
    val speedPxPerSecond = 25f

    LaunchedEffect(
        album,
        containerWidthPx,
        textWidthPx
    ) {

        if (!needsMarquee) {

            scrollPosition = 0f

            return@LaunchedEffect
        }

        /*
         * Start completely outside the RIGHT edge.
         */
        scrollPosition =
            containerWidthPx.toFloat()

        /*
         * Small pause before entering.
         */
        delay(1200)

        while (true) {

            /*
             * Move from RIGHT to LEFT.
             */
            while (
                scrollPosition >
                -textWidthPx.toFloat()
            ) {

                scrollPosition -=
                    speedPxPerSecond *
                            16f /
                            1000f

                if (
                    scrollPosition <
                    -textWidthPx.toFloat()
                ) {

                    scrollPosition =
                        -textWidthPx.toFloat()
                }

                delay(16)
            }

            /*
             * Hold the end briefly.
             */
            delay(1200)

            /*
             * Restart from outside the RIGHT edge.
             */
            scrollPosition =
                containerWidthPx.toFloat()

            /*
             * Pause before next cycle.
             */
            delay(1200)
        }
    }

    Column(
        modifier = Modifier.width(220.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = "Playing from Album",
            color = Color.White.copy(
                alpha = 0.75f
            ),
            fontSize = 13.sp
        )

        Box(
            modifier = Modifier
                .width(220.dp)
                .height(28.dp)
                .clip(
                    RoundedCornerShape(4.dp)
                )
                .onSizeChanged {
                    containerWidthPx =
                        it.width
                }
        ) {

            Text(
                text = album,
                color = Color.White,
                fontSize = 17.sp,
                maxLines = 1,
                softWrap = false,

                modifier =
                    Modifier
                        .wrapContentWidth(
                            unbounded = true
                        )
                        .offset {
                            IntOffset(
                                x =
                                    scrollPosition
                                        .toInt(),
                                y = 0
                            )
                        },

                onTextLayout = {
                    textWidthPx =
                        it.size.width
                }
            )
        }
    }
}


/*
 * Convert milliseconds into:
 *
 * minutes:seconds
 *
 * Example:
 * 65000 -> 1:05
 */
private fun formatDuration(
    durationMillis: Long
): String {

    val totalSeconds =
        durationMillis / 1000

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds
    )
}