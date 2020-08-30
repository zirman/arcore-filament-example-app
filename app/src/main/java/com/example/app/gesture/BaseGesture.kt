package com.example.app.gesture

import android.view.MotionEvent

// A gesture represents a sequence of touch events that are detected to represent a particular
// type of motion (i.e. Dragging, Pinching).
// Gestures are created and updated by BaseGestureRecognizer's.
abstract class BaseGesture<T : BaseGesture<T>>(val gesturePointersUtility: GesturePointersUtility) {
    // Interface definition for callbacks to be invoked by a [BaseGesture].
    interface OnGestureEventListener<T : BaseGesture<T>> {
        fun onUpdated(gesture: T)
        fun onFinished(gesture: T)
    }

    private var hasStarted = false
    private var justStarted = false
    private var hasFinished = false
    private var wasCancelled = false

    private var eventListener: OnGestureEventListener<T>? = null

    fun hasStarted(): Boolean {
        return hasStarted
    }

    fun justStarted(): Boolean {
        return justStarted
    }

    fun hasFinished(): Boolean {
        return hasFinished
    }

    fun wasCancelled(): Boolean {
        return wasCancelled
    }

    fun inchesToPixels(inches: Float): Float {
        return gesturePointersUtility.inchesToPixels(inches)
    }

    fun pixelsToInches(pixels: Float): Float {
        return gesturePointersUtility.pixelsToInches(pixels)
    }

    fun setGestureEventListener(listener: OnGestureEventListener<T>) {
        eventListener = listener
    }

    fun onTouch(motionEvent: MotionEvent) {
        if (!hasStarted && canStart(motionEvent)) {
            start(motionEvent)
            return
        }

        justStarted = false

        if (hasStarted) {
            if (updateGesture(motionEvent)) {
                dispatchUpdateEvent()
            }
        }
    }

    abstract fun canStart(motionEvent: MotionEvent): Boolean
    abstract fun onStart(motionEvent: MotionEvent)
    abstract fun updateGesture(motionEvent: MotionEvent): Boolean

    abstract fun onCancel()
    abstract fun onFinish()

    fun cancel() {
        wasCancelled = true
        onCancel()
        complete()
    }

    fun complete() {
        hasFinished = true

        if (hasStarted) {
            onFinish()
            dispatchFinishedEvent()
        }
    }

    private fun start(motionEvent: MotionEvent) {
        hasStarted = true
        justStarted = true
        onStart(motionEvent)
    }

    private fun dispatchUpdateEvent() {
        if (eventListener != null) {
            eventListener!!.onUpdated(self)
        }
    }

    private fun dispatchFinishedEvent() {
        if (eventListener != null) {
            eventListener!!.onFinished(self)
        }
    }

    // For compile-time safety so we don't need to cast when dispatching events.
    abstract val self: T
}
