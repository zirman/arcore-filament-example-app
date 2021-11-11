package com.example.app.gesture

import android.view.MotionEvent

/**
 * A gesture represents a sequence of touch events that are detected to represent
 * a particular type of motion (i.e. Dragging, Pinching).
 * Gestures are created and updated by BaseGestureRecognizer's.
 *
 * Base class for DragGesture, PinchGesture, TwistGesture..
 * */
abstract class BaseGesture<T : BaseGesture<T>>(val gesturePointersUtility: GesturePointersUtility) {

    /** Gesture listener interface. */
    interface OnGestureEventListener<T : BaseGesture<T>> {
        fun onUpdated(gesture: T)
        fun onFinished(gesture: T)
    }

    // gesture lifecycle
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

    /**
     * Start or update gesture status onTouch
     * */
    fun onTouch(motionEvent: MotionEvent) {
        // check if new gesture
        if (!hasStarted && canStart(motionEvent)) {
            start(motionEvent)
            return
        }

        // at this point, the gesture is an update to a previously started gesture
        // hence reset the just started value to false
        justStarted = false

        //
        if (hasStarted) {
            if (updateGesture(motionEvent)) {
                dispatchUpdateEvent()
            }
        }
    }

    /** Checks if to start a gesture event */
    abstract fun canStart(motionEvent: MotionEvent): Boolean
    /** Actions to perform on gesture start */
    abstract fun onStart(motionEvent: MotionEvent)
    /** Checks if to update the gesture event */
    abstract fun updateGesture(motionEvent: MotionEvent): Boolean
    /** Actions to perform on gesture event cancel */
    abstract fun onCancel()
    /** Actions to perform on gesture event finish */
    abstract fun onFinish()

    /**
     * Cancel the gesture for given touch event
     */
    fun cancel() {
        wasCancelled = true
        onCancel()
        complete()
    }

    /**
     * Gesture complete
     */
    fun complete() {
        hasFinished = true

        if (hasStarted) {
            onFinish()
            dispatchFinishedEvent()
        }
    }

    /**
     * Gesture start
     *
     * @param motionEvent touch event info
     */
    private fun start(motionEvent: MotionEvent) {
        hasStarted = true
        justStarted = true
        onStart(motionEvent)
    }

    /**
     * Gesture update callback
     */
    private fun dispatchUpdateEvent() {
        if (eventListener != null) {
            eventListener!!.onUpdated(self)
        }
    }

    /**
     * Gesture finished callback
     */
    private fun dispatchFinishedEvent() {
        if (eventListener != null) {
            eventListener!!.onFinished(self)
        }
    }

    // For compile-time safety so we don't need to cast when dispatching events.
    abstract val self: T
}
