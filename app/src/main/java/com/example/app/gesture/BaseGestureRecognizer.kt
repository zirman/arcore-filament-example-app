package com.example.app.gesture

import android.view.MotionEvent
import java.util.*

/**
 * A Gesture recognizer processes touch input to determine if a gesture should start and fires an
 * event when the gesture is started.
 * To determine when an gesture is finished/updated, listen to the events on the gesture object.
 *
 * Base class for other gesture recognizers.
 * */
abstract class BaseGestureRecognizer<T : BaseGesture<T>>(val gesturePointersUtility: GesturePointersUtility) {

    interface OnGestureStartedListener<T : BaseGesture<T>> {
        fun onGestureStarted(gesture: T)
    }

    val gestures = ArrayList<T>()
    private val gestureStartedListeners: ArrayList<OnGestureStartedListener<T>> = ArrayList()

    /**
     * Add a gesture listener
     * */
    fun addOnGestureStartedListener(listener: OnGestureStartedListener<T>) {
        if (!gestureStartedListeners.contains(listener)) {
            gestureStartedListeners.add(listener)
        }
    }

    /**
     * Explicitly remove a gesture listener
     */
    fun removeOnGestureStartedListener(listener: OnGestureStartedListener<T>) {
        gestureStartedListeners.remove(listener)
    }

    /**
     * Evaluate if a touch event qualifies to be a gesture and
     * notify listeners if it is a gesture
     * finally remove it from the gestures collection after it is finished
     */
    fun onTouch(motionEvent: MotionEvent) {
        // Instantiate gestures based on touch input.
        // Just because a gesture was created, doesn't mean that it is started.
        // For example, a DragGesture is created when the user touch's down,
        // but doesn't actually start until the touch has moved beyond a threshold.
        tryCreateGestures(motionEvent)

        // Propagate event to gestures and determine if they should start.
        for (gesture in gestures) {
            gesture.onTouch(motionEvent)

            if (gesture.justStarted()) {
                dispatchGestureStarted(gesture)
            }
        }

        removeFinishedGestures()
    }

    /**
     * Create gesture if a touch event qualifies to be a gesture and add to [gestures] else ignore
     * */
    abstract fun tryCreateGestures(motionEvent: MotionEvent)

    /**
     * Callback to listeners notifying the gesture has started
     */
    private fun dispatchGestureStarted(gesture: T) {
        for (listener in gestureStartedListeners) {
            listener.onGestureStarted(gesture)
        }
    }

    /**
     * Remove gesture from the gestures collection after it has finished
     * */
    private fun removeFinishedGestures() {
        gestures.removeIf { it.hasFinished() }
    }
}
