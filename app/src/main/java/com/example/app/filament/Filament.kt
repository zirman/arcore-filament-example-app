package com.example.app.filament

import android.content.Context
import android.opengl.EGLContext
import android.view.Surface
import android.view.SurfaceView
import com.example.app.createEglContext
import com.example.app.destroyEglContext
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader

class Filament(context: Context, val surfaceView: SurfaceView) {
    var timestamp: Long = 0L
    private val eglContext: EGLContext = createEglContext().orNull()!!
    val engine: Engine = Engine.create(eglContext)
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()

    val camera: Camera = engine
        .createCamera()
        .also { camera ->
            // Set the exposure on the camera, this exposure follows the sunny f/16 rule
            // Since we've defined a light that has the same intensity as the sun, it
            // guarantees a proper exposure
            camera.setExposure(16f, 1f / 125f, 100f)
        }

    val view: View = engine
        .createView()
        .also { view ->
            view.camera = camera
            view.scene = scene
        }

    val assetLoader =
        AssetLoader(engine, MaterialProvider(engine), EntityManager.get())

    val resourceLoader =
        ResourceLoader(engine)

    var swapChain: SwapChain? = null
    val displayHelper = DisplayHelper(context)

    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    // Required to ensure we don't return before Filament is done executing the
                    // destroySwapChain command, otherwise Android might destroy the Surface
                    // too early
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
            }
        }

        attachTo(surfaceView)
    }

    fun destroy() {
        // Always detach the surface before destroying the engine
        uiHelper.detach()
        engine.destroy()
        destroyEglContext(eglContext)
    }
}
