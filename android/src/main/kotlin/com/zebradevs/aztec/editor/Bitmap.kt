package com.zebradevs.aztec.editor

import android.graphics.Bitmap

// Extension function for upscaling the bitmap if needed
fun Bitmap.upscaleTo(minWidth: Int): Bitmap {
    val scale = minWidth.toFloat() / this.width
    val newHeight = (this.height * scale).toInt()
    return Bitmap.createScaledBitmap(this, minWidth, newHeight, true)
}