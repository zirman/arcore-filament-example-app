package com.example.app

import android.app.Activity
import android.view.View
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit

interface AutoHideSystemUi {
    val onSystemUiFlagHideNavigationDisposable: CompositeDisposable
}

fun <T> T.onCreateAutoHideSystemUi() where T : Activity, T : AutoHideSystemUi {
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE

    @Suppress("DEPRECATION")
    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        // immersive mode will not automatically hide the navigation bar after being revealed
        if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
            onResumeAutoHideSystemUi()
        }
    }
}

fun <T> T.onResumeAutoHideSystemUi() where T : Activity, T : AutoHideSystemUi {
    @Suppress("DEPRECATION")
    if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
        onSystemUiFlagHideNavigationDisposable.clear()

        Single.just(Unit)
            .delay(
                resources
                    .getInteger(R.integer.hide_system_navigation_timeout_seconds)
                    .toLong(),
                TimeUnit.SECONDS
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                },
                {}
            )
            // Do not remove: Kotlin has a bug where it casts Boolean result of previous statement to
            // Unit
            .let { onSystemUiFlagHideNavigationDisposable.add(it); Unit }
    }
}
