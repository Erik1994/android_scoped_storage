package com.example.scopedstorage

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
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
import com.example.scopedstorage.model.SharedStoragePhoto
import com.example.scopedstorage.util.sdk29AndUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

const val imageMaxSize = 500
class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private var internalStoragePhotoAdapter: InternalStoragePhotoAdapter? = null
    private var externalStoragePhotoAdapter: SharedStoragePhotoAdapter? = null
    private val resultContract = ActivityResultContracts.TakePicturePreview()

    private val contentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            if (isPermissionsGranted()) {
                setExternalStoragePhotoToResyclerView()
            }
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermissions = arrayListOf<String>()
            permissions.entries.forEach {
                if (!it.value) {
                    deniedPermissions.add(it.key)
                }
                deniedPermissions.size.takeIf { size -> size > 0 }?.let {
                    showDialog()
                } ?: run { setExternalStoragePhotoToResyclerView() }
            }
        }

    private val takePhoto = registerForActivityResult(resultContract) { bitmap ->
        bitmap?.let {
            lifecycleScope.launch {
                val isPrivate = binding?.switchPrivate?.isChecked ?: false
                val isSavedSuccessFully = when {
                    isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                    isPermissionsGranted() -> savePhotoToExternalStorage(
                        UUID.randomUUID().toString(),
                        it
                    )
                    else -> {
                        requestPermission()
                        false
                    }
                }
                if (isPrivate) {
                    setInternalStoragePhotoToResyclerView()
                }

                if (isSavedSuccessFully) {
                    Toast.makeText(this@MainActivity, "Photo saved successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save photo", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding?.let { setContentView(it.root) }
        setUpInternalStoragePhotoRv()
        setUpExternalStoragePhotoRv()
        observeData()
        setClickListener()
        if (!isPermissionsGranted()) {
            requestPermission()
        } else {
            setExternalStoragePhotoToResyclerView()
        }
        setInternalStoragePhotoToResyclerView()
    }

    private fun setClickListener() {
        binding?.apply {
            btnTakePhoto.setOnClickListener {
                takePhoto.launch(null)
            }
        }
    }

    private fun observeData() {
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
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

    private fun setUpInternalStoragePhotoRv() = binding?.rvPrivatePhotos?.apply {
        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            lifecycleScope.launch {
                val isDeltaionIsSuccessFully = deletePhotoFromInternalStorage(it.name)
                if (isDeltaionIsSuccessFully) {
                    setInternalStoragePhotoToResyclerView()
                    Toast.makeText(this@MainActivity, "Photo successfully deleted", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to delete photo", Toast.LENGTH_LONG).show()
                }
            }
        }
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun setUpExternalStoragePhotoRv() = binding?.rvPublicPhotos?.apply {
        externalStoragePhotoAdapter = SharedStoragePhotoAdapter {
//            val isDeltaionIsSuccessFully = deletePhotoFromInternalStorage(it.name)
//            if (isDeltaionIsSuccessFully) {
//                setInternalStoragePhotoToResyclerView()
//                Toast.makeText(context, "Photo successfully deleted", Toast.LENGTH_LONG).show()
//            } else {
//                Toast.makeText(context, "Failed to delete photo", Toast.LENGTH_LONG).show()
//            }
        }
        adapter = externalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun setExternalStoragePhotoToResyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromExternallStorage()
            externalStoragePhotoAdapter?.submitList(photos)
        }
    }

    private fun setInternalStoragePhotoToResyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromInternalStorage()
            internalStoragePhotoAdapter?.submitList(photos)
        }
    }

    /** Internal Storage **/

    private suspend fun savePhotoToInternalStorage(fileName: String, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            try {
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

    private suspend fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                deleteFile(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /** External Storage **/

    private suspend fun savePhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            // for writing we only have access to VOLUME_EXTERNAL_PRIMARY
            val imageCollaction = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            }
            try {
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
    }

    private suspend fun loadPhotoFromExternallStorage(): List<SharedStoragePhoto> {
        // for reading we can access to different Volumes in this case have been chosen VOLUME_EXTERNAL
        return withContext(Dispatchers.IO) {
            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE
            )

            val photos = mutableListOf<SharedStoragePhoto>()
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val conentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val bitmap = getBitmpFromUri(conentUri)
                    bitmap?.let {
                        photos.add(SharedStoragePhoto(id, displayName, width, height, it))
                    }
                    if(photos.size == 50) break
                }
                photos.toList()
            } ?: listOf()
        }
    }

    private suspend fun getBitmpFromUri(uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            contentResolver?.openInputStream(uri)?.use {
                val bitmap = BitmapFactory.decodeStream(it)
                getResizedBitmap(bitmap, imageMaxSize)
            }
        }
    }

    private suspend fun getResizedBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        return withContext(Dispatchers.IO) {
            var height = bitmap.height
            var width = bitmap.width
            val bitmapRatio = width.toFloat() / height
            bitmapRatio.takeIf { it > 1 }?.let {
                width = maxSize
                height = (width / bitmapRatio).toInt()
            } ?: run {
                height = maxSize
                width = (height * bitmapRatio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, width, height, true)
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

    private fun openSettingsScreen() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }.also {
            startActivity(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}