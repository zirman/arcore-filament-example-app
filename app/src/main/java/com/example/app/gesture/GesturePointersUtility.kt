package com.example.app.gesture

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import com.example.app.V3
import com.example.app.v3
import java.util.*

/**
 * Retains/Releases pointer Ids so that each pointer can only be used in one gesture at a time.
 * Provides helper functions for converting touch coordinates between pixels and inches.
 *
 * @param displayMetrics general info about display, such as its size, density, and font scaling.
 * */
class GesturePointersUtility(private val displayMetrics: DisplayMetrics) {
    companion object {
        /**
         * Convert Gesture MotionEvent to Position in X, Y Plane : Vector3(X, Y, 0)
         * */
        fun motionEventToPosition(me: MotionEvent, pointerId: Int): V3 {
            val index = me.findPointerIndex(pointerId)
            return v3(me.getX(index), me.getY(index), 0f)
        }
    }

    /**
     * HashSet of PointerIds retained for a gesture
     * */
    private val retainedPointerIds: HashSet<Int> = HashSet()

    /**
     * Add pointerId to the HashSet of retainedPointerIds
     * */
    fun retainPointerId(pointerId: Int) {
        if (!isPointerIdRetained(pointerId)) {
            retainedPointerIds.add(pointerId)
        }
    }

    /**
     * Remove pointerId from the HashSet of retainedPointerIds
     * */
    fun releasePointerId(pointerId: Int) {
        retainedPointerIds.remove(Integer.valueOf(pointerId))
    }

    /**
     * Check if the pointer is used in another gesture
     *
     * check if pointer id is in the HashSet of retainedPointerIds
     * */
    fun isPointerIdRetained(pointerId: Int): Boolean {
        return retainedPointerIds.contains(pointerId)
    }

    /**
     * Convert inches to pixels
     *
     * @param inches inches measure
     * */
    fun inchesToPixels(inches: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, inches, displayMetrics)
    }

    /**
     * Convert pixels to inches
     *
     * @param pixels pixels measure
     * */
    fun pixelsToInches(pixels: Float): Float {
        val inchOfPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, 1f, displayMetrics)
        return pixels / inchOfPixels
    }
}
