package com.android.lunify.browse.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.lunify.browse.adapter.SearchAdapter
import com.android.lunify.browse.util.BrowseHistoryManager
import com.android.lunify.browse.util.playOnlineTrack
import com.android.lunify.browse.viewmodel.BrowseViewModel
import com.android.lunify.databinding.FragmentBrowseHomeBinding

/**
 * Handles online search results list and recently played history displays.
 * Reuses fragment_browse_home.xml layout since both are simple RecyclerView lists.
 */
class BrowseSearchFragment : Fragment() {

    private var _binding: FragmentBrowseHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowseViewModel by activityViewModels()
    private var isHistory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isHistory = it.getBoolean(ARG_IS_HISTORY, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeToRefresh()
        
        if (isHistory) {
            // History list display
            loadHistoryList()
        } else {
            // Active search results display
            observeSearchResults()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isHistory) {
            loadHistoryList() // Reload history if returning to the tab
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (isHistory) {
                loadHistoryList()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadHistoryList() {
        binding.swipeRefresh.isRefreshing = false
        val tracks = BrowseHistoryManager.getHistory(requireContext())
        binding.recyclerView.adapter = SearchAdapter(tracks) { item ->
            val category = viewModel.currentCategory.value ?: com.android.lunify.browse.viewmodel.BrowseCategory.MUSIC
            playOnlineTrack(this, viewModel, item, category)
        }
    }

    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { tracks ->
            binding.swipeRefresh.isRefreshing = false
            if (tracks != null) {
                binding.recyclerView.adapter = SearchAdapter(tracks) { item ->
                    val category = viewModel.currentCategory.value ?: com.android.lunify.browse.viewmodel.BrowseCategory.MUSIC
                    playOnlineTrack(this, viewModel, item, category)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_HISTORY = "arg_is_history"

        fun newInstance(isHistory: Boolean): BrowseSearchFragment {
            return BrowseSearchFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_HISTORY, isHistory)
                }
            }
        }
    }
}
