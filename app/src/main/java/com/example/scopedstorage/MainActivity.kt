package com.example.scopedstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.scopedstorage.adapter.InternalStoragePhotoAdapter
import com.example.scopedstorage.databinding.ActivityMainBinding
import com.example.scopedstorage.model.InternalStoragePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private var internalStoragePhotoAdapter: InternalStoragePhotoAdapter? = null

    private val resultContract = ActivityResultContracts.TakePicturePreview()

    private val takePhoto = registerForActivityResult(resultContract) { bitmap ->
        bitmap?.let {
            val isPrivate = binding?.switchPrivate?.isChecked ?: false
            if (isPrivate) {
                val isSavedSuccessFully = savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                if(isSavedSuccessFully) {
                    setInternalStoragePhotoToResyclerView()
                    Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save photo", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding?.let { setContentView(it.root) }
        setUpInternalStoragePhotoRv()
        setInternalStoragePhotoToResyclerView()
        setClickListener()
    }

    private fun setClickListener() {
        binding?.apply {
            btnTakePhoto.setOnClickListener {
                takePhoto.launch(null)
            }
        }
    }

    private fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun setUpInternalStoragePhotoRv() = binding?.rvPrivatePhotos?.apply {
        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeltaionIsSuccessFully = deletePhotoFromInternalStorage(it.name)
            if(isDeltaionIsSuccessFully) {
                setInternalStoragePhotoToResyclerView()
                Toast.makeText(context, "Photo successfully deleted", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to delete photo", Toast.LENGTH_LONG).show()
            }
        }
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun setInternalStoragePhotoToResyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromInternalStorage()
            internalStoragePhotoAdapter?.submitList(photos)
        }
    }

    private suspend fun loadPhotoFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bitmap)
            } ?: listOf()
        }
    }

    private fun savePhotoToInternalStorage(fileName: String, bitmap: Bitmap): Boolean {
        return try {
            openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't save bitmap.")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}