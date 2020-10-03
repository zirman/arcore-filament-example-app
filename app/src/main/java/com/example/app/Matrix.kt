package com.example.app

import android.app.Activity
import android.content.Context
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import com.example.app.arcore.ArCore
import com.example.app.filament.Filament
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.nio.channels.Channels
import kotlin.math.*

const val floatSize: Int = java.lang.Float.BYTES

inline val Float.Companion.degreesInTau: Float get() = 360f
inline val Float.Companion.tau: Float get() = PI.toFloat() * 2f
inline val Float.toDegrees: Float get() = this * (Float.degreesInTau / Float.tau)
inline val Float.toRadians: Float get() = this * (Float.tau / Float.degreesInTau)

inline val Float.clampToTau: Float
    get() =
        when {
            this < 0f ->
                this + ceil(-this / Float.tau) * Float.tau
            this >= Float.tau ->
                this - floor(this / Float.tau) * Float.tau
            else ->
                this
        }

inline class V2A(val floatArray: FloatArray)

inline class V3(val floatArray: FloatArray)
inline class V3A(val floatArray: FloatArray)

inline class V4A(val floatArray: FloatArray)
inline class M4(val floatArray: FloatArray)

inline class TriangleIndexArray(val shortArray: ShortArray)

inline fun triangleIndexArrayCreate(
    count: Int,
    i1: (Int) -> Short,
    i2: (Int) -> Short,
    i3: (Int) -> Short
): TriangleIndexArray {
    val triangleIndexArray = TriangleIndexArray(ShortArray(count * 3))

    for (i in 0 until count) {
        val k = i * 3
        triangleIndexArray.shortArray[k + 0] = i1(i)
        triangleIndexArray.shortArray[k + 1] = i2(i)
        triangleIndexArray.shortArray[k + 2] = i3(i)
    }

    return triangleIndexArray
}

fun m4Identity(): M4 = FloatArray(16)
    .also { Matrix.setIdentityM(it, 0) }
    .let { M4(it) }

fun M4.scale(x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.scaleM(it, 0, floatArray, 0, x, y, z) }
    .let { M4(it) }

fun M4.rotate(angle: Float, x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.rotateM(it, 0, floatArray, 0, angle, x, y, z) }
    .let { M4(it) }

fun M4.translate(x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.translateM(it, 0, floatArray, 0, x, y, z) }
    .let { M4(it) }

fun M4.multiply(m: M4): M4 = FloatArray(16)
    .also { Matrix.multiplyMM(it, 0, floatArray, 0, m.floatArray, 0) }
    .let { M4(it) }

fun M4.invert(): M4 = FloatArray(16)
    .also { Matrix.invertM(it, 0, floatArray, 0) }
    .let { M4(it) }

fun m4Rotate(angle: Float, x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.setRotateM(it, 0, angle, x, y, z) }
    .let { M4(it) }

fun FloatArray.toDoubleArray(): DoubleArray = DoubleArray(size)
    .also { doubleArray ->
        for (i in indices) {
            doubleArray[i] = this[i].toDouble()
        }
    }

fun Frame.projectionMatrix(): M4 = FloatArray(16)
    .apply { camera.getProjectionMatrix(this, 0, ArCore.near, ArCore.far) }
    .let { M4(it) }

@Suppress("DEPRECATION")
fun Activity.displayRotation(): Int =
    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
    else windowManager.defaultDisplay)!!.rotation

@Suppress("DEPRECATION")
fun Activity.displayRotationDegrees(): Int =
    when (displayRotation()) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> throw Exception("Invalid Display Rotation")
    }

fun Pose.matrix(): M4 = FloatArray(16)
    .also { toMatrix(it, 0) }
    .let { M4(it) }

inline fun v2aCreate(count: Int, x: (Int) -> Float, y: (Int) -> Float): V2A =
    V2A(FloatArray(count * dimenV2A))
        .also {
            for (i in it.indices) {
                it.set(i, x(i), y(i))
            }
        }

const val dimenV2A: Int = 2
inline val V2A.dimen: Int get() = dimenV2A
fun V2A.count(): Int = floatArray.size / dimen
inline val V2A.indices: IntRange get() = IntRange(0, count() - 1)

fun V2A.set(i: Int, x: Float, y: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
}

const val dimenV3A: Int = 3
inline val V3A.dimen: Int get() = dimenV3A

fun V3A.set(i: Int, x: Float, y: Float, z: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
    floatArray[(i * dimen) + 2] = z
}

