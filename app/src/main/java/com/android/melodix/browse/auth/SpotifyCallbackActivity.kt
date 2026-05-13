package com.android.melodix.browse.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.melodix.ui.activity.MainActivity
import kotlinx.coroutines.launch

/**
 * Handles OAuth callback from Spotify authorization flow.
 * Extracts authorization code and exchanges it for access token.
 */
class SpotifyCallbackActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SpotifyCallback"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get authorization code from intent
        val uri = intent?.data
        if (uri != null && uri.scheme == "com.android.melodix" && uri.host == "spotify-callback") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            
            when {
                code != null -> {
                    // Success - exchange code for token
                    handleAuthorizationCode(code)
                }
                error != null -> {
                    // User denied or error occurred
                    Log.e(TAG, "Authorization error: $error")
                    finishWithError("Authorization failed: $error")
                }
                else -> {
                    Log.e(TAG, "No code or error in callback")
                    finishWithError("Invalid callback")
                }
            }
        } else {
            Log.e(TAG, "Invalid callback URI: $uri")
            finishWithError("Invalid callback URI")
        }
    }

    private fun handleAuthorizationCode(code: String) {
        val authManager = SpotifyAuthManager.getInstance(applicationContext)
        
        lifecycleScope.launch {
            val result = authManager.handleAuthorizationCode(code)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully authenticated with Spotify")
                    finishWithSuccess()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to exchange code for token", error)
                    finishWithError("Authentication failed: ${error.message}")
                }
            )
        }
    }

    private fun finishWithSuccess() {
        // Navigate back to main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun finishWithError(message: String) {
        // Navigate back to main activity with error
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("spotify_auth_error", message)
        }
        startActivity(intent)
        finish()
    }
}
