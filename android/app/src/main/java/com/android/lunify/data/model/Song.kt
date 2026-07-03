package com.android.lunify.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArtUri: Uri?,
    val dateAdded: Long = 0,
    val playCount: Int = 0
) : Parcelable {

    val subtitle: String
        get() = "$artist | $album"
}

