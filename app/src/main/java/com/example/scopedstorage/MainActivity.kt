package com.example.scopedstorage

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.scopedstorage.adapter.InternalStoragePhotoAdapter
import com.example.scopedstorage.adapter.SharedStoragePhotoAdapter
import com.example.scopedstorage.databinding.ActivityMainBinding
import com.example.scopedstorage.model.InternalStoragePhoto
import com.example.scopedstorage.util.sdk29AndUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private var internalStoragePhotoAdapter: InternalStoragePhotoAdapter? = null
    private var externalStoragePhotoAdapter: SharedStoragePhotoAdapter? = null


    private val resultContract = ActivityResultContracts.TakePicturePreview()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermissions = arrayListOf<String>()
            permissions.entries.forEach {
                if (!it.value) {
                    deniedPermissions.add(it.key)
                }
                deniedPermissions.size.takeIf { size -> size > 0 }?.let {
                    showDialog()
                }
            }
        }

    private val takePhoto = registerForActivityResult(resultContract) { bitmap ->
        bitmap?.let {
            val isPrivate = binding?.switchPrivate?.isChecked ?: false
            val isSavedSuccessFully = when {
                isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                isPermissionsGranted() -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
                else -> {
                    requestPermission()
                    false
                }
            }
            if (isPrivate) {
                setInternalStoragePhotoToResyclerView()
            }
            if (isSavedSuccessFully) {
                Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Failed to save photo", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun openSettingsScreen() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }.also {
            startActivity(it)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding?.let { setContentView(it.root) }
        if (!isPermissionsGranted()) {
            requestPermission()
        }
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

    private fun requestPermission() =
        requestPermissions.launch(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )

    private fun isPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun savePhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        val imageCollaction = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }
        return try {
            contentResolver.insert(imageCollaction, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Coldn't save bitmap")
                    }
                }
            } ?: throw IOException("Coldn't create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
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
            if (isDeltaionIsSuccessFully) {
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
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't save bitmap.")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun showDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission required")
            .setMessage("Please grant all permissions to use this app")
            .setNegativeButton("Cancel") { dialog: DialogInterface, _ ->
                dialog.dismiss()
                finish()
            }
            .setPositiveButton("Ok") { dialog: DialogInterface, _ ->
                dialog.dismiss()
                openSettingsScreen()

            }.show()
    }
}