package com.android.lunify.browse.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.lunify.R
import com.android.lunify.browse.adapter.SearchAdapter
import com.android.lunify.browse.util.playOnlineTrack
import com.android.lunify.browse.viewmodel.BrowseViewModel
import com.android.lunify.databinding.FragmentBrowsePlaylistsBinding
import com.android.lunify.databinding.ItemPlaylistBinding
import com.android.lunify.download.data.model.ExtractedContent
import com.android.lunify.download.engine.ytdlp.YtDlpAndroidEngine
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Fragment that displays and manages saved playlist URLs extracted via yt-dlp.
 */
class BrowsePlaylistsFragment : Fragment() {

    private var _binding: FragmentBrowsePlaylistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowseViewModel by activityViewModels()
    private val savedPlaylists = mutableListOf<SavedPlaylist>()
    private var adapter: PlaylistsAdapter? = null

    companion object {
        private const val TAG = "BrowsePlaylistsFragment"
        private const val FILENAME = "browse_playlists.json"
        
        fun newInstance() = BrowsePlaylistsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowsePlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadPlaylists()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        adapter = PlaylistsAdapter(savedPlaylists, 
            onPlaylistClick = { playlist -> showPlaylistTracks(playlist) },
            onPlaylistDelete = { playlist -> deletePlaylist(playlist) }
        )
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener {
            loadPlaylists()
        }
    }

    private fun setupListeners() {
        binding.btnSavePlaylist.setOnClickListener {
            val url = binding.etPlaylistUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                savePlaylistFromUrl(url)
            }
        }
    }

    private fun loadPlaylists() {
        binding.swipeRefresh.isRefreshing = false
        val file = File(requireContext().filesDir, FILENAME)
        savedPlaylists.clear()
        
        if (file.exists()) {
            try {
                val content = file.readText()
                val array = JSONArray(content)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tracksArray = obj.getJSONArray("tracks")
                    val tracksList = mutableListOf<ExtractedContent>()
                    for (j in 0 until tracksArray.length()) {
                        val tObj = tracksArray.getJSONObject(j)
                        tracksList.add(
                            ExtractedContent(
                                url = tObj.getString("url"),
                                title = tObj.getString("title"),
                                thumbnailUrl = tObj.optString("thumbnailUrl").takeIf { it.isNotEmpty() },
                                duration = tObj.optString("duration").takeIf { it.isNotEmpty() },
                                author = tObj.optString("author").takeIf { it.isNotEmpty() },
                                platform = tObj.optString("platform", "YouTube")
                            )
                        )
                    }
                    savedPlaylists.add(
                        SavedPlaylist(
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotEmpty() },
                            tracks = tracksList
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlists", e)
            }
        }
        
        adapter?.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.emptyState.visibility = if (savedPlaylists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun savePlaylistFromUrl(url: String) {
        binding.btnSavePlaylist.isEnabled = false
        binding.btnSavePlaylist.text = "..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val engine = YtDlpAndroidEngine(requireContext())
                engine.initialize()
                
                Log.d(TAG, "Extracting playlist: $url")
                val result = engine.extractContent(url)
                if (result.isSuccess) {
                    val playlistContent = result.getOrNull()
                    if (playlistContent != null && playlistContent.isPlaylist) {
                        val newPlaylist = SavedPlaylist(
                            title = playlistContent.title,
                            url = url,
                            thumbnailUrl = playlistContent.thumbnailUrl,
                            tracks = playlistContent.playlistItems
                        )
                        
                        withContext(Dispatchers.Main) {
                            persistPlaylist(newPlaylist)
                            binding.etPlaylistUrl.text.clear()
                            loadPlaylists()
                            Toast.makeText(requireContext(), "Playlist saved!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "URL is not a valid playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Extraction failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playlist", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving playlist: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnSavePlaylist.isEnabled = true
                    binding.btnSavePlaylist.text = "Save"
                }
            }
        }
    }

    private fun persistPlaylist(playlist: SavedPlaylist) {
        savedPlaylists.removeAll { it.url == playlist.url }
        savedPlaylists.add(0, playlist)
        saveAllPlaylistsToDisk()
    }

    private fun deletePlaylist(playlist: SavedPlaylist) {
        savedPlaylists.remove(playlist)
        saveAllPlaylistsToDisk()
        loadPlaylists()
        Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
    }

    private fun saveAllPlaylistsToDisk() {
        try {
            val array = JSONArray()
            for (p in savedPlaylists) {
                val obj = JSONObject().apply {
                    put("title", p.title)
                    put("url", p.url)
                    put("thumbnailUrl", p.thumbnailUrl ?: "")
                    
                    val tracksArray = JSONArray()
                    for (t in p.tracks) {
                        val tObj = JSONObject().apply {
                            put("url", t.url)
                            put("title", t.title)
                            put("thumbnailUrl", t.thumbnailUrl ?: "")
                            put("duration", t.duration ?: "")
                            put("author", t.author ?: "")
                            put("platform", t.platform)
                        }
                        tracksArray.put(tObj)
                    }
                    put("tracks", tracksArray)
                }
                array.put(obj)
            }
            
            val file = File(requireContext().filesDir, FILENAME)
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playlists to disk", e)
        }
    }

    private fun showPlaylistTracks(playlist: SavedPlaylist) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_duo_id, null)
        
        // Let's configure custom bottom sheet layout dynamically or create a simple recycler view
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.fragment_browse_home, null)
        val titleView = TextView(requireContext()).apply {
            text = playlist.title
            setPadding(24, 24, 24, 12)
            textSize = 18f
            setTextColor(resources.getColor(R.color.textPrimary, null))
            paint.isFakeBoldText = true
        }
        
        val container = view.findViewById<ViewGroup>(R.id.swipeRefresh).parent as ViewGroup
        container.addView(titleView, 0)
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = SearchAdapter(playlist.tracks) { track ->
            val category = viewModel.currentCategory.value ?: com.android.lunify.browse.viewmodel.BrowseCategory.MUSIC
            playOnlineTrack(this, viewModel, track, category)
            bottomSheetDialog.dismiss()
        }
        
        view.findViewById<View>(R.id.swipeRefresh).isEnabled = false
        
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private data class SavedPlaylist(
        val title: String,
        val url: String,
        val thumbnailUrl: String?,
        val tracks: List<ExtractedContent>
    )

    private class PlaylistsAdapter(
        private val playlists: List<SavedPlaylist>,
        private val onPlaylistClick: (SavedPlaylist) -> Unit,
        private val onPlaylistDelete: (SavedPlaylist) -> Unit
    ) : RecyclerView.Adapter<PlaylistsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = playlists[position]
            holder.binding.tvPlaylistTitle.text = item.title
            holder.binding.tvPlaylistInfo.text = "${item.tracks.size} tracks"

            Glide.with(holder.itemView.context)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .into(holder.binding.ivPlaylistThumbnail)

            holder.itemView.setOnClickListener { onPlaylistClick(item) }
            
            // Re-purpose chevron or options for delete button
            val deleteBtn = holder.itemView.findViewById<ImageButton>(R.id.btnOptions) ?: holder.binding.root.findViewById(R.id.btnOptions)
            deleteBtn?.setImageResource(R.drawable.ic_close)
            deleteBtn?.setOnClickListener {
                onPlaylistDelete(item)
            }
        }

        override fun getItemCount(): Int = playlists.size
    }
}
