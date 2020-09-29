package com.example.app.arcore

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.opengl.EGLContext
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.example.app.*
import com.example.app.filament.Filament
import com.google.android.filament.*
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.ar.core.*
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class ArCore(private val activity: Activity, private val view: View) {
    companion object {
        const val near = 0.1f
        const val far = 30f
        private const val positionBufferIndex: Int = 0
        private const val uvBufferIndex: Int = 1
    }

    val eglContext: EGLContext = createEglContext().orNull()!!
    private val cameraStreamTextureId: Int = createExternalTextureId()

    @Entity
    var renderable: Int = 0

    val session: Session = Session(activity)
        .also { session ->
            session.config
                .apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    focusMode = Config.FocusMode.AUTO

                    depthMode =
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }

                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    // getting ar frame doesn't block and gives last frame
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                .let(session::configure)

            session.setCameraTextureName(cameraStreamTextureId)
        }

    private val cameraId: String = session.cameraConfig.cameraId

    private val cameraManager: CameraManager =
        ContextCompat.getSystemService(activity, CameraManager::class.java)!!

    var timestamp: Long = 0L

    lateinit var frame: Frame

    private lateinit var cameraDevice: CameraDevice

    private lateinit var depthTexture: Texture

    fun destroy() {
        session.close()
        cameraDevice.close()
        destroyEglContext(eglContext)
    }

    fun configurationChange() {
        if (this::frame.isInitialized.not()) return

        val intrinsics = frame.camera.textureIntrinsics
        val dimensions = intrinsics.imageDimensions

        val displayWidth: Int
        val displayHeight: Int
        val displayRotation: Int

        DisplayMetrics()
            .also { displayMetrics ->
                @Suppress("DEPRECATION")
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
                else activity.windowManager.defaultDisplay)!!
                    .also { display ->
                        display.getRealMetrics(displayMetrics)
                        displayRotation = display.rotation
                    }

                displayWidth = displayMetrics.widthPixels
                displayHeight = displayMetrics.heightPixels
            }

        // camera width and height relative to display
        val cameraWidth: Int
        val cameraHeight: Int

        when (cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION)!!) {
            0, 180 -> when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    cameraWidth = dimensions[0]
                    cameraHeight = dimensions[1]
                }
                else -> {
                    cameraWidth = dimensions[1]
                    cameraHeight = dimensions[0]
                }
            }
            else -> when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    cameraWidth = dimensions[1]
                    cameraHeight = dimensions[0]
                }
                else -> {
                    cameraWidth = dimensions[0]
                    cameraHeight = dimensions[1]
                }
            }
        }

        val cameraRatio: Float = cameraWidth.toFloat() / cameraHeight.toFloat()
        val displayRatio: Float = displayWidth.toFloat() / displayHeight.toFloat()

        val viewWidth: Int
        val viewHeight: Int

        if (displayRatio < cameraRatio) {
            // width constrained
            viewWidth = displayWidth
            viewHeight = (displayWidth.toFloat() / cameraRatio).roundToInt()
        } else {
            // height constrained
            viewWidth = (displayHeight.toFloat() * cameraRatio).roundToInt()
            viewHeight = displayHeight
        }

        view.updateLayoutParams<FrameLayout.LayoutParams> {
            width = viewWidth
            height = viewHeight
        }

        session.setDisplayGeometry(displayRotation, viewWidth, viewHeight)
    }

    var hasDepthImage: Boolean = false

    fun update(frame: Frame, filament: Filament) {
        val firstFrame = this::frame.isInitialized.not()
        this.frame = frame

        if (firstFrame) {
            configurationChange()
        }

        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) && hasDepthImage.not()) {
            try {
                val depthImage = frame.acquireDepthImage()
                hasDepthImage = true

                if (this::depthTexture.isInitialized.not()) {
                    initTextures(filament, depthImage)
                }

                depthTexture.setImage(
                    filament.engine,
                    0,
                    Texture.PixelBufferDescriptor(
                        depthImage.planes[0].buffer,
                        Texture.Format.RG,
                        Texture.Type.UBYTE,
                        1,
                        0,
                        0,
                        0,
                        @Suppress("DEPRECATION")
                        Handler()
                    ) {
                        depthImage.close()
                        hasDepthImage = false
                    }
                )
            } catch (error: Throwable) {
            }
        }

        // update camera projection
        //filament.setProjectionMatrix(frame.projectionMatrix())
        filament.camera.setCustomProjection(
            frame.projectionMatrix().floatArray.toDoubleArray(),
            near.toDouble(),
            far.toDouble()
        )

        val cameraTransform = frame.camera.displayOrientedPose.matrix()
        filament.camera.setModelMatrix(cameraTransform.floatArray)
        val instance = filament.engine.transformManager.create(renderable)
        filament.engine.transformManager.setTransform(instance, cameraTransform.floatArray)
    }

    private fun initTextures(filament: Filament, depthImage: Image) {
        val tesWidth = depthImage.width
        val tesHeight = depthImage.height
        val clipPosition: V2A =
            FloatArray(((tesWidth * tesHeight) + tesWidth + tesHeight + 1) * dimenV2A)
                .let { V2A(it) }

        val uvs: V2A =
            FloatArray(((tesWidth * tesHeight) + tesWidth + tesHeight + 1) * dimenV2A)
                .let { V2A(it) }

        for (k in 0..tesHeight) {
            val v = k.toFloat() / tesHeight.toFloat()
            val y = (k.toFloat() / tesHeight.toFloat()) * 2f - 1f

            for (i in 0..tesWidth) {
                val u = i.toFloat() / tesWidth.toFloat()
                val x = (i.toFloat() / tesWidth.toFloat()) * 2f - 1f
                clipPosition.set(k * (tesWidth + 1) + i, x, y)
                uvs.set(k * (tesWidth + 1) + i, u, v)
            }
        }

        val triangleIndices = ShortArray(tesWidth * tesHeight * 6)
        for (k in 0 until tesHeight) {
            for (i in 0 until tesWidth) {
                triangleIndices[((k * tesWidth + i) * 6) + 0] =
                    ((k * (tesWidth + 1)) + i + 0).toShort()
                triangleIndices[((k * tesWidth + i) * 6) + 1] =
                    ((k * (tesWidth + 1)) + i + 1).toShort()
                triangleIndices[((k * tesWidth + i) * 6) + 2] =
                    ((k + 1) * (tesWidth + 1) + i).toShort()

                triangleIndices[((k * tesWidth + i) * 6) + 3] =
                    ((k + 1) * (tesWidth + 1) + i).toShort()
                triangleIndices[((k * tesWidth + i) * 6) + 4] =
                    ((k * (tesWidth + 1)) + i + 1).toShort()
                triangleIndices[((k * tesWidth + i) * 6) + 5] =
                    ((k + 1) * (tesWidth + 1) + i + 1).toShort()
            }
        }

//        Log.e("FOOBAR", "${clipPosition.count()} ${clipPosition.floatArray.joinToString(", ")}")
//        Log.e("FOOBAR", "${uvs.count()} ${uvs.floatArray.joinToString(", ")}")
//        Log.e("FOOBAR", "${triangleIndices.size} ${triangleIndices.joinToString(", ")}")

        val camera = frame.camera
        val intrinsics = camera.textureIntrinsics
        val dimensions = intrinsics.imageDimensions
        val width = dimensions[0]
        val height = dimensions[1]

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .geometry(
                0,
                PrimitiveType.TRIANGLES,
                VertexBuffer
                    .Builder()
                    .vertexCount(clipPosition.count())
                    .bufferCount(2)
                    .attribute(
                        VertexAttribute.POSITION,
                        positionBufferIndex,
                        AttributeType.FLOAT2,
                        0,
                        0
                    )
                    .attribute(
                        VertexAttribute.UV0,
                        uvBufferIndex,
                        AttributeType.FLOAT2,
                        0,
                        0
                    )
                    .build(filament.engine)
                    .also { vertexBuffer ->
                        vertexBuffer.setBufferAt(
                            filament.engine,
                            positionBufferIndex,
                            clipPosition.floatArray.toFloatBuffer()
                        )

                        vertexBuffer.setBufferAt(
                            filament.engine,
                            uvBufferIndex,
                            uvs.floatArray.toFloatBuffer()
                        )
                    },
                IndexBuffer
                    .Builder()
                    .indexCount(triangleIndices.size)
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .build(filament.engine)
                    .apply { setBuffer(filament.engine, triangleIndices.toShortBuffer()) }
            )
            .material(
                0,
                activity
                    .readUncompressedAsset("materials/camera.filamat")
                    .let { byteBuffer ->
                        Material
                            .Builder()
                            .payload(byteBuffer, byteBuffer.remaining())
                    }
                    .build(filament.engine)
                    .createInstance()
                    .also { materialInstance ->
                        materialInstance.setParameter(
                            "cameraTexture",
                            Texture
                                .Builder()
                                .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                                .format(Texture.InternalFormat.RGB8)
                                .build(filament.engine)
                                .apply {
                                    setExternalStream(
                                        filament.engine,
                                        Stream
                                            .Builder()
                                            .stream(cameraStreamTextureId.toLong())
                                            .width(width)
                                            .height(height)
                                            .build(filament.engine)
                                    )
                                },
                            TextureSampler(
                                TextureSampler.MinFilter.LINEAR,
                                TextureSampler.MagFilter.LINEAR,
                                TextureSampler.WrapMode.CLAMP_TO_EDGE
                            )
                            //.also { it.anisotropy = 8.0f }
                        )

                        materialInstance.setParameter(
                            "depthTexture",
                            Texture
                                .Builder()
                                .width(depthImage.width)
                                .height(depthImage.height)
                                .sampler(Texture.Sampler.SAMPLER_2D)
                                .format(Texture.InternalFormat.RG8)
                                .levels(1)
                                .build(filament.engine)
                                .also { depthTexture = it },
                            TextureSampler()//.also { it.anisotropy = 8.0f }
                        )
                    }
            )
            .build(
                filament.engine,
                EntityManager.get().create().also { entity ->
                    filament.scene.addEntity(entity)
                    renderable = entity
                }
            )
    }
}
