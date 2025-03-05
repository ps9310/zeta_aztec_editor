package com.zebradevs.aztec.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import org.wordpress.aztec.Html

class GlideVideoThumbnailLoader(
    private val context: Context,
    private val headers: Map<String, String>? = null
) : Html.VideoThumbnailGetter {

    override fun loadVideoThumbnail(
        source: String,
        callbacks: Html.VideoThumbnailGetter.Callbacks,
        maxWidth: Int
    ) {
        loadVideoThumbnail(source, callbacks, maxWidth, 0)
    }

    override fun loadVideoThumbnail(
        source: String,
        callbacks: Html.VideoThumbnailGetter.Callbacks,
        maxWidth: Int,
        minWidth: Int
    ) {
        val correctedSource = extractVideoUrl(source)
        val glideModel: Any = if (headers.isNullOrEmpty()) {
            correctedSource
        } else {
            GlideUrl(
                correctedSource,
                LazyHeaders.Builder().apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }.build()
            )
        }

        Glide.with(context)
            .asBitmap()
            .load(glideModel)
            .fitCenter()
            .into(object : Target<Bitmap> {
                override fun onLoadStarted(placeholder: Drawable?) {
                    callbacks.onThumbnailLoading(placeholder)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    callbacks.onThumbnailFailed()
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    glideAnimation: Transition<in Bitmap>?
                ) {
                    if (resource.width < minWidth) {
                        return callbacks.onThumbnailLoaded(
                            BitmapDrawable(context.resources, resource.upscaleTo(minWidth))
                        )
                    }

                    resource.density = DisplayMetrics.DENSITY_DEFAULT
                    callbacks.onThumbnailLoaded(BitmapDrawable(context.resources, resource))
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun getSize(cb: SizeReadyCallback) {
                    cb.onSizeReady(maxWidth, maxWidth)
                }

                override fun removeCallback(cb: SizeReadyCallback) {}

                override fun setRequest(request: Request?) {}

                override fun getRequest(): Request? {
                    return null
                }

                override fun onStart() {}

                override fun onStop() {}

                override fun onDestroy() {}
            })
    }

    private fun extractVideoUrl(source: String): String {
        return videoRegex.find(source)?.groupValues?.get(1) ?: source
    }
}
