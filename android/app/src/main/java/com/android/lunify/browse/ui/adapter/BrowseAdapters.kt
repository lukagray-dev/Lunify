package com.android.lunify.browse.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.lunify.R
import com.android.lunify.browse.ui.viewmodel.FeedSection
import com.android.lunify.databinding.ItemBrowseCardBinding
import com.android.lunify.databinding.ItemBrowseCarouselBinding
import com.android.lunify.databinding.ItemBrowseHeroBinding
import com.android.lunify.databinding.ItemBrowseQuickPickBinding
import com.android.lunify.databinding.ItemSongBinding
import com.android.lunify.download.data.model.ExtractedContent
import com.bumptech.glide.Glide

/**
 * Adapter for horizontal scroll card items (used in Home carousels)
 */
class TrackCarouselAdapter(
    private val tracks: List<ExtractedContent>,
    private val onTrackClick: (ExtractedContent) -> Unit
) : RecyclerView.Adapter<TrackCarouselAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBrowseCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBrowseCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = tracks[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvSubtitle.text = item.author ?: "YouTube"
        
        if (!item.duration.isNullOrEmpty()) {
            holder.binding.tvDuration.visibility = View.VISIBLE
            holder.binding.tvDuration.text = item.duration
        } else {
            holder.binding.tvDuration.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(item.thumbnailUrl)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(holder.binding.ivThumbnail)

        holder.itemView.setOnClickListener { onTrackClick(item) }
    }

    override fun getItemCount(): Int = tracks.size
}

/**
 * Adapter for vertical rows in the "Quick picks" section
 */
class QuickPicksAdapter(
    private val tracks: List<ExtractedContent>,
    private val onTrackClick: (ExtractedContent) -> Unit
) : RecyclerView.Adapter<QuickPicksAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBrowseQuickPickBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBrowseQuickPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = tracks[position]
        holder.binding.tvIndex.text = String.format("%02d", position + 1)
        holder.binding.tvTitle.text = item.title
        holder.binding.tvArtist.text = item.author ?: "YouTube"
        holder.binding.tvDuration.text = item.duration ?: ""

        Glide.with(holder.itemView.context)
            .load(item.thumbnailUrl)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(holder.binding.ivThumbnail)

        holder.itemView.setOnClickListener { onTrackClick(item) }
    }

    override fun getItemCount(): Int = minOf(3, tracks.size) // Only show top 3 items
}

/**
 * Vertical adapter holding all the dynamic sections in the Home feed (Hero, Quick Picks, Carousels)
 */
class HomeFeedAdapter(
    private val sections: List<FeedSection>,
    private val onTrackClick: (ExtractedContent) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_CAROUSEL = 1
    }

    class HeroViewHolder(val binding: ItemBrowseHeroBinding) : RecyclerView.ViewHolder(binding.root)
    class CarouselViewHolder(val binding: ItemBrowseCarouselBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        val section = sections[position]
        return if (section.hero) VIEW_TYPE_HERO else VIEW_TYPE_CAROUSEL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HERO) {
            HeroViewHolder(ItemBrowseHeroBinding.inflate(inflater, parent, false))
        } else {
            CarouselViewHolder(ItemBrowseCarouselBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val section = sections[position]

        if (holder is HeroViewHolder) {
            val firstTrack = section.tracks.firstOrNull()
            if (firstTrack != null) {
                holder.binding.tvHeroTitle.text = firstTrack.title
                holder.binding.tvHeroSubtitle.text = firstTrack.author ?: "Cinematic Special"
                holder.binding.tvHeroBrand.text = "LUNIFY ORIGINALS"

                Glide.with(holder.itemView.context)
                    .load(firstTrack.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(holder.binding.ivHeroBackdrop)

                holder.binding.btnHeroPlay.setOnClickListener { onTrackClick(firstTrack) }
                holder.itemView.setOnClickListener { onTrackClick(firstTrack) }
            }
        } else if (holder is CarouselViewHolder) {
            holder.binding.tvSectionTitle.text = section.title
            
            val context = holder.itemView.context
            val isQuickPicks = section.title.equals("Quick picks", ignoreCase = true)

            if (isQuickPicks) {
                // Vertical layout manager for Quick picks
                holder.binding.rvCarousel.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                holder.binding.rvCarousel.adapter = QuickPicksAdapter(section.tracks, onTrackClick)
            } else {
                // Horizontal layout manager for other carousels
                holder.binding.rvCarousel.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                holder.binding.rvCarousel.adapter = TrackCarouselAdapter(section.tracks, onTrackClick)
            }
        }
    }

    override fun getItemCount(): Int = sections.size
}

/**
 * Adapter for search results and history lists
 */
class SearchAdapter(
    private val tracks: List<ExtractedContent>,
    private val onTrackClick: (ExtractedContent) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = tracks[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvSubtitle.text = item.author ?: "YouTube Online"
        holder.binding.btnOptions.visibility = View.GONE // Hide local options button
        holder.binding.divider.visibility = View.VISIBLE

        Glide.with(holder.itemView.context)
            .load(item.thumbnailUrl)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(holder.binding.ivThumbnail)

        holder.itemView.setOnClickListener { onTrackClick(item) }
    }

    override fun getItemCount(): Int = tracks.size
}
