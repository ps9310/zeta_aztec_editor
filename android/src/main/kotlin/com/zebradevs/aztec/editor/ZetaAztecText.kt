package com.zebradevs.aztec.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.text.Spanned
import android.util.AttributeSet
import org.wordpress.aztec.AztecText

class ZetaAztecText : AztecText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Nullable property for maximum paste length that can be set at runtime.
    // If null or <= 0, no truncation will occur.
    var maxLength: Int? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val firstItem = clip.getItemAt(0)
                // Only perform check if maxPasteLength is not null and > 0.
                val maxLength = maxLength
                if (maxLength != null && maxLength > 0) {
                    if (id == android.R.id.pasteAsPlainText) {
                        // For plain text paste, truncate without styling.
                        val clipboardContent = firstItem.coerceToText(context).toString()
                        val plainText = Html.fromHtml(clipboardContent, Html.FROM_HTML_MODE_LEGACY).toString()
                        if (plainText.length > maxLength) {
                            val truncatedPlainText = plainText.substring(0, maxLength)
                            val newClip = ClipData.newPlainText("truncated", truncatedPlainText)
                            val originalClip = clip
                            clipboard.setPrimaryClip(newClip)
                            val result = super.onTextContextMenuItem(id)
                            clipboard.setPrimaryClip(originalClip)
                            return result
                        }
                    } else {
                        // For rich text paste, preserve styling.
                        val clipboardContent = firstItem.coerceToHtmlText(context)
                        val spanned = Html.fromHtml(clipboardContent, Html.FROM_HTML_MODE_LEGACY)
                        if (spanned.length > maxLength) {
                            val truncatedSpanned = spanned.subSequence(0, maxLength) as Spanned
                            val truncatedHtml = Html.toHtml(truncatedSpanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                            val newClip = ClipData.newHtmlText("truncated", truncatedSpanned.toString(), truncatedHtml)
                            val originalClip = clip
                            clipboard.setPrimaryClip(newClip)
                            val result = super.onTextContextMenuItem(id)
                            clipboard.setPrimaryClip(originalClip)
                            return result
                        }
                    }
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }
}