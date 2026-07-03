package com.android.lunify.converter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.lunify.converter.data.model.ConversionProgress
import com.android.lunify.converter.data.model.ConversionStatus
import com.android.lunify.converter.data.repository.PlaylistConverterRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for playlist conversion operations.
 * Manages conversion state and progress.
 */
class PlaylistConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistConverterRepository(application.applicationContext)

    private val _conversionProgress = MutableLiveData<ConversionProgress>()
    val conversionProgress: LiveData<ConversionProgress> = _conversionProgress

    private val _isConverting = MutableLiveData(false)
    val isConverting: LiveData<Boolean> = _isConverting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Start playlist conversion
     */
    fun convertPlaylist(spotifyUrl: String, playlistName: String? = null) {
        if (_isConverting.value == true) {
            _error.value = "Conversion already in progress"
            return
        }

        _isConverting.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                repository.convertPlaylist(spotifyUrl, playlistName).collect { progress ->
                    _conversionProgress.value = progress
                    
                    when (progress.status) {
                        ConversionStatus.COMPLETE -> {
                            _isConverting.value = false
                        }
                        ConversionStatus.ERROR -> {
                            _error.value = progress.message
                            _isConverting.value = false
                        }
                        else -> {
                            // Continue processing
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Conversion failed: ${e.message}"
                _isConverting.value = false
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Reset conversion state
     */
    fun reset() {
        _conversionProgress.value = null
        _isConverting.value = false
        _error.value = null
    }
}
