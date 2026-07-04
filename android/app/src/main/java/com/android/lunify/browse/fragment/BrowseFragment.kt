package com.android.lunify.browse.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.lunify.R
import com.android.lunify.browse.viewmodel.BrowseCategory
import com.android.lunify.browse.viewmodel.BrowseViewModel
import com.android.lunify.databinding.FragmentBrowseBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Host fragment for the Browse tab.
 * Displays the category switcher dropdown ("Lunify Music" / "Lunify Videos") in the header,
 * handles category updates in BrowseViewModel, and binds tabs to ViewPager2.
 */
class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowseViewModel by activityViewModels()
    private var pagerAdapter: BrowsePagerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoryDropdown()
        setupSearchInput()
        observeViewModel()
    }

    private fun setupSearchInput() {
        binding.etSearch.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = textView.text.toString().trim()
                if (query.isNotEmpty()) {
                    // 1. Force ViewPager to search tab
                    val category = viewModel.currentCategory.value ?: BrowseCategory.MUSIC
                    val searchTabPosition = if (category == BrowseCategory.MUSIC) 1 else 3
                    binding.viewPager.currentItem = searchTabPosition
                    
                    // 2. Perform search
                    viewModel.performSearch(query)
                    
                    // 3. Hide keyboard
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(textView.windowToken, 0)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupCategoryDropdown() {
        binding.platformSelector.setOnClickListener {
            showCategoryDropdown(it)
        }
    }

    private fun showCategoryDropdown(anchor: View) {
        val context = anchor.context
        val listPopupWindow = ListPopupWindow(context).apply {
            setAnchorView(anchor)
            val categories = listOf("Lunify Music", "Lunify Videos")
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, categories))
            
            setOnItemClickListener { _, _, position, _ ->
                val selectedCategory = if (position == 0) BrowseCategory.MUSIC else BrowseCategory.VIDEOS
                viewModel.setCategory(selectedCategory)
                dismiss()
            }
        }
        listPopupWindow.show()
    }

    private fun observeViewModel() {
        viewModel.currentCategory.observe(viewLifecycleOwner) { category ->
            val platformName = when (category) {
                BrowseCategory.MUSIC -> "Lunify Music"
                BrowseCategory.VIDEOS -> "Lunify Videos"
            }
            binding.tvPlatformName.text = platformName
            binding.ivPlatformIcon.setImageResource(
                if (category == BrowseCategory.MUSIC) R.drawable.ic_music_note else R.drawable.ic_video
            )

            // Setup or refresh ViewPager with correct tabs for the selected category
            setupViewPager(category)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupViewPager(category: BrowseCategory) {
        val tabTitles = when (category) {
            BrowseCategory.MUSIC -> listOf("Home", "Search", "History", "Playlists")
            BrowseCategory.VIDEOS -> listOf("Home", "Movies", "TV Shows", "Search")
        }

        pagerAdapter = BrowsePagerAdapter(this, category, tabTitles.size)
        binding.viewPager.adapter = pagerAdapter

        // Reconnect TabLayoutMediator to refresh tabs
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BrowseFragment = BrowseFragment()
    }
}

/**
 * Adapter for the ViewPager2 containing Browse sub-fragments.
 */
class BrowsePagerAdapter(
    fragment: Fragment,
    private val category: BrowseCategory,
    private val tabCount: Int
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = tabCount

    override fun createFragment(position: Int): Fragment {
        return when (category) {
            BrowseCategory.MUSIC -> {
                when (position) {
                    0 -> BrowseHomeFragment.newInstance("home", BrowseCategory.MUSIC)
                    1 -> BrowseSearchFragment.newInstance(isHistory = false)
                    2 -> BrowseSearchFragment.newInstance(isHistory = true)
                    3 -> BrowsePlaylistsFragment.newInstance()
                    else -> BrowseHomeFragment.newInstance("home", BrowseCategory.MUSIC)
                }
            }
            BrowseCategory.VIDEOS -> {
                when (position) {
                    0 -> BrowseHomeFragment.newInstance("home", BrowseCategory.VIDEOS)
                    1 -> BrowseHomeFragment.newInstance("movies", BrowseCategory.VIDEOS)
                    2 -> BrowseHomeFragment.newInstance("tv_shows", BrowseCategory.VIDEOS)
                    3 -> BrowseSearchFragment.newInstance(isHistory = false)
                    else -> BrowseHomeFragment.newInstance("home", BrowseCategory.VIDEOS)
                }
            }
        }
    }
}
