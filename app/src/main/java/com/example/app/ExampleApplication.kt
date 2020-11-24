package com.example.app

import android.app.Application
import com.google.android.filament.utils.Utils

class ExampleApplication : Application() {
    companion object {
        lateinit var instance: ExampleApplication private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Utils.init()
    }
}
