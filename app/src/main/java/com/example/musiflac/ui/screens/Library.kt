package com.laizycoder.musiflac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.laizycoder.musiflac.data.MusicTrack

@Composable
fun LibraryScreen(
    musicTracks: List<MusicTrack>,
    paddingValues: PaddingValues,
    onTrackSelected: (MusicTrack) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 20.dp,
            bottom = 20.dp
        )
    ) {
        item {
            Text(
                text = "Library",
                fontSize = 28.sp
            )

            Text(
                text = "${musicTracks.size} songs",
                color = Color(0xFF888888),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )
        }

        items(
            items = musicTracks,
            key = { track -> track.id }
        ) { track ->
            SongItem(
                track = track,
                onClick = {
                    onTrackSelected(track)
                }
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )
        }
    }
}

@Composable
private fun SongItem(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFF1E1E1E))
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(
            artworkUri = track.artworkUri
        )

        Spacer(
            modifier = Modifier.width(14.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color.Transparent)
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1
            )

            Text(
                text = track.artist,
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
            )

            Text(
                text = track.album,
                color = Color(0xFF777777),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            text = formatDuration(track.duration),
            color = Color(0xFF888888),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⋮",
                color = Color.White,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun AlbumArtwork(
    artworkUri: String?
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF303030)),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "♫",
                color = Color(0xFF888888),
                fontSize = 24.sp
            )
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds
    )
}
