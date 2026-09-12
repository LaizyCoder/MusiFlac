package com.laizycoder.musiflac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.laizycoder.musiflac.data.MusicTrack
import com.laizycoder.musiflac.player.MusicPlayer
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(
    track: MusicTrack,
    musicPlayer: MusicPlayer,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var isPlaying by remember(track.id) {
        mutableStateOf(
            musicPlayer.isPlaying()
        )
    }

    LaunchedEffect(track.id) {
        while (true) {
            isPlaying =
                musicPlayer.isPlaying()

            delay(200)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 10.dp,
                vertical = 6.dp
            )
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                Color(0xFF242424)
            )
            .clickable {
                onOpen()
            }
            .padding(8.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .background(
                    Color(0xFF303030)
                )
        ) {

            AsyncImage(
                model = track.artworkUri,
                contentDescription =
                    "Album artwork",
                modifier =
                    Modifier.size(48.dp),
                contentScale =
                    ContentScale.Crop
            )
        }

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1
            )

            Text(
                text = track.artist,
                color =
                    Color(0xFFAAAAAA),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    onPrevious()
                },

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "◀",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(
                    RoundedCornerShape(12.dp)
                )
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
                color = Color.White,
                fontSize = 22.sp
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    onNext()
                },

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "▶",
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}