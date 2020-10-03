package com.example.app.aractivity

import android.view.View
import com.example.app.arcore.ArCore
import com.example.app.renderer.*

data class ScreenPosition(val x: Float, val y: Float)

data class ViewRect(val left: Float, val top: Float, val width: Float, val height: Float)

sealed class TouchEvent(val x: Float, val y: Float) {
    class Move(x: Float, y: Float) : TouchEvent(x, y)
    class Stop(x: Float, y: Float) : TouchEvent(x, y)
}

data class ArContext(
    val arCore: ArCore,
    val lightRenderer: LightRenderer,
    val planeRenderer: PlaneRenderer,
    val modelRenderer: ModelRenderer,
    val frameCallback: FrameCallback
)

fun View.toViewRect(): ViewRect =
    ViewRect(
        left.toFloat(),
        top.toFloat(),
        width.toFloat(),
        height.toFloat()
    )
