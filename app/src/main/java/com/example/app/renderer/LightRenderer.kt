package com.example.app.renderer

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import com.example.app.*
import com.example.app.filament.Filament
import com.google.android.filament.*
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import java.nio.ByteBuffer
import kotlin.math.log2
import kotlin.math.max

/**
 * Update Lighting of the Scene
 *
 * @param context Activity Context
 * @param filament Filament Instance
 */
class LightRenderer(context: Context, private val filament: Filament) {
    private val reflections: Texture = loadReflections(context.assets, filament.engine)
    private var irradiance: FloatArray = FloatArray(27)

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


    /**
     * Update lighting for the frame
     */
    fun doFrame(frame: Frame) {
        // update lighting estimate for frame

        // Return if the light estimate is not valid this frame
        if (frame.lightEstimate.state != LightEstimate.State.VALID) {
            return
        }

        // Get ambient lighting as spherical harmonics coefficients.
        val irradianceUpdate = frame.lightEstimate.environmentalHdrAmbientSphericalHarmonics
            .let { getEnvironmentalHdrSphericalHarmonics(it) }

        // if irradianceUpdate not equal to previous irradiance values: update
        // ie: if scene indirect lighting has changed
        if (irradiance.asSequence().zip(irradianceUpdate.asSequence()).any { (x, y) -> x != y }) {
            irradiance = irradianceUpdate

            filament.scene.indirectLight = IndirectLight
                .Builder()
                .reflections(reflections)
                .irradiance(3, irradiance)
                .build(filament.engine)
        }

        // update main directional light's direction
        with(frame.lightEstimate.environmentalHdrMainLightDirection) {
            filament.engine.lightManager.setDirection(
                directionalLightInstance,
                -get(0),
                -get(1),
                -get(2),
            )
        }

        // update the main directional light's intensity
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
                color.floatArray[2],
            )
        }
    }
}

/**
 * Read the width and height of asset
 *
 * @param assets Asset Manager
 * @param name Path to Asset
 *
 * @return width, height
 */
private fun peekSize(assets: AssetManager, name: String): Pair<Int, Int> {
    assets.open(name).use { input ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        return opts.outWidth to opts.outHeight
    }
}

private const val reflectionsName = "reflections"

/**
 * Load texture from assets to direct byte buffer and convert as Filament texture
 *
 * @param texture Filament Texture manager object
 * @param assets Asset Manager
 * @param engine Filament Engine
 * @param prefix Texture Name prefix
 * @param level Texture level
 *
 * @return Bool: true if success, false on exception
 */
private fun loadCubemap(
    texture: Texture,
    assets: AssetManager,
    engine: Engine,
    prefix: String = "",
    level: Int = 0,
): Boolean {
    // This is important, the alpha channel does not encode opacity but some
    // of the bits of an R11G11B10F image to represent HDR data. We must tell
    // Android to not pre-multiply the RGB channels by the alpha channel
    val opts = BitmapFactory.Options().apply { inPremultiplied = false }

    // R11G11B10F is always 4 bytes per pixel
    val faceSize = texture.getWidth(level) * texture.getHeight(level) * 4
    val offsets = IntArray(6) { it * faceSize }
    // Allocate enough memory for all the cubemap faces
    val storage = ByteBuffer.allocateDirect(faceSize * 6)

    arrayOf("px", "nx", "py", "ny", "pz", "nz").forEach { suffix ->
        try {
            assets.open("$reflectionsName/$prefix$suffix.rgb32f").use {
                val bitmap = BitmapFactory.decodeStream(it, null, opts)
                bitmap?.copyPixelsToBuffer(storage)
            }
        } catch (e: Exception) {
            return false
        }
    }

    // Rewind the texture buffer
    storage.flip()

    val buffer = Texture.PixelBufferDescriptor(
        storage,
        Texture.Format.RGB, Texture.Type.UINT_10F_11F_11F_REV
    )

    texture.setImage(engine, level, buffer, offsets)
    return true
}

/**
 *  Load Reflections texture
 *
 *  @param assets Asset manager
 *  @param engine Filament Engine
 *
 *  @return Reflections Texture
 */
private fun loadReflections(assets: AssetManager, engine: Engine): Texture {
    val (w, h) = peekSize(assets, "$reflectionsName/m0_nx.rgb32f")
    val texture = Texture.Builder()
        .width(w)
        .height(h)
        .levels(log2(w.toFloat()).toInt() + 1)
        .format(Texture.InternalFormat.R11F_G11F_B10F)
        .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
        .build(engine)

    for (i in 0 until texture.levels) {
        if (!loadCubemap(texture, assets, engine, "m${i}_", i)) break
    }

    return texture
}
