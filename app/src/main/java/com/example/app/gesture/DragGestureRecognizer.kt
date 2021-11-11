package com.example.app.gesture

import android.view.MotionEvent

class DragGestureRecognizer(gesturePointersUtility: GesturePointersUtility) :
    BaseGestureRecognizer<DragGesture>(gesturePointersUtility) {

    interface OnGestureStartedListener : BaseGestureRecognizer.OnGestureStartedListener<DragGesture>

    // DragGesture is created when the user touch's down,
    // but doesn't actually start until the touch has moved beyond a threshold.
    override fun tryCreateGestures(motionEvent: MotionEvent) {
        val action = motionEvent.actionMasked
        val actionId = motionEvent.getPointerId(motionEvent.actionIndex)

        val touchBegan = action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN

        if (touchBegan && !gesturePointersUtility.isPointerIdRetained(actionId)) {
            gestures.add(DragGesture(gesturePointersUtility, motionEvent))
        }
    }
}
