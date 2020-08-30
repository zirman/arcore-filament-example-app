package com.example.app

import android.app.Application
import android.os.Looper
import com.google.android.filament.utils.Utils
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins

class ExampleApplication : Application() {
    companion object {
        lateinit var instance: ExampleApplication private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        RxAndroidPlugins.setInitMainThreadSchedulerHandler {
            AndroidSchedulers.from(Looper.getMainLooper(), true)
        }

        RxJavaPlugins.setErrorHandler {
        }

        Utils.init()
    }
}
