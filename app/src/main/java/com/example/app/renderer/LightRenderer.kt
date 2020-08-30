package com.example.app.renderer

import com.example.app.filament.Filament
import com.example.app.V3
import com.example.app.div
import com.example.app.getEnvironmentalHdrSphericalHarmonics
import com.google.android.filament.EntityInstance
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import kotlin.math.max

class LightRenderer(private val filament: Filament) {
    @EntityInstance
    private var directionalLightInstance: Int = EntityManager
        .get()
        .create()
        .let { directionalLight ->
            filament.scene.addEntity(directionalLight)

            LightManager
                .Builder(LightManager.Type.DIRECTIONAL)
                .castShadows(true)
                .build(filament.engine, directionalLight)

            filament.engine.lightManager.getInstance(directionalLight)
        }

    fun doFrame(frame: Frame) {
        // update lighting estimate
        if (frame.lightEstimate.state != LightEstimate.State.VALID) {
            return
        }

        filament.scene.indirectLight = IndirectLight
            .Builder()
            .irradiance(
                3,
                frame.lightEstimate.environmentalHdrAmbientSphericalHarmonics
                    .let(::getEnvironmentalHdrSphericalHarmonics)
            )
            .build(filament.engine)

        with(frame.lightEstimate.environmentalHdrMainLightDirection) {
            filament.engine.lightManager.setDirection(
                directionalLightInstance,
                -get(0),
                -get(1),
                -get(2)
            )
        }

        with(frame.lightEstimate.environmentalHdrMainLightIntensity) {
            // Scale hdr rgb values to fit in range [0, 1).
            // There may be a better way to do this conversion.
            val rgbMax = max(max(get(0), get(1)), get(2))
            // prevent div by zero
            val color = V3(this).div(max(0.00001f, rgbMax))

            filament.engine.lightManager.setColor(
                directionalLightInstance,
                color.floatArray[0],
                color.floatArray[1],
                color.floatArray[2]
            )
        }
    }
}
