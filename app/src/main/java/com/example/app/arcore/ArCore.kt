package com.example.app.arcore

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.camera2.*
import android.opengl.EGLContext
import android.opengl.Matrix
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import arrow.core.orNull
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
        private val cameraScreenSpaceVertices: V4A =
            floatArrayOf(
                -1f, +1f, -1f, 1f,
                -1f, -3f, -1f, 1f,
                +3f, +1f, -1f, 1f
            )
                .let { V4A(it) }

        private val arCameraStreamTriangleIndices: ShortArray =
            shortArrayOf(0, 1, 2)

        private val cameraUvs: V2A =
            floatArrayOf(
                0f, 0f,
                2f, 0f,
                0f, 2f
            )
                .let { V2A(it) }

        private const val arCameraStreamPositionBufferIndex: Int = 0
        private const val arCameraStreamUvBufferIndex: Int = 1
    }

    val eglContext: EGLContext = createEglContext().orNull()!!
    private val arCameraStreamTextureId1: Int = createExternalTextureId()

    val session: Session = Session(activity)
        .also { session ->
            session.config
                .apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    focusMode = Config.FocusMode.AUTO
                    depthMode = Config.DepthMode.DISABLED
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    // getting ar frame doesn't block and gives last frame
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                .let(session::configure)

            session.setCameraTextureName(arCameraStreamTextureId1)
        }

    private val cameraId: String = session.cameraConfig.cameraId

    private val cameraManager: CameraManager =
        ContextCompat.getSystemService(activity, CameraManager::class.java)!!

    var timestamp: Long = 0L

    @Entity
    private var arCameraStreamRenderable: Int = 0

    @EntityInstance
    var arCameraStreamTransform: Int = 0

    lateinit var frame: Frame
    private lateinit var arCameraStreamMaterialInstance1: MaterialInstance
    private lateinit var cameraDevice: CameraDevice
    private lateinit var arCameraStreamVertexBuffer: VertexBuffer

    fun destroy() {
        session.close()
        cameraDevice.close()
        destroyEglContext(eglContext)
    }

    fun configurationChange(filament: Filament) {
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

        arCameraStreamVertexBuffer.setBufferAt(
            filament.engine,
            arCameraStreamUvBufferIndex,
            cameraUvs.floatArray.toFloatBuffer()
        )
    }

    private fun init(filament: Filament) {
        val camera = frame.camera
        val intrinsics = camera.textureIntrinsics
        val dimensions = intrinsics.imageDimensions
        val width = dimensions[0]
        val height = dimensions[1]

        arCameraStreamMaterialInstance1 = activity
            .readUncompressedAsset("materials/unlit.filamat")
            .let { byteBuffer ->
                Material
                    .Builder()
                    .payload(byteBuffer, byteBuffer.remaining())
            }
            .build(filament.engine)
            .createInstance()
            .apply {
                setParameter(
                    "videoTexture",
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
                                    .stream(arCameraStreamTextureId1.toLong())
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
                )
            }

        val cameraIndexBuffer: IndexBuffer = IndexBuffer
            .Builder()
            .indexCount(arCameraStreamTriangleIndices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(filament.engine)
            .apply { setBuffer(filament.engine, arCameraStreamTriangleIndices.toShortBuffer()) }

        arCameraStreamVertexBuffer = VertexBuffer
            .Builder()
            .vertexCount(arCameraStreamTriangleIndices.size)
            .bufferCount(2)
            .attribute(
                VertexAttribute.POSITION,
                arCameraStreamPositionBufferIndex,
                AttributeType.FLOAT4,
                0,
                0
            )
            .attribute(
                VertexAttribute.UV0,
                arCameraStreamUvBufferIndex,
                AttributeType.FLOAT2,
                0,
                0
            )
            .build(filament.engine)

        arCameraStreamVertexBuffer.setBufferAt(
            filament.engine,
            arCameraStreamUvBufferIndex,
            cameraUvs.floatArray.toFloatBuffer()
        )

        arCameraStreamRenderable = EntityManager.get().create()

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .priority(7) // Always draw the camera feed last to avoid overdraw
            .geometry(0, PrimitiveType.TRIANGLES, arCameraStreamVertexBuffer, cameraIndexBuffer)
            .material(0, arCameraStreamMaterialInstance1)
            .build(filament.engine, arCameraStreamRenderable)

        // add to the scene
        filament.scene.addEntity(arCameraStreamRenderable)

        arCameraStreamTransform = filament.engine.transformManager.create(arCameraStreamRenderable)
        configurationChange(filament)
    }

    fun update(frame: Frame, filament: Filament) {
        val firstFrame = this::frame.isInitialized.not()
        this.frame = frame

        if (firstFrame) {
            init(filament)
        }

        val projectionMatrixInv = m4Rotate(-activity.displayRotationDegrees().toFloat(), 0f, 0f, 1f)
            .multiply(frame.projectionMatrix()).invert()

        val cameraVertices = FloatArray(cameraScreenSpaceVertices.floatArray.size)

        for (i in cameraScreenSpaceVertices.floatArray.indices step 4) {
            Matrix.multiplyMV(
                cameraVertices,
                i,
                projectionMatrixInv.floatArray,
                0,
                cameraScreenSpaceVertices.floatArray,
                i
            )
        }

        for (i in cameraScreenSpaceVertices.floatArray.indices step 4) {
            cameraVertices[i + 0] *= cameraVertices[i + 3]
            cameraVertices[i + 1] *= cameraVertices[i + 3]
            cameraVertices[i + 2] *= cameraVertices[i + 3]
            cameraVertices[i + 3] *= 1f
        }

        val vertexBufferData = cameraVertices.toFloatBuffer()

        vertexBufferData.rewind()

        arCameraStreamVertexBuffer
            .setBufferAt(filament.engine, arCameraStreamPositionBufferIndex, vertexBufferData)

        arCameraStreamVertexBuffer
            .setBufferAt(filament.engine, arCameraStreamPositionBufferIndex, vertexBufferData)
    }
}
