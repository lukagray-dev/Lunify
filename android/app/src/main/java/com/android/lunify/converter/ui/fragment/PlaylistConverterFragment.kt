package com.android.lunify.converter.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.android.lunify.converter.data.model.ConversionStatus
import com.android.lunify.converter.ui.viewmodel.PlaylistConverterViewModel
import com.android.lunify.databinding.FragmentPlaylistConverterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Fragment for converting Spotify playlists to YouTube playlists.
 */
class PlaylistConverterFragment : Fragment() {

    private var _binding: FragmentPlaylistConverterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlaylistConverterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistConverterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnConvert.setOnClickListener {
            val url = binding.etPlaylistUrl.text?.toString()?.trim()
            if (url.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please enter a Spotify playlist URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidSpotifyUrl(url)) {
                Toast.makeText(requireContext(), "Invalid Spotify playlist URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val playlistName = binding.etPlaylistName.text?.toString()?.trim()
            startConversion(url, playlistName)
        }
    }

    private fun isValidSpotifyUrl(url: String): Boolean {
        // Support multiple Spotify URL formats:
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
        // spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
        return url.contains("open.spotify.com/playlist/") || 
               url.contains("spotify.com/playlist/") ||
               url.contains("spotify:playlist:")
    }

    private fun startConversion(url: String, playlistName: String?) {
        // Show confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Convert Playlist?")
            .setMessage("This will create a new YouTube playlist with matching tracks. This may take several minutes.")
            .setPositiveButton("Convert") { _, _ ->
                viewModel.convertPlaylist(url, playlistName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.conversionProgress.observe(viewLifecycleOwner) { progress ->
            when (progress.status) {
                ConversionStatus.IDLE -> {
                    binding.progressSection.visibility = View.GONE
                    binding.btnConvert.isEnabled = true
                }
                ConversionStatus.FETCHING_PLAYLIST,
                ConversionStatus.SEARCHING_TRACKS,
                ConversionStatus.CREATING_PLAYLIST,
                ConversionStatus.ADDING_VIDEOS -> {
                    binding.progressSection.visibility = View.VISIBLE
                    binding.btnConvert.isEnabled = false
                    binding.tvProgressStatus.text = progress.message
                    binding.progressBar.progress = progress.percentage
                    binding.tvProgressDetails.text = "${progress.currentTrack} / ${progress.totalTracks} tracks"
                }
                ConversionStatus.COMPLETE -> {
                    binding.progressSection.visibility = View.GONE
                    binding.btnConvert.isEnabled = true
                    showSuccessDialog(progress.message)
                    // Clear inputs
                    binding.etPlaylistUrl.text?.clear()
                    binding.etPlaylistName.text?.clear()
                }
                ConversionStatus.ERROR -> {
                    binding.progressSection.visibility = View.GONE
                    binding.btnConvert.isEnabled = true
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Conversion Failed")
                    .setMessage(it)
                    .setPositiveButton("OK") { _, _ ->
                        viewModel.clearError()
                    }
                    .show()
            }
        }
    }

    private fun showSuccessDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Conversion Complete!")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PlaylistConverterFragment()
    }
}
