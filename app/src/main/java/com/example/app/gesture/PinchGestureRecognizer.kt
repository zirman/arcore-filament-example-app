package com.example.app.gesture

import android.view.MotionEvent

class PinchGestureRecognizer(gesturePointersUtility: GesturePointersUtility) :
    BaseGestureRecognizer<PinchGesture>(gesturePointersUtility) {
    interface OnGestureStartedListener :
        BaseGestureRecognizer.OnGestureStartedListener<PinchGesture>

    override fun tryCreateGestures(motionEvent: MotionEvent) {
        // Pinch gestures require at least two fingers to be touching.
        if (motionEvent.pointerCount < 2) {
            return
        }

        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)
        val action = motionEvent.actionMasked

        val touchBegan = action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN

        if (!touchBegan || gesturePointersUtility.isPointerIdRetained(actionId)) {
            return
        }

        // Determine if there is another pointer Id that has not yet been retained.
        for (i in 0 until motionEvent.pointerCount) {
            val pointerId = motionEvent.getPointerId(i)

            if (pointerId == actionId) {
                continue
            }

            if (gesturePointersUtility.isPointerIdRetained(pointerId)) {
                continue
            }

            gestures.add(PinchGesture(gesturePointersUtility, motionEvent, pointerId))
        }
    }
}
