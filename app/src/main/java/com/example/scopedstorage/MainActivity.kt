package com.example.scopedstorage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.scopedstorage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding?.let { setContentView(it.root) }


    }
}