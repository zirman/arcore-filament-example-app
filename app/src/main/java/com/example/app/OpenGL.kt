package com.example.app

import android.opengl.*
import arrow.core.Either
import arrow.core.left
import arrow.core.right

fun createEglContext(): Either<Exception, EGLContext> {
    val eglOpenGlEs3Bit = 0x40
    val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    EGL14.eglInitialize(display, null, 0, null, 0)
    val configs: Array<EGLConfig?> = arrayOfNulls(1)

    EGL14.eglChooseConfig(
        display,
        intArrayOf(EGL14.EGL_RENDERABLE_TYPE, eglOpenGlEs3Bit, EGL14.EGL_NONE),
        0,
        configs,
        0,
        1,
        intArrayOf(0),
        0,
    )

    val context: EGLContext =
        EGL14.eglCreateContext(
            display,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0,
        )

    val surface: EGLSurface =
        EGL14.eglCreatePbufferSurface(
            display,
            configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0,
        )

    return if (EGL14.eglMakeCurrent(display, surface, surface, context)) {
        context.right()
    } else {
        Exception("Error creating EGL Context").left()
    }
}

fun createExternalTextureId(): Int = IntArray(1)
    .apply { GLES30.glGenTextures(1, this, 0) }
    .first()
    .apply { GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this) }

fun destroyEglContext(context: EGLContext): Either<Exception, Unit> =
    if (EGL14.eglDestroyContext(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY), context)
    ) {
        Unit.right()
    } else {
        Exception("Error destroying EGL context").left()
    }
