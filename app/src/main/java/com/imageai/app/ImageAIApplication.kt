package com.imageai.app

import android.app.Application
import com.imageai.app.di.initNetwork

class ImageAIApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initNetwork(this)
    }
}
