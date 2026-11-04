package com.toonstream.app.presentation.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.toonstream.app.databinding.ItemEpisodeCardBinding
import com.toonstream.app.domain.model.EpisodeItem

class EpisodeAdapter(
    private val onEpisodeClick: (EpisodeItem) -> Unit
) : ListAdapter<EpisodeItem, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EpisodeViewHolder(private val binding: ItemEpisodeCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: EpisodeItem) {
            binding.episodeTitle.text = episode.title
            binding.episodeSubtitle.text = "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}"

            if (!episode.thumbnailUrl.isNullOrBlank()) {
                binding.episodeThumbnail.load(episode.thumbnailUrl) {
                    crossfade(true)
                }
            } else {
                binding.episodeThumbnail.setImageDrawable(null)
            }

            binding.root.setOnClickListener { onEpisodeClick(episode) }
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<EpisodeItem>() {
        override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean = oldItem == newItem
    }
}
