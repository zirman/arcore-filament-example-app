package com.example.app.gesture

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import com.example.app.V3
import com.example.app.v3
import java.util.*

// Retains/Releases pointer Ids so that each pointer can only be used in one gesture at a time.
// Provides helper functions for converting touch coordinates between pixels and inches.
class GesturePointersUtility(private val displayMetrics: DisplayMetrics) {
    companion object {
        fun motionEventToPosition(me: MotionEvent, pointerId: Int): V3 {
            val index = me.findPointerIndex(pointerId)
            return v3(me.getX(index), me.getY(index), 0f)
        }
    }

    private val retainedPointerIds: HashSet<Int> = HashSet()

    fun retainPointerId(pointerId: Int) {
        if (!isPointerIdRetained(pointerId)) {
            retainedPointerIds.add(pointerId)
        }
    }

    fun releasePointerId(pointerId: Int) {
        retainedPointerIds.remove(Integer.valueOf(pointerId))
    }

    fun isPointerIdRetained(pointerId: Int): Boolean {
        return retainedPointerIds.contains(pointerId)
    }

    fun inchesToPixels(inches: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, inches, displayMetrics)
    }

    fun pixelsToInches(pixels: Float): Float {
        val inchOfPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, 1f, displayMetrics)
        return pixels / inchOfPixels
    }
}
