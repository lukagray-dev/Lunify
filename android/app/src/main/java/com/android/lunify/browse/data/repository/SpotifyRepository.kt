package com.android.lunify.browse.data.repository

import android.content.Context
import android.util.Log
import com.android.lunify.browse.auth.SpotifyAuthManager
import com.android.lunify.browse.data.api.SpotifyApiService
import com.android.lunify.browse.data.mapper.SpotifyMapper.toSpotifyAlbum
import com.android.lunify.browse.data.mapper.SpotifyMapper.toSpotifyArtist
import com.android.lunify.browse.data.mapper.SpotifyMapper.toSpotifyPlaylist
import com.android.lunify.browse.data.mapper.SpotifyMapper.toSpotifyTrack
import com.android.lunify.browse.data.mapper.SpotifyMapper.toSpotifyUserProfile
import com.android.lunify.browse.data.model.*
import com.android.lunify.browse.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Repository for Spotify data operations.
 * Connects to Spotify Web API for all data.
 */
class SpotifyRepository(context: Context? = null) {

    private val apiService: SpotifyApiService = NetworkModule.spotifyApiService
    private val authManager: SpotifyAuthManager? = context?.let { SpotifyAuthManager.getInstance(it) }

    /**
     * Get OAuth token from authenticated user
     */
    private suspend fun getOAuthToken(): String? {
        return try {
            authManager?.getAccessToken()
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error getting OAuth token", e)
            null
        }
    }

