package com.zebradevs.aztec.editor.toolbar

import com.zebradevs.aztec.editor.R
import org.wordpress.aztec.AztecTextFormat
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.toolbar.IToolbarAction
import org.wordpress.aztec.toolbar.ToolbarActionType

enum class MediaToolbarAction(
    override val buttonId: Int,
    override val buttonDrawableRes: Int,
    override val actionType: ToolbarActionType,
    override val textFormats: Set<ITextFormat> = setOf()
) : IToolbarAction {
    VIDEO(
        R.id.media_bar_button_gallery,
        R.drawable.media_option_video,
        ToolbarActionType.OTHER,
        setOf(AztecTextFormat.FORMAT_NONE)
    ),
    IMAGE(
        R.id.media_bar_button_camera,
        R.drawable.media_option_image,
        ToolbarActionType.OTHER,
        setOf(AztecTextFormat.FORMAT_NONE)
    )
}
