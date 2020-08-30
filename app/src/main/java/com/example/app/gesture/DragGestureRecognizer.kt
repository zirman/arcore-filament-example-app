package com.example.app.gesture

import android.view.MotionEvent

class DragGestureRecognizer(gesturePointersUtility: GesturePointersUtility) :
    BaseGestureRecognizer<DragGesture>(gesturePointersUtility) {
    interface OnGestureStartedListener : BaseGestureRecognizer.OnGestureStartedListener<DragGesture>

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