    /**
     * Get home content (featured playlists, new releases, recommendations)
     */
    fun getHomeContent(): Flow<Result<SpotifyHomeContent>> = flow {
        try {
            Log.d("SpotifyRepository", "Loading home content")
            val token = getOAuthToken()
            if (token == null) {
                Log.e("SpotifyRepository", "No OAuth token available")
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            Log.d("SpotifyRepository", "Token obtained, making API calls")
            NetworkModule.spotifyOauthToken = token

            // Get featured playlists
            val featuredResponse = apiService.getFeaturedPlaylists(limit = 20)
            val featuredPlaylists = if (featuredResponse.isSuccessful) {
                Log.d("SpotifyRepository", "Featured playlists: ${featuredResponse.body()?.playlists?.items?.size ?: 0}")
                featuredResponse.body()?.playlists?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()
            } else {
                Log.e("SpotifyRepository", "Featured playlists failed: ${featuredResponse.code()} - ${featuredResponse.errorBody()?.string()}")
                emptyList()
            }

            // Get new releases - skip if fails (may require additional permissions)
            val newReleases = try {
                val newReleasesResponse = apiService.getNewReleases(limit = 20)
                if (newReleasesResponse.isSuccessful) {
                    Log.d("SpotifyRepository", "New releases: ${newReleasesResponse.body()?.albums?.items?.size ?: 0}")
                    newReleasesResponse.body()?.albums?.items?.map { it.toSpotifyAlbum() } ?: emptyList()
                } else {
                    Log.w("SpotifyRepository", "New releases failed: ${newReleasesResponse.code()}, skipping")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.w("SpotifyRepository", "New releases error, skipping", e)
                emptyList()
            }

            // Get user's top tracks as recommendations instead of seed-based recommendations
            val recommendations = try {
                val topTracksResponse = apiService.getUserTopTracks(timeRange = "short_term", limit = 20)
                if (topTracksResponse.isSuccessful) {
                    Log.d("SpotifyRepository", "Top tracks: ${topTracksResponse.body()?.items?.size ?: 0}")
                    topTracksResponse.body()?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                } else {
                    Log.w("SpotifyRepository", "Top tracks failed: ${topTracksResponse.code()}, trying recently played")
                    // Fallback to recently played
                    val recentResponse = apiService.getRecentlyPlayed(limit = 20)
                    if (recentResponse.isSuccessful) {
                        recentResponse.body()?.items?.map { it.track.toSpotifyTrack() } ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.w("SpotifyRepository", "Recommendations error, skipping", e)
                emptyList()
            }

            val content = SpotifyHomeContent(
                featuredPlaylists = featuredPlaylists,
                newReleases = newReleases,
                recommendations = recommendations
            )
            Log.d("SpotifyRepository", "Home content loaded: ${featuredPlaylists.size} playlists, ${newReleases.size} releases, ${recommendations.size} tracks")
            emit(Result.success(content))
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading home content", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search for tracks, albums, artists, playlists
     */
    fun search(query: String): Flow<Result<SpotifySearchResult>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.search(query = query, limit = 20)

            if (response.isSuccessful) {
                val searchResponse = response.body()
                val tracks = searchResponse?.tracks?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                val albums = searchResponse?.albums?.items?.map { it.toSpotifyAlbum() } ?: emptyList()
                val artists = searchResponse?.artists?.items?.map { it.toSpotifyArtist() } ?: emptyList()
                val playlists = searchResponse?.playlists?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()

                emit(Result.success(SpotifySearchResult(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    playlists = playlists
                )))
            } else {
                emit(Result.failure(Exception("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error searching", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's library (saved tracks)
     */
    fun getSavedTracks(): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            Log.d("SpotifyRepository", "Loading saved tracks")
            val token = getOAuthToken()
            if (token == null) {
                Log.e("SpotifyRepository", "No OAuth token available")
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getSavedTracks(limit = 50)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.track.toSpotifyTrack() } ?: emptyList()
                Log.d("SpotifyRepository", "Loaded ${tracks.size} saved tracks")
                emit(Result.success(tracks))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("SpotifyRepository", "Failed to load saved tracks: ${response.code()} - $errorBody")
                
                // Provide helpful error message for 403
                val errorMessage = when (response.code()) {
                    403 -> "Access denied. Please ensure:\n1. Your Spotify app has 'user-library-read' scope enabled\n2. Your account is added to the app's allowlist in Spotify Developer Dashboard\n3. You've re-authenticated after adding scopes"
                    401 -> "Authentication expired. Please sign in again."
                    else -> "Failed to load saved tracks: ${response.code()}"
                }
                emit(Result.failure(Exception(errorMessage)))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading saved tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's saved albums
     */
    fun getSavedAlbums(): Flow<Result<List<SpotifyAlbum>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getSavedAlbums(limit = 50)

            if (response.isSuccessful) {
                val albums = response.body()?.items?.map { it.album.toSpotifyAlbum() } ?: emptyList()
                emit(Result.success(albums))
            } else {
                emit(Result.failure(Exception("Failed to load saved albums: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading saved albums", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's playlists
     */
    fun getUserPlaylists(): Flow<Result<List<SpotifyPlaylist>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getUserPlaylists(limit = 50)

            if (response.isSuccessful) {
                val playlists = response.body()?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()
                emit(Result.success(playlists))
            } else {
                emit(Result.failure(Exception("Failed to load playlists: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading playlists", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user profile
     */
    fun getUserProfile(): Flow<Result<SpotifyUserProfile>> = flow {
        try {
            Log.d("SpotifyRepository", "Loading user profile")
            val token = getOAuthToken()
            if (token == null) {
                Log.e("SpotifyRepository", "No OAuth token available")
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getCurrentUserProfile()

            if (response.isSuccessful) {
                val profile = response.body()?.toSpotifyUserProfile()
                if (profile != null) {
                    Log.d("SpotifyRepository", "Profile loaded: ${profile.displayName}")
                    emit(Result.success(profile))
                } else {
                    emit(Result.failure(Exception("Profile not found")))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("SpotifyRepository", "Failed to load profile: ${response.code()} - $errorBody")
                
                val errorMessage = when (response.code()) {
                    403 -> "Access denied. Please ensure your Spotify app has 'user-read-private' and 'user-read-email' scopes enabled."
                    401 -> "Authentication expired. Please sign in again."
                    else -> "Failed to load profile: ${response.code()}"
                }
                emit(Result.failure(Exception(errorMessage)))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading profile", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get playlist tracks
     */
    fun getPlaylistTracks(playlistId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getPlaylistTracks(playlistId, limit = 100)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.mapNotNull { it.track?.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load playlist tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading playlist tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get album tracks
     */
    fun getAlbumTracks(albumId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getAlbumTracks(albumId, limit = 50)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load album tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading album tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get artist top tracks
     */
    fun getArtistTopTracks(artistId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getArtistTopTracks(artistId)

            if (response.isSuccessful) {
                val tracks = response.body()?.tracks?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load artist tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading artist tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's top tracks
     */
    fun getUserTopTracks(): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getUserTopTracks(timeRange = "medium_term", limit = 20)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load top tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading top tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}
