package com.example.app.aractivity

import android.view.View

data class ScreenPosition(val x: Float, val y: Float)

data class ViewRect(val left: Float, val top: Float, val width: Float, val height: Float)

sealed class TouchEvent(val x: Float, val y: Float) {
    class Move(x: Float, y: Float) : TouchEvent(x, y)
    class Stop(x: Float, y: Float) : TouchEvent(x, y)
}

fun View.toViewRect(): ViewRect =
    ViewRect(
        left.toFloat(),
        top.toFloat(),
        width.toFloat(),
        height.toFloat(),
    )
