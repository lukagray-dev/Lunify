package com.android.melodix.browse.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.melodix.browse.ui.fragment.BrowseHomeFragment
import com.android.melodix.browse.ui.fragment.BrowsePlaylistsFragment
import com.android.melodix.browse.ui.fragment.BrowseProfileFragment
import com.android.melodix.browse.ui.fragment.BrowseShortsFragment
import com.android.melodix.browse.ui.fragment.BrowseSubscriptionsFragment

class BrowseTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val tabs = listOf(
        TabInfo("Home", BrowseHomeFragment::class.java),
        TabInfo("Shorts", BrowseShortsFragment::class.java),
        TabInfo("Subscriptions", BrowseSubscriptionsFragment::class.java),
        TabInfo("Playlists", BrowsePlaylistsFragment::class.java),
        TabInfo("Profile", BrowseProfileFragment::class.java)
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return tabs[position].fragmentClass.getDeclaredConstructor().newInstance()
    }

    fun getTabTitle(position: Int): String = tabs[position].title

    data class TabInfo(
        val title: String,
        val fragmentClass: Class<out Fragment>
    )
}
