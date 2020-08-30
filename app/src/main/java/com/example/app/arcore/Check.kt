package com.example.app.arcore

import android.app.Activity
import com.example.app.ResumeBehavior
import com.example.app.OpenGLVersionNotSupported
import com.example.app.checkIfOpenGlVersionSupported
import com.example.app.minOpenGlVersion
import com.example.app.showOpenGlNotSupportedDialog
import com.google.ar.core.ArCoreApk
import io.reactivex.Single

fun <T> T.checkArCore(): Single<Unit> where T : Activity, T : ResumeBehavior =
    Single
        .just(Unit)
        .flatMap {
            if (checkIfOpenGlVersionSupported(minOpenGlVersion)) {
                Single.just(Unit)
            } else {
                showOpenGlNotSupportedDialog(this)
                    .doOnSuccess { finish() }
                    .flatMap { throw OpenGLVersionNotSupported() }
            }
        }
        .flatMap { resumeBehavior.filter { it }.firstOrError() }
        .flatMap {
            // check if ARCore is installed
            when (ArCoreApk
                .getInstance()
                .requestInstall(
                    this,
                    true,
                    ArCoreApk.InstallBehavior.REQUIRED,
                    ArCoreApk.UserMessageType.USER_ALREADY_INFORMED
                )) {
                ArCoreApk.InstallStatus.INSTALLED -> Single
                    .just(Unit)
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> resumeBehavior
                    .filter { it }
                    .map { Unit }
                    .firstOrError()
                    .doOnSuccess {
                        // check if installation was successful
                        when (ArCoreApk
                            .getInstance()
                            .requestInstall(
                                this,
                                false,
                                ArCoreApk.InstallBehavior.REQUIRED,
                                ArCoreApk.UserMessageType.USER_ALREADY_INFORMED
                            )) {
                            ArCoreApk.InstallStatus.INSTALLED -> Single
                                .just(this)
                            else ->
                                throw Exception()
                        }
                    }
                else ->
                    throw Exception()
            }
        }
