package com.example.app.renderer

import android.view.Choreographer
import com.example.app.arcore.ArCore
import com.google.ar.core.Frame
import java.util.concurrent.TimeUnit

class FrameCallback(
    private val arCore: ArCore,
    private val doFrame: (frame: Frame) -> Unit
) : Choreographer.FrameCallback {
    companion object {
        private const val maxFramesPerSecond: Long = 60
    }

    sealed class FrameRate(val factor: Long) {
        object Full : FrameRate(1)
        object Half : FrameRate(2)
        object Third : FrameRate(3)
    }

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastTick: Long = 0
    private var frameRate: FrameRate = FrameRate.Full

    override fun doFrame(frameTimeNanos: Long) {
        choreographer.postFrameCallback(this)

        // limit to max fps
        val nanoTime = System.nanoTime()
        val tick = nanoTime / (TimeUnit.SECONDS.toNanos(1) / maxFramesPerSecond)

        if (lastTick / frameRate.factor == tick / frameRate.factor) {
            return
        }

        lastTick = tick

        // render using frame from last tick to reduce possibility of jitter but increases latency
        if (// only render if we have an ar frame
            arCore.timestamp != 0L &&
            arCore.filament.uiHelper.isReadyToRender &&
            // This means you are sending frames too quickly to the GPU
            arCore.filament.renderer.beginFrame(arCore.filament.swapChain!!, frameTimeNanos)
        ) {
            arCore.filament.timestamp = arCore.timestamp
            arCore.filament.renderer.render(arCore.filament.view)
            arCore.filament.renderer.endFrame()
        }

        val frame = arCore.session.update()

        // During startup the camera system may not produce actual images immediately. In
        // this common case, a frame with timestamp = 0 will be returned.
        if (frame.timestamp != 0L &&
            frame.timestamp != arCore.timestamp
        ) {
            arCore.timestamp = frame.timestamp
            arCore.update(frame, arCore.filament)
            doFrame(frame)
        }
    }

    fun start() {
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
    }
}
