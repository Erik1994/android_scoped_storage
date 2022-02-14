package com.example.scopedstorage.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scopedstorage.databinding.ItemPhotoBinding
import com.example.scopedstorage.model.SharedStoragePhoto

class SharedStoragePhotoAdapter(private val onPhotoClick: (SharedStoragePhoto) -> Unit) :
    ListAdapter<SharedStoragePhoto, SharedStoragePhotoAdapter.ViewHolder>(DiffCallBack()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(currentList[position])
    }

    inner class ViewHolder(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.ivPhoto.setOnLongClickListener {
                onPhotoClick(currentList[adapterPosition])
                true
            }
        }

        fun bind(photo: SharedStoragePhoto) {
            binding.apply {
                ivPhoto.setImageBitmap(photo.contentUri)

                val aspectRatio = photo.width.toFloat() / photo.height.toFloat()
                ConstraintSet().apply {
                    clone(root)
                    setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                    applyTo(root)
                }
            }
        }
    }

    class DiffCallBack : DiffUtil.ItemCallback<SharedStoragePhoto>() {
        override fun areItemsTheSame(
            oldItem: SharedStoragePhoto,
            newItem: SharedStoragePhoto
        ): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: SharedStoragePhoto,
            newItem: SharedStoragePhoto
        ): Boolean =
            oldItem == newItem
    }
}