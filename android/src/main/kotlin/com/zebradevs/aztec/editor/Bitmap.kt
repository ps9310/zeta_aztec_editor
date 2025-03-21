package com.zebradevs.aztec.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale


fun getBitmapFromVectorDrawable(
    context: Context,
    @DrawableRes drawableId: Int,
    targetWidth: Int? = null
): Bitmap? {
    val drawable = AppCompatResources.getDrawable(context, drawableId) ?: return null
    val intrinsicWidth = drawable.intrinsicWidth
    val intrinsicHeight = drawable.intrinsicHeight

    // If targetWidth is provided, calculate new dimensions to maintain aspect ratio.
    val width = targetWidth ?: intrinsicWidth
    val scale = width / intrinsicWidth.toFloat()
    val height = (intrinsicHeight * scale).toInt()

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}

// Extension function for upscaling the bitmap if needed
fun Bitmap.upscaleTo(minWidth: Int): Bitmap {
    val scale = minWidth.toFloat() / this.width
    val newHeight = (this.height * scale).toInt()
    return this.scale(minWidth, newHeight)
}

