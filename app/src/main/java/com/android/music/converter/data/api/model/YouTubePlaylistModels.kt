package com.android.music.converter.data.api.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a YouTube playlist
 */
data class CreatePlaylistRequest(
    @SerializedName("snippet") val snippet: PlaylistSnippet,
    @SerializedName("status") val status: PlaylistStatus? = null
)

data class PlaylistSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("defaultLanguage") val defaultLanguage: String? = "en"
)

data class PlaylistStatus(
    @SerializedName("privacyStatus") val privacyStatus: String = "public" // public, private, unlisted
)

/**
 * Response from creating a YouTube playlist
 */
data class CreatePlaylistResponse(
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: PlaylistSnippet,
    @SerializedName("status") val status: PlaylistStatus
)

/**
 * Request body for adding a video to a playlist
 */
data class AddVideoToPlaylistRequest(
    @SerializedName("snippet") val snippet: PlaylistItemSnippet
)

data class PlaylistItemSnippet(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("resourceId") val resourceId: ResourceId
)

data class ResourceId(
    @SerializedName("kind") val kind: String = "youtube#video",
    @SerializedName("videoId") val videoId: String
)

/**
 * Response from adding a video to a playlist
 */
data class AddVideoToPlaylistResponse(
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: PlaylistItemSnippet
)
