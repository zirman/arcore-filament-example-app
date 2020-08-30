package com.example.app.gesture

import android.view.MotionEvent
import com.example.app.*

class DragGesture(gesturePointersUtility: GesturePointersUtility, motionEvent: MotionEvent) :
    BaseGesture<DragGesture>(gesturePointersUtility) {
    interface OnGestureEventListener : BaseGesture.OnGestureEventListener<DragGesture>

    companion object {
        private const val SLOP_INCHES = 0.1f
    }

    private val pointerId: Int = motionEvent.getPointerId(motionEvent.actionIndex)

    private val startPosition: V3 =
        GesturePointersUtility.motionEventToPosition(motionEvent, pointerId)

    var position: V3 = startPosition
    private var delta: V3 = v3Origin

    override fun canStart(motionEvent: MotionEvent): Boolean {
        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)
        val action = motionEvent.actionMasked

        if (gesturePointersUtility.isPointerIdRetained(pointerId)) {
            cancel()
            return false
        }

        if (actionId == pointerId
            && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
        ) {
            cancel()
            return false
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancel()
            return false
        }

        if (action != MotionEvent.ACTION_MOVE) {
            return false
        }

        if (motionEvent.pointerCount > 1) {
            for (i in 0 until motionEvent.pointerCount) {
                val id = motionEvent.getPointerId(i)

                if (id != pointerId && !gesturePointersUtility.isPointerIdRetained(id)) {
                    return false
                }
            }
        }

        val newPosition: V3 =
            GesturePointersUtility.motionEventToPosition(motionEvent, pointerId)

        val diff: Float = newPosition.sub(startPosition).magnitude()
        val slopPixels = gesturePointersUtility.inchesToPixels(SLOP_INCHES)

        return diff >= slopPixels
    }

    override fun onStart(motionEvent: MotionEvent) {
        position = GesturePointersUtility.motionEventToPosition(motionEvent, pointerId)
        gesturePointersUtility.retainPointerId(pointerId)
    }

    override fun updateGesture(motionEvent: MotionEvent): Boolean {
        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)
        val action = motionEvent.actionMasked
        if (action == MotionEvent.ACTION_MOVE) {
            val newPosition: V3 =
                GesturePointersUtility.motionEventToPosition(motionEvent, pointerId)

            if (newPosition.eq(position).not()) {
                delta = newPosition.sub(position)
                position = newPosition
                return true
            }
        } else if (actionId == pointerId
            && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
        ) {
            complete()
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancel()
        }
        return false
    }

    override fun onCancel() {
    }

    override fun onFinish() {
        gesturePointersUtility.releasePointerId(pointerId)
    }

    override val self: DragGesture get() = this
}
