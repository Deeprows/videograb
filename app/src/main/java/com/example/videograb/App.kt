package com.example.videograb

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.start(this)          // heavy yt-dlp setup runs on a background thread
        DownloadManager.init(this)
    }
}
