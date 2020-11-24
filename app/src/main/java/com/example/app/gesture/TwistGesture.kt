package com.example.app.gesture

import android.view.MotionEvent
import com.example.app.*
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sign

class TwistGesture(
    gesturePointersUtility: GesturePointersUtility,
    motionEvent: MotionEvent,
    private val pointerId2: Int,
) : BaseGesture<TwistGesture>(gesturePointersUtility) {
    interface OnGestureEventListener : BaseGesture.OnGestureEventListener<TwistGesture>

    companion object {
        private const val SLOP_ROTATION_DEGREES = 15.0f

        private fun calculateDeltaRotation(
            currentPosition1: V3,
            currentPosition2: V3,
            previousPosition1: V3,
            previousPosition2: V3
        ): Float {
            val currentDirection: V3 = currentPosition1.sub(currentPosition2).normalize()
            val previousDirection: V3 = previousPosition1.sub(previousPosition2).normalize()

            return (acos(currentDirection.dot(previousDirection)).toDegrees) *
                    sign(previousDirection.x * currentDirection.y - previousDirection.y * currentDirection.x)
        }
    }

    private val pointerId1: Int = motionEvent.getPointerId(motionEvent.actionIndex)

    private var startPosition1: V3 =
        GesturePointersUtility.motionEventToPosition(motionEvent, pointerId1)

    private var startPosition2: V3 =
        GesturePointersUtility.motionEventToPosition(motionEvent, pointerId2)

    private var previousPosition1: V3 = startPosition1
    private var previousPosition2: V3 = startPosition2

    var deltaRotationDegrees = 0f
        private set

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

        val newPosition1: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId1)
        val newPosition2: V3 = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId2)
        val deltaPosition1: V3 = newPosition1.sub(previousPosition1)
        val deltaPosition2: V3 = newPosition2.sub(previousPosition2)

        previousPosition1 = newPosition1
        previousPosition2 = newPosition2

        // Check that both fingers are moving.
        if (deltaPosition1.eq(v3Origin)
            || deltaPosition2.eq(v3Origin)
        ) {
            return false
        }

        val rotation =
            calculateDeltaRotation(newPosition1, newPosition2, startPosition1, startPosition2)

        return abs(rotation) >= SLOP_ROTATION_DEGREES
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

        deltaRotationDegrees =
            calculateDeltaRotation(newPosition1, newPosition2, previousPosition1, previousPosition2)

        if (deltaRotationDegrees.isNaN()) {
            deltaRotationDegrees = 0f
        }

        previousPosition1 = newPosition1
        previousPosition2 = newPosition2

        return true
    }

    override fun onCancel() {
    }

    override fun onFinish() {
        gesturePointersUtility.releasePointerId(pointerId1)
        gesturePointersUtility.releasePointerId(pointerId2)
    }

    override val self: TwistGesture
        get() = this
}
