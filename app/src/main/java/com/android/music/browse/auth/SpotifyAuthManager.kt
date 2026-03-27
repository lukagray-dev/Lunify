package com.android.music.browse.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages Spotify OAuth2 authentication using PKCE flow.
 * Handles authorization, token management, and refresh for Spotify API access.
 */
class SpotifyAuthManager(context: Context) {

    companion object {
        private const val TAG = "SpotifyAuthManager"

        // OAuth configuration
        private const val CLIENT_ID = "d8aa004a8bf0458198a4d8b4952dfeb6"
        private const val CLIENT_SECRET = "f9bb8844acb740b083dabb3e31b44e47"
        private const val REDIRECT_URI = "com.android.music://spotify-callback"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        
        // Scopes for Spotify API access
        private const val SCOPES = "user-read-private user-read-email " +
                "user-library-read user-library-modify " +
                "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private " +
                "user-top-read user-read-recently-played " +
                "streaming user-read-playback-state user-modify-playback-state"
        
        // SharedPreferences keys
        private const val PREFS_NAME = "spotify_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_CODE_VERIFIER = "code_verifier"
        
        @Volatile
        private var instance: SpotifyAuthManager? = null

        fun getInstance(context: Context): SpotifyAuthManager {
            return instance ?: synchronized(this) {
                instance ?: SpotifyAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _authState = MutableStateFlow<SpotifyAuthState>(SpotifyAuthState.NotAuthenticated)
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val tokenExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            _authState.value = SpotifyAuthState.Authenticated(accessToken)
        } else if (prefs.getString(KEY_REFRESH_TOKEN, null) != null) {
            // Token expired but we have refresh token
            _authState.value = SpotifyAuthState.TokenExpired
        }
    }

    /**
     * Handle authorization callback with code
     */
    suspend fun handleAuthorizationCode(code: String): Result<String> {
        return try {
            val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return Result.failure(Exception("Code verifier not found"))
            
            Log.d(TAG, "Exchanging authorization code for token")
            
            // Exchange code for access token
            val response = exchangeCodeForToken(code, codeVerifier)
            
            response.fold(
                onSuccess = { tokenData ->
                    Log.d(TAG, "Token exchange successful")
                    saveTokenData(tokenData)
                    _authState.value = SpotifyAuthState.Authenticated(tokenData.accessToken)
                    Result.success(tokenData.accessToken)
                },
                onFailure = { error ->
                    Log.e(TAG, "Token exchange failed", error)
                    _authState.value = SpotifyAuthState.Error("Authorization failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling authorization code", e)
            _authState.value = SpotifyAuthState.Error("Authorization failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Start authorization flow - launches browser for user consent
     */
    fun startAuthorization(context: Context) {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        
        // Save code verifier for later use
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()
        
        // Build authorization URL
        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", SCOPES)
            .build()
        
        // Launch browser
        val intent = Intent(Intent.ACTION_VIEW, authUri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        
        _authState.value = SpotifyAuthState.Loading
    }

    /**
     * Exchange authorization code for access token
     */
    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): Result<TokenData> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            
            val requestBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code_verifier", codeVerifier)
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                val expiresIn = json.getLong("expires_in")
                
                Result.success(TokenData(accessToken, refreshToken, expiresIn))
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code} - $responseBody")
                Result.failure(Exception("Token exchange failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            ?: return@withContext Result.failure(Exception("No refresh token available"))
        
        try {
            val client = OkHttpClient()
            
            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val accessToken = json.getString("access_token")
                val expiresIn = json.getLong("expires_in")
                
                // Update stored token
                val tokenData = TokenData(accessToken, refreshToken, expiresIn)
                saveTokenData(tokenData)
                _authState.value = SpotifyAuthState.Authenticated(accessToken)
                
                Result.success(accessToken)
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code} - $responseBody")
                _authState.value = SpotifyAuthState.Error("Token refresh failed")
                Result.failure(Exception("Token refresh failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            _authState.value = SpotifyAuthState.Error("Token refresh error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get current access token, refreshing if needed
     */
    suspend fun getAccessToken(): String? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val tokenExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        return if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            accessToken
        } else {
            // Try to refresh
            refreshAccessToken().getOrNull()
        }
    }

    /**
     * Sign out and clear tokens
     */
    fun signOut() {
        prefs.edit().clear().apply()
        _authState.value = SpotifyAuthState.NotAuthenticated
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is SpotifyAuthState.Authenticated
    }

    private fun saveTokenData(tokenData: TokenData) {
        val expiryTime = System.currentTimeMillis() + (tokenData.expiresIn * 1000)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokenData.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokenData.refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
    }

    // PKCE helper methods
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class TokenData(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long
    )
}

/**
 * Represents the authentication state for Spotify
 */
sealed class SpotifyAuthState {
    object NotAuthenticated : SpotifyAuthState()
    object Loading : SpotifyAuthState()
    object TokenExpired : SpotifyAuthState()
    data class Authenticated(val accessToken: String) : SpotifyAuthState()
    data class Error(val message: String) : SpotifyAuthState()
}
