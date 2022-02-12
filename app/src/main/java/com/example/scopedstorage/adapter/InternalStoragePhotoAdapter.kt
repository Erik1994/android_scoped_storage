package com.example.scopedstorage.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scopedstorage.databinding.ItemPhotoBinding
import com.example.scopedstorage.model.InternalStoragePhoto

class InternalStoragePhotoAdapter(private val onPhotoClick: (InternalStoragePhoto) -> Unit) :
    ListAdapter<InternalStoragePhoto, InternalStoragePhotoAdapter.ViewHolder>(DiffCallBack()) {

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

        fun bind(photo: InternalStoragePhoto) {
            binding.apply {
                ivPhoto.setImageBitmap(photo.bmp)

                val aspectRatio = photo.bmp.width.toFloat() / photo.bmp.height.toFloat()
                ConstraintSet().apply {
                    clone(root)
                    setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                    applyTo(root)
                }
            }
        }
    }

    class DiffCallBack : DiffUtil.ItemCallback<InternalStoragePhoto>() {
        override fun areItemsTheSame(
            oldItem: InternalStoragePhoto,
            newItem: InternalStoragePhoto
        ): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: InternalStoragePhoto,
            newItem: InternalStoragePhoto
        ): Boolean =
            oldItem.name == newItem.name && oldItem.bmp.sameAs(newItem.bmp)
    }
}