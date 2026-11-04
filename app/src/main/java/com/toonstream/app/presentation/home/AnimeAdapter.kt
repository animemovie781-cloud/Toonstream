package com.toonstream.app.presentation.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.toonstream.app.databinding.ItemAnimeCardBinding
import com.toonstream.app.domain.model.ContentItem

class AnimeAdapter(
    private val onItemClick: (ContentItem) -> Unit
) : ListAdapter<ContentItem, AnimeAdapter.AnimeViewHolder>(AnimeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimeViewHolder {
        val binding = ItemAnimeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AnimeViewHolder(private val binding: ItemAnimeCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContentItem) {
            binding.titleText.text = item.title

            if (item.posterUrl.isNotBlank()) {
                binding.posterImage.load(item.posterUrl) {
                    crossfade(true)
                }
            } else {
                binding.posterImage.setImageDrawable(null)
            }

            // Rating badge
            if (!item.rating.isNullOrBlank()) {
                binding.ratingBadge.visibility = View.VISIBLE
                binding.ratingBadge.text = "★ ${item.rating}"
            } else {
                binding.ratingBadge.visibility = View.GONE
            }

            // Episode / Season / Type badge
            val badgeText = when {
                !item.episodeLabel.isNullOrBlank() -> item.episodeLabel
                !item.seasonLabel.isNullOrBlank() -> item.seasonLabel
                item.type.isNotBlank() -> item.type
                else -> null
            }

            if (!badgeText.isNullOrBlank()) {
                binding.typeBadge.visibility = View.VISIBLE
                binding.typeBadge.text = badgeText
            } else {
                binding.typeBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class AnimeDiffCallback : DiffUtil.ItemCallback<ContentItem>() {
        override fun areItemsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean = oldItem == newItem
    }
}
