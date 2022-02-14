package com.example.scopedstorage.model

import android.graphics.Bitmap

data class SharedStoragePhoto(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Bitmap
)