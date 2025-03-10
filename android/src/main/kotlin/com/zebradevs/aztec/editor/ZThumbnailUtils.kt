package com.zebradevs.aztec.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

object ZThumbnailUtils {

    /**
     * Generate a video thumbnail from a local file using MediaMetadataRetriever.
     * @param videoPath The absolute path of the video file.
     * @param timeMs The timestamp (in milliseconds) for the frame to capture.
     * @return A Bitmap thumbnail of the video or null if failed.
     */
    fun getVideoThumbnail(videoPath: String, timeMs: Long = 1000): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e("ThumbnailUtils", "Failed to retrieve video thumbnail: ${e.message}")
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Generate an image thumbnail with a fixed max width while maintaining the aspect ratio.
     * @param imagePath The absolute path of the image file.
     * @param maxWidth The maximum width for the thumbnail.
     * @return A Bitmap thumbnail of the image or null if failed.
     */


    fun getImageThumbnail(imagePath: String, maxWidth: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true // Only get dimensions, don't load the bitmap
            BitmapFactory.decodeFile(imagePath, this)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            Log.e(
                "ThumbnailUtils",
                "Invalid image dimensions: Width=$originalWidth, Height=$originalHeight"
            )
            return null
        }

        // Maintain aspect ratio
        val scaleFactor = originalWidth.toFloat() / maxWidth.toFloat()
        val newHeight = (originalHeight / scaleFactor).toInt()

        if (maxWidth <= 0 || newHeight <= 0) {
            Log.e("ThumbnailUtils", "Invalid target dimensions: Width=$maxWidth, Height=$newHeight")
            return null
        }

        // Use inSampleSize to reduce memory usage
        val inSampleSize = calculateInSampleSize(options, maxWidth, newHeight)

        options.apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Lower memory usage than ARGB_8888
            inMutable = true // Allow modification
        }

        return try {
            // Decode bitmap with scaling
            val decodedBitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null

            // Ensure scaling only happens when necessary
            val scaledBitmap = if (decodedBitmap.width > maxWidth) {
                Bitmap.createScaledBitmap(decodedBitmap, maxWidth, newHeight, true)
            } else {
                decodedBitmap
            }

            // Fix rotation before returning the final bitmap
            return fixImageRotation(imagePath, scaledBitmap)
        } catch (e: OutOfMemoryError) {
            Log.e("ThumbnailUtils", "Out of memory while creating thumbnail: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("ThumbnailUtils", "Failed to retrieve image thumbnail: ${e.message}")
            null
        }
    }

    /**
     * Calculate an appropriate sample size for efficient image loading.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun fixImageRotation(imagePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (rotationDegrees == 0) return bitmap // No rotation needed

            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: IOException) {
            Log.e("ThumbnailUtils", "Error reading EXIF data: ${e.message}")
            bitmap // Return original bitmap if there's an error
        }
    }
}