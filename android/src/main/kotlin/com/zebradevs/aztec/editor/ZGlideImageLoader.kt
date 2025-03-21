package com.zebradevs.aztec.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import org.wordpress.aztec.Html

class ZGlideImageLoader(
    private val context: Context,
    private val headers: Map<String, String> = emptyMap()
) : Html.ImageGetter {

    override fun loadImage(source: String, callbacks: Html.ImageGetter.Callbacks, maxWidth: Int) {
        loadImage(source, callbacks, maxWidth, 0)
    }

    override fun loadImage(
        source: String,
        callbacks: Html.ImageGetter.Callbacks,
        maxWidth: Int,
        minWidth: Int
    ) {
        // Build LazyHeaders from the injected headers map
        val lazyHeaders = LazyHeaders.Builder().apply {
            headers.forEach { (key, value) ->
                addHeader(key, value)
            }
        }.build()

        // Create a GlideUrl with the LazyHeaders
        val glideUrl = GlideUrl(source, lazyHeaders)

        val deviceWidth = context.resources.displayMetrics.widthPixels
        getBitmapFromVectorDrawable(
            context,
            R.drawable.image_placeholder,
            deviceWidth
        )?.let {
            val placeholderDrawable = it.toDrawable(context.resources)
            Glide.with(context)
                .asBitmap()
                .load(glideUrl)
                .placeholder(placeholderDrawable)
                .error(placeholderDrawable)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .into(object : Target<Bitmap> {
                    override fun onLoadStarted(placeholder: Drawable?) {
                        callbacks.onImageLoaded(placeholderDrawable)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        callbacks.onImageLoaded(placeholderDrawable)
                    }

                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        if (resource.width < minWidth) {
                            return callbacks.onImageLoaded(
                                resource.upscaleTo(minWidth).toDrawable(context.resources)
                            )
                        }

                        // By default, BitmapFactory.decodeFile sets the bitmap's density to the device default so, we need
                        // to correctly set the input density to 160 ourselves.
                        resource.density = DisplayMetrics.DENSITY_DEFAULT
                        callbacks.onImageLoaded(resource.toDrawable(context.resources))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}

                    override fun getSize(cb: SizeReadyCallback) {
                        cb.onSizeReady(maxWidth, Target.SIZE_ORIGINAL)
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
    }
}