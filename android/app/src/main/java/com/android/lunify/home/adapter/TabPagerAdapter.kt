package com.android.lunify.home.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.lunify.home.fragment.AlbumsFragment
import com.android.lunify.home.fragment.ArtistsFragment
import com.android.lunify.home.fragment.FoldersFragment
import com.android.lunify.home.fragment.SongsFragment
import com.android.lunify.home.fragment.VideosFragment

class TabPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val tabTitles = listOf("Songs", "Videos", "Artists", "Albums", "Folders")

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SongsFragment.newInstance()
            1 -> VideosFragment.newInstance()
            2 -> ArtistsFragment.newInstance()
            3 -> AlbumsFragment.newInstance()
            4 -> FoldersFragment.newInstance()
            else -> SongsFragment.newInstance()
        }
    }

    fun getTabTitle(position: Int): String = tabTitles[position]
}
