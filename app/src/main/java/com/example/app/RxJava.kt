package com.example.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import io.reactivex.Single

class UserCanceled : Exception()
class OpenGLVersionNotSupported : Exception()

const val cameraPermissionRequestCode = 1001
val minOpenGlVersion = Version(3, 0, 0, null, null)

fun Context.checkIfOpenGlVersionSupported(minOpenGlVersion: Version): Boolean =
    versionComparator.compare(
        minOpenGlVersion,
        ContextCompat
            .getSystemService(this, ActivityManager::class.java)!!
            .deviceConfigurationInfo
            .glEsVersion
            .let { parserVersion.parse(it) }
    ) <= 0

fun showOpenGlNotSupportedDialog(activity: Activity): Single<Unit> = Single
    .create { singleEmitter ->
        val alertDialog = AlertDialog
            .Builder(activity)
            .setTitle(R.string.opengl_required_title)
            .setMessage(
                activity.getString(
                    R.string.opengl_required_message,
                    minOpenGlVersion.print()
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> singleEmitter.onSuccess(Unit) }
            .setCancelable(false)
            .show()

        singleEmitter.setCancellable { alertDialog.dismiss() }
    }