fun mulV3(r: FloatArray, ri: Int, v: FloatArray, vi: Int, s: Float) {
    r[ri + 0] = v[vi + 0] * s
    r[ri + 1] = v[vi + 1] * s
    r[ri + 2] = v[vi + 2] * s
}

const val dimenV4A: Int = 4
inline val V4A.dimen: Int get() = dimenV4A
fun V4A.count(): Int = floatArray.size / dimen

fun V4A.getX(i: Int): Float = floatArray[(i * dimen) + 0]
fun V4A.getY(i: Int): Float = floatArray[(i * dimen) + 1]
fun V4A.getZ(i: Int): Float = floatArray[(i * dimen) + 2]
fun V4A.getW(i: Int): Float = floatArray[(i * dimen) + 3]

fun V4A.set(i: Int, x: Float, y: Float, z: Float, w: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
    floatArray[(i * dimen) + 2] = z
    floatArray[(i * dimen) + 3] = w
}

fun V3.dot(v: V3): Float =
    x * v.x + y * v.y + z * v.z

fun V3.neg(): V3 =
    v3(
        -x,
        -y,
        -z
    )

val v3Origin: V3 = v3(0f, 0f, 0f)

fun v3(x: Float, y: Float, z: Float): V3 = FloatArray(3)
    .let { V3(it) }
    .also {
        it.x = x
        it.y = y
        it.z = z
    }

inline var V3.x: Float
    get() = floatArray[0]
    set(x) {
        floatArray[0] = x
    }

inline var V3.y: Float
    get() = floatArray[1]
    set(y) {
        floatArray[1] = y
    }

inline var V3.z: Float
    get() = floatArray[2]
    set(z) {
        floatArray[2] = z
    }

fun V3.normalize(): V3 =
    scale(1f / magnitude())

fun V3.magnitude(): Float =
    sqrt(dot(this))

fun V3.scale(s: Float): V3 =
    v3(
        x * s,
        y * s,
        z * s
    )

fun V3.div(d: Float): V3 =
    v3(
        x / d,
        y / d,
        z / d
    )

fun FloatArray.toFloatBuffer(): FloatBuffer = ByteBuffer
    .allocateDirect(size * floatSize)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .also { floatBuffer ->
        floatBuffer.put(this)
        floatBuffer.rewind()
    }

fun Context.readUncompressedAsset(@Suppress("SameParameterValue") assetName: String): ByteBuffer {
    assets.openFd(assetName)
        .use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())

            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()

            return dst.apply { rewind() }
        }
}

fun ShortArray.toShortBuffer(): ShortBuffer = ShortBuffer
    .allocate(size)
    .also { shortBuffer ->
        shortBuffer.put(this)
        shortBuffer.rewind()
    }

// These coefficients came out Filament IndirectLight java doc for irradiance.
private val environmentalHdrToFilamentShCoefficients =
    floatArrayOf(
        0.282095f, -0.325735f, 0.325735f,
        -0.325735f, 0.273137f, -0.273137f,
        0.078848f, -0.273137f, 0.136569f
    )

fun getEnvironmentalHdrSphericalHarmonics(sphericalHarmonics: FloatArray): FloatArray =
    FloatArray(27)
        .also { irradianceData ->
            for (index in 0 until 27 step 3) {
                mulV3(
                    irradianceData,
                    index,
                    sphericalHarmonics,
                    index,
                    environmentalHdrToFilamentShCoefficients[index / 3]
                )
            }
        }

fun FloatBuffer.polygonToVertices(m: M4): V4A {
    val f = FloatArray((capacity() / 2) * 4)
    val v = FloatArray(4)
    v[1] = 0f
    v[3] = 1f
    rewind()

    for (i in f.indices step 4) {
        v[0] = get()
        v[2] = get()
        Matrix.multiplyMV(f, i, m.floatArray, 0, v, 0)
    }

    return V4A(f)
}

fun FloatBuffer.polygonToUV(): V2A {
    val f = V2A(FloatArray(capacity()))
    rewind()

    for (i in f.indices) {
        f.set(i, get() * 10f, get() * 5f)
    }

    return f
}

// uses world space to determine UV coordinates for better stability
fun V4A.horizontalToUV(): V2A = v2aCreate(count(), { i -> getX(i) * 10f }, { i -> getZ(i) * 5f })

fun V3.sub(v: V3): V3 =
    v3(
        x - v.x,
        y - v.y,
        z - v.z
    )

fun V3.eq(v: V3): Boolean = x == v.x &&
        y == v.y &&
        z == v.z
