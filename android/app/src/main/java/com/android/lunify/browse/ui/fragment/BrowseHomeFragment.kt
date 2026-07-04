package com.android.lunify.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.lunify.browse.ui.adapter.HomeFeedAdapter
import com.android.lunify.browse.ui.util.playOnlineTrack
import com.android.lunify.browse.ui.viewmodel.BrowseCategory
import com.android.lunify.browse.ui.viewmodel.BrowseViewModel
import com.android.lunify.databinding.FragmentBrowseHomeBinding

/**
 * A sub-fragment within Browse that loads a dynamic feed of carousels.
 * Used for "Home", "Movies", and "TV Shows" discovery.
 */
class BrowseHomeFragment : Fragment() {

    private var _binding: FragmentBrowseHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowseViewModel by activityViewModels()
    private var tabId: String = "home"
    private var category: BrowseCategory = BrowseCategory.MUSIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabId = it.getString(ARG_TAB_ID) ?: "home"
            category = BrowseCategory.valueOf(it.getString(ARG_CATEGORY) ?: BrowseCategory.MUSIC.name)
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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCategoryFeed(category, tabId, forceRefresh = true)
        }
    }

    private fun observeViewModel() {
        val targetLiveData = when (category) {
            BrowseCategory.MUSIC -> viewModel.musicHomeFeed
            BrowseCategory.VIDEOS -> when (tabId) {
                "home" -> viewModel.videoHomeFeed
                "movies" -> viewModel.videoMoviesFeed
                "tv_shows" -> viewModel.videoTvShowsFeed
                else -> viewModel.videoHomeFeed
            }
        }

        targetLiveData.observe(viewLifecycleOwner) { sections ->
            binding.swipeRefresh.isRefreshing = false
            if (sections != null) {
                binding.recyclerView.adapter = HomeFeedAdapter(sections) { track ->
                    playOnlineTrack(this, viewModel, track, category)
                }
            }
        }

        // Lazy load the feed if it is empty when first mounted
        if (targetLiveData.value.isNullOrEmpty()) {
            viewModel.loadCategoryFeed(category, tabId, forceRefresh = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TAB_ID = "arg_tab_id"
        private const val ARG_CATEGORY = "arg_category"

        fun newInstance(tabId: String, category: BrowseCategory): BrowseHomeFragment {
            return BrowseHomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TAB_ID, tabId)
                    putString(ARG_CATEGORY, category.name)
                }
            }
        }
    }
}
