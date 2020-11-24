package com.example.app.gesture

import android.view.MotionEvent
import com.example.app.*
import kotlin.math.abs
import kotlin.math.cos

class PinchGesture(
    gesturePointersUtility: GesturePointersUtility,
    motionEvent: MotionEvent,
    private val pointerId2: Int,
) : BaseGesture<PinchGesture>(gesturePointersUtility) {
    interface OnGestureEventListener : BaseGesture.OnGestureEventListener<PinchGesture>

    companion object {
        private const val SLOP_INCHES = 0.05f
        private const val SLOP_MOTION_DIRECTION_DEGREES = 30.0f
    }

    private val pointerId1: Int = motionEvent.getPointerId(motionEvent.actionIndex)

    private var startPosition1: V3 =
        GesturePointersUtility.motionEventToPosition(motionEvent, pointerId1)

    private var startPosition2: V3 =
        GesturePointersUtility.motionEventToPosition(motionEvent, pointerId2)

    private var previousPosition1: V3 = startPosition1
    private var previousPosition2: V3 = startPosition2

    var gap = 0f
        private set

    var gapDelta = 0f
        private set

    fun gapInches(): Float {
        return gesturePointersUtility.pixelsToInches(gap)
    }

    fun gapDeltaInches(): Float {
        return gesturePointersUtility.pixelsToInches(gapDelta)
    }

    override fun canStart(motionEvent: MotionEvent): Boolean {
        if (gesturePointersUtility.isPointerIdRetained(pointerId1)
            || gesturePointersUtility.isPointerIdRetained(pointerId2)
        ) {
            cancel()
            return false
        }

        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)
        val action = motionEvent.actionMasked

        if (action == MotionEvent.ACTION_CANCEL) {
            cancel()
            return false
        }

        val touchEnded = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP

        if (touchEnded && (actionId == pointerId1 || actionId == pointerId2)) {
            cancel()
            return false
        }

        if (action != MotionEvent.ACTION_MOVE) {
            return false
        }

        val firstToSecond: V3 = startPosition1.sub(startPosition2)
        val firstToSecondDirection: V3 = firstToSecond.normalize()
        val newPosition1: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId1)
        val newPosition2: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId2)
        val deltaPosition1: V3 = newPosition1.sub(previousPosition1)
        val deltaPosition2: V3 = newPosition2.sub(previousPosition2)
        previousPosition1 = newPosition1
        previousPosition2 = newPosition2
        val dot1: Float = deltaPosition1.normalize().dot(firstToSecondDirection.neg())
        val dot2: Float = deltaPosition2.normalize().dot(firstToSecondDirection)
        val dotThreshold = cos(Math.toRadians(SLOP_MOTION_DIRECTION_DEGREES.toDouble())).toFloat()

        // Check angle of motion for the first touch.
        if (deltaPosition1.eq(v3Origin).not() && abs(dot1) < dotThreshold) {
            return false
        }

        // Check angle of motion for the second touch.
        if (deltaPosition2.eq(v3Origin).not() && abs(dot2) < dotThreshold) {
            return false
        }

        val startGap: Float = firstToSecond.magnitude()
        gap = newPosition1.sub(newPosition2).magnitude()
        val separation = abs(gap - startGap)
        val slopPixels = gesturePointersUtility.inchesToPixels(SLOP_INCHES)
        return separation >= slopPixels
    }

    override fun onStart(motionEvent: MotionEvent) {
        gesturePointersUtility.retainPointerId(pointerId1)
        gesturePointersUtility.retainPointerId(pointerId2)
    }

    override fun updateGesture(motionEvent: MotionEvent): Boolean {
        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)
        val action = motionEvent.actionMasked

        if (action == MotionEvent.ACTION_CANCEL) {
            cancel()
            return false
        }

        val touchEnded = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP

        if (touchEnded && (actionId == pointerId1 || actionId == pointerId2)) {
            complete()
            return false
        }

        if (action != MotionEvent.ACTION_MOVE) {
            return false
        }

        val newPosition1: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId1)
        val newPosition2: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId2)
        val newGap: Float = newPosition1.sub(newPosition2).magnitude()

        if (newGap == gap) {
            return false
        }

        gapDelta = newGap - gap
        gap = newGap
        return true
    }

    override fun onCancel() {
    }

    override fun onFinish() {
        gesturePointersUtility.releasePointerId(pointerId1)
        gesturePointersUtility.releasePointerId(pointerId2)
    }

    override val self: PinchGesture
        get() = this
}
