package com.example.app.renderer

import com.example.app.arcore.ArCore
import com.example.app.filament.Filament
import com.example.app.matrix
import com.example.app.projectionMatrix
import com.google.ar.core.Frame

class CameraRenderer(private val filament: Filament, private val arCore: ArCore) {
    fun doFrame(frame: Frame) {
        // update camera
        filament.setProjectionMatrix(frame.projectionMatrix())

        filament.setCameraMatrix(
            arCore.arCameraStreamTransform,
            frame.camera.displayOrientedPose.matrix()
        )
    }
}
