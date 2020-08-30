package com.example.app.arcore

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import com.example.app.tau
import kotlin.math.cos
import kotlin.math.sin

class HandMotionAnimation(private val handImageView: View) : Animation() {
    companion object {
        private const val ANIMATION_SPEED_MS: Long = 2500L
    }

    private val containerView: View = handImageView.parent as View

    init {
        repeatCount = INFINITE
        duration = ANIMATION_SPEED_MS
        startOffset = 1000
    }

    override fun applyTransformation(interpolatedTime: Float, transformation: Transformation?) {
        val startAngle = Float.tau / 4f
        val progressAngle = Float.tau * interpolatedTime
        val currentAngle = startAngle + progressAngle
        val handWidth: Float = handImageView.width.toFloat()
        val radius: Float = handImageView.resources.displayMetrics.density * 25f
        var xPos = radius * 2f * cos(currentAngle.toDouble()).toFloat()
        var yPos = radius * sin(currentAngle.toDouble()).toFloat()
        xPos += containerView.width / 2f
        yPos += containerView.height / 2f
        xPos -= handWidth / 2f
        yPos -= handImageView.height / 2f
        handImageView.x = xPos
        handImageView.y = yPos
        handImageView.invalidate()
    }
}
