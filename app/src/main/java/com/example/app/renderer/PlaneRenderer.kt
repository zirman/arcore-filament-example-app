package com.example.app.renderer

import android.content.Context
import com.example.app.*
import com.example.app.R
import com.example.app.filament.Filament
import com.example.app.filament.TextureType
import com.example.app.filament.loadTexture
import com.google.android.filament.*
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

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

    private val planeMaterialInstance: MaterialInstance = textureMaterial
        .createInstance()
        .also { materialInstance ->
            materialInstance.setParameter(
                "texture",
                context.loadTexture(filament.engine, R.drawable.sceneform_plane, TextureType.Color),
                TextureSampler().also { it.anisotropy = 8.0f }
            )
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

        if (planeTrackables.isEmpty()) {
            return
        }

        var vertexBufferOffset: Int = 0
        var indexBufferOffset: Int = 0

        planeVertexFloatBuffer.rewind()
        planeUvFloatBuffer.rewind()
        planeIndexShortBuffer.rewind()

        for (plane in planeTrackables) {
            // gets plane vertices in world space
            val planeVertices = plane.polygon.polygonToVertices(plane.centerPose.matrix())

            // triangle fan of indices over convex polygon
            val planeTriangleIndices =
                triangleIndexArrayCreate(
                    planeVertices.count() - 2,
                    { vertexBufferOffset.toShort() },
                    { i -> (vertexBufferOffset + i + 1).toShort() },
                    { i -> (vertexBufferOffset + i + 2).toShort() }
                )

            // check for for buffer overflow
            if (vertexBufferOffset + planeVertices.count() > planeVertexBufferSize ||
                indexBufferOffset + planeTriangleIndices.shortArray.count() > planeIndexBufferSize
            ) {
                break
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
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .priority(1)
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                planeVertexBuffer,
                planeIndexBuffer,
                0,
                indexBufferOffset
            )
            .material(0, planeMaterialInstance)
            .build(filament.engine, planeRenderable)
    }
}
