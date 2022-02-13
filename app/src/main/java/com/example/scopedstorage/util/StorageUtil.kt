package com.example.scopedstorage.util

import android.os.Build

inline fun <T> sdk29AndUp(onSdk: () -> T): T? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onSdk()
    } else null
}