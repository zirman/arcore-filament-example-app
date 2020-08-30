package com.example.app.arcore

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class HandMotionView : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clearAnimation()
        startAnimation(HandMotionAnimation(this))
    }
}
