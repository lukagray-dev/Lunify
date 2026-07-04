package com.android.lunify.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.android.lunify.R
import com.android.lunify.databinding.ActivitySplashBinding
import com.android.lunify.home.activity.MainActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val SPLASH_DELAY = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        
        // Navigate to main after delay
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMain()
        }, SPLASH_DELAY)
    }

    private fun setupUI() {
        // Use the app's mipmap icon
        binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
