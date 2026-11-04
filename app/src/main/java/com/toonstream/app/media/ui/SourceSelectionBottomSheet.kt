package com.toonstream.app.media.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.toonstream.app.databinding.BottomSheetSourceSelectionBinding
import com.toonstream.app.databinding.ItemSourceEntryBinding
import com.toonstream.app.media.model.ExtractorLink

class SourceSelectionBottomSheet(
    private val sources: List<ExtractorLink>,
    private val currentLink: ExtractorLink?,
    private val onSourceSelected: (ExtractorLink) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSourceSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSourceSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textSourcesCount.text = "${sources.size} working sources resolved locally"

        val adapter = SourceAdapter { selected ->
            onSourceSelected(selected)
            dismiss()
        }

        binding.recyclerSources.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSources.adapter = adapter
        adapter.submitList(sources)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SourceAdapter(
        private val onItemClick: (ExtractorLink) -> Unit
    ) : ListAdapter<ExtractorLink, SourceAdapter.SourceViewHolder>(SourceDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
            val itemBinding = ItemSourceEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SourceViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class SourceViewHolder(private val itemBinding: ItemSourceEntryBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(link: ExtractorLink) {
                itemBinding.sourceName.text = link.sourceName
                itemBinding.sourceQuality.text = link.quality.label
                itemBinding.sourceFormat.text = link.type.name
                itemBinding.sourceLanguage.text = link.language

                if (link.isWorking) {
                    itemBinding.sourceStatus.text = "• Working"
                    itemBinding.sourceStatus.setTextColor(0xFF4CAF50.toInt())
                } else {
                    itemBinding.sourceStatus.text = "• Unverified"
                    itemBinding.sourceStatus.setTextColor(0xFFFFC107.toInt())
                }

                val isSelected = link.id == currentLink?.id || link.url == currentLink?.url
                itemBinding.iconSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

                itemBinding.root.setOnClickListener {
                    onItemClick(link)
                }
            }
        }
    }

    class SourceDiffCallback : DiffUtil.ItemCallback<ExtractorLink>() {
        override fun areItemsTheSame(oldItem: ExtractorLink, newItem: ExtractorLink): Boolean =
            oldItem.id == newItem.id && oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: ExtractorLink, newItem: ExtractorLink): Boolean =
            oldItem == newItem
    }
}
