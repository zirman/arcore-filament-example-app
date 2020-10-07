package com.example.app.renderer

import android.content.Context
import com.example.app.*
import com.example.app.R
import com.example.app.filament.Filament
import com.google.android.filament.*
import com.google.android.filament.textured.TextureType
import com.google.android.filament.textured.loadTexture
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min

class PlaneRenderer(context: Context, private val filament: Filament) {
    companion object {
        private const val planeVertexBufferSize: Int = 1000
        private const val planeIndexBufferSize: Int = (planeVertexBufferSize - 2) * 3
    }

    private val textureMaterial: Material = context
        .readUncompressedAsset("materials/textured.filamat")
        .let { byteBuffer ->
            Material
                .Builder()
                .payload(byteBuffer, byteBuffer.remaining())
                .build(filament.engine)
        }

    private val textureMaterialInstance: MaterialInstance = textureMaterial
        .createInstance()
        .also { materialInstance ->
            materialInstance.setParameter(
                "texture",
                loadTexture(filament.engine, context.resources, R.drawable.sceneform_plane, TextureType.COLOR),
                TextureSampler().also { it.anisotropy = 8.0f }
            )
        }

    private val shadowMaterial: Material = context
        .readUncompressedAsset("materials/shadow.filamat")
        .let { byteBuffer ->
            Material
                .Builder()
                .payload(byteBuffer, byteBuffer.remaining())
                .build(filament.engine)
        }

    private val planeVertexFloatBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(planeVertexBufferSize * dimenV4A * floatSize)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val planeUvFloatBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(planeVertexBufferSize * dimenV2A * floatSize)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val planeIndexShortBuffer: ShortBuffer = ShortBuffer
        .allocate(planeIndexBufferSize)

    private val planeVertexBuffer: VertexBuffer = VertexBuffer
        .Builder()
        .vertexCount(planeVertexBufferSize)
        .bufferCount(2)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION,
            0,
            VertexBuffer.AttributeType.FLOAT4,
            0,
            0
        )
        .attribute(
            VertexBuffer.VertexAttribute.UV0,
            1,
            VertexBuffer.AttributeType.FLOAT2,
            0,
            0
        )
        .build(filament.engine)

    private val planeIndexBuffer: IndexBuffer = IndexBuffer
        .Builder()
        .indexCount(planeIndexBufferSize)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(filament.engine)

    @Entity
    private val planeRenderable: Int =
        EntityManager.get().create().also { filament.scene.addEntity(it) }

    fun doFrame(frame: Frame) {
        // update plane trackables
        val planeTrackables = frame
            .getUpdatedTrackables(Plane::class.java)
            .map { plane -> plane.subsumedBy ?: plane }
            .toSet()
            .filter { plane -> plane.trackingState == TrackingState.TRACKING }
            .also { if (it.isEmpty()) return }
            .sortedBy { it.type != Plane.Type.HORIZONTAL_UPWARD_FACING }

        val indexNotUpwardFacing = planeTrackables
            .indexOfFirst { it.type != Plane.Type.HORIZONTAL_UPWARD_FACING }

        var xMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        var zMin = Float.POSITIVE_INFINITY
        var zMax = Float.NEGATIVE_INFINITY

        var vertexBufferOffset: Int = 0
        var indexBufferOffset: Int = 0

        var indexWithoutShadow: Int? = null

        planeVertexFloatBuffer.rewind()
        planeUvFloatBuffer.rewind()
        planeIndexShortBuffer.rewind()

        for (i in 0 until planeTrackables.count()) {
            val plane = planeTrackables[i]

            // index of first triangle that doesn't have shadows applied
            if (i == indexNotUpwardFacing) {
                indexWithoutShadow = indexBufferOffset
            }

            // gets plane vertices in world space
            val planeVertices = plane.polygon.polygonToVertices(plane.centerPose.matrix())

            // triangle fan of indices over convex polygon
            val planeTriangleIndices =
                triangleIndexArrayCreate(
                    planeVertices.count() - 2,
                    { vertexBufferOffset.toShort() },
                    { k -> (vertexBufferOffset + k + 1).toShort() },
                    { k -> (vertexBufferOffset + k + 2).toShort() }
                )

            // check for for buffer overflow
            if (vertexBufferOffset + planeVertices.count() > planeVertexBufferSize ||
                indexBufferOffset + planeTriangleIndices.shortArray.count() > planeIndexBufferSize
            ) {
                break
            }

            for (k in planeVertices.floatArray.indices step 4) {
                xMin = min(planeVertices.floatArray[k + 0], xMin)
                xMax = max(planeVertices.floatArray[k + 0], xMax)
                yMin = min(planeVertices.floatArray[k + 1], yMin)
                yMax = max(planeVertices.floatArray[k + 1], yMax)
                zMin = min(planeVertices.floatArray[k + 2], zMin)
                zMax = max(planeVertices.floatArray[k + 2], zMax)
            }

            // push out data to nio buffers
            planeVertexFloatBuffer.put(planeVertices.floatArray)

            planeUvFloatBuffer.put(
                if (plane.type == Plane.Type.VERTICAL) {
                    // uv coordinates from model space
                    plane.polygon.polygonToUV()
                } else {
                    // uv coordinates from world space
                    planeVertices.horizontalToUV()
                }.floatArray
            )

            planeIndexShortBuffer.put(planeTriangleIndices.shortArray)

            vertexBufferOffset += planeVertices.count()
            indexBufferOffset += planeTriangleIndices.shortArray.count()
        }

        // push nio buffers to gpu
        var count = planeVertexFloatBuffer.capacity() - planeVertexFloatBuffer.remaining()
        planeVertexFloatBuffer.rewind()

        planeVertexBuffer.setBufferAt(
            filament.engine,
            0,
            planeVertexFloatBuffer,
            0,
            count
        )

        count = planeUvFloatBuffer.capacity() - planeUvFloatBuffer.remaining()
        planeUvFloatBuffer.rewind()

        planeVertexBuffer.setBufferAt(
            filament.engine,
            1,
            planeUvFloatBuffer,
            0,
            count
        )

        count = planeIndexShortBuffer.capacity() - planeIndexShortBuffer.remaining()
        planeIndexShortBuffer.rewind()

        planeIndexBuffer.setBuffer(
            filament.engine,
            planeIndexShortBuffer,
            0,
            count
        )

        // update renderable index buffer count
        RenderableManager
            .Builder(2)
            .castShadows(false)
            .receiveShadows(true)
            .culling(true)
            .boundingBox(
                Box(
                    (xMin + xMax) / 2f,
                    (yMin + yMax) / 2f,
                    (zMin + zMax) / 2f,
                    (xMax - xMin) / 2f,
                    (yMax - yMin) / 2f,
                    (zMax - zMin) / 2f
                )
            )
            .geometry( // texture is applied to all triangles
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                planeVertexBuffer,
                planeIndexBuffer,
                0,
                indexBufferOffset
            )
            .material(0, textureMaterialInstance)
            .geometry( // shadows are applied to upward facing triangles
                1,
                RenderableManager.PrimitiveType.TRIANGLES,
                planeVertexBuffer,
                planeIndexBuffer,
                0,
                indexWithoutShadow ?: indexBufferOffset
            )
            .material(1, shadowMaterial.defaultInstance)
            .build(filament.engine, planeRenderable)
    }
}
