package com.zebradevs.aztec.editor

import android.content.Context
import android.text.Html
import android.util.AttributeSet
import org.wordpress.aztec.AztecText

class ZetaAztecText : AztecText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    var maxLength: Int? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        if ((id == android.R.id.paste || id == android.R.id.pasteAsPlainText) && maxLength != null && maxLength!! > 0) {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)

                val currentTextLength = text.length
                val remainingLength = maxLength!! - currentTextLength
                if (remainingLength <= 0) {
                    return true
                }

                val pasteText = if (id == android.R.id.pasteAsPlainText) {
                    Html.fromHtml(
                        item.coerceToText(context).toString(),
                        Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                } else {
                    Html.fromHtml(
                        item.coerceToHtmlText(context),
                        Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                }

                val safeText = if (pasteText.length > remainingLength) {
                    pasteText.substring(0, remainingLength)
                } else {
                    pasteText
                }

                insertSafeText(safeText)
                return true
            }
        }

        return super.onTextContextMenuItem(id)
    }

    private fun insertSafeText(textToInsert: String) {
        val editable = editableText
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)

        editable.replace(
            start.coerceAtMost(editable.length),
            end.coerceAtMost(editable.length),
            textToInsert
        )
    }
}