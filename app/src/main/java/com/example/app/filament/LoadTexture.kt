package com.example.app.filament

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.android.TextureHelper
import java.lang.IllegalArgumentException

enum class TextureType {
    Color,
    Normal,
    Data
}

fun Context.loadTexture(engine: Engine, @DrawableRes resourceId: Int, type: TextureType): Texture {
    val options = BitmapFactory.Options()
    // Color is the only type of texture we want to pre-multiply with the alpha channel
    // Pre-multiplication is the default behavior, so we need to turn it off here
    options.inPremultiplied = type == TextureType.Color

    val bitmap =
        BitmapFactory.decodeResource(resources, resourceId, options)

    val texture = Texture.Builder()
        .width(bitmap.width)
        .height(bitmap.height)
        .sampler(Texture.Sampler.SAMPLER_2D)
        .format(internalFormat(type))
        // This tells Filament to figure out the number of mip levels
        .levels(0xff)
        .build(engine)

    // TextureHelper offers a method that skips the copy of the bitmap into a ByteBuffer
    TextureHelper.setBitmap(engine, texture, 0, bitmap)
    texture.generateMipmaps(engine)

    return texture
}

private fun internalFormat(type: TextureType) = when (type) {
    TextureType.Color -> Texture.InternalFormat.SRGB8_A8
    TextureType.Normal -> Texture.InternalFormat.RGBA8
    TextureType.Data -> Texture.InternalFormat.RGBA8
}

// Not required when SKIP_BITMAP_COPY is true
// Use String representation for compatibility across API levels
private fun format(bitmap: Bitmap) = when (bitmap.config.name) {
    "ALPHA_8" -> Texture.Format.ALPHA
    "RGB_565" -> Texture.Format.RGB
    "ARGB_8888" -> Texture.Format.RGBA
    "RGBA_F16" -> Texture.Format.RGBA
    else -> throw IllegalArgumentException("Unknown bitmap configuration")
}

// Not required when SKIP_BITMAP_COPY is true
private fun type(bitmap: Bitmap) = when (bitmap.config.name) {
    "ALPHA_8" -> Texture.Type.USHORT
    "RGB_565" -> Texture.Type.USHORT_565
    "ARGB_8888" -> Texture.Type.UBYTE
    "RGBA_F16" -> Texture.Type.HALF
    else -> throw IllegalArgumentException("Unsupported bitmap configuration")
}
