package com.zebradevs.aztec.editor

import android.os.Parcelable
import com.zebradevs.aztec.editor.messages.AztecEditorConfig
import com.zebradevs.aztec.editor.messages.AztecEditorTheme
import com.zebradevs.aztec.editor.messages.AztecToolbarOption
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class EditorConfig(
    val primaryColor: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val placeholder: String? = null,
    val characterLimit: Long? = null,
    val title: String,
    val theme: AztecEditorTheme,
    val toolbarOptions: List<AztecToolbarOption>,
    val authHeaders: @RawValue Map<String, String>
) : Parcelable {


    companion object {
        fun from(aztecConfig: AztecEditorConfig): EditorConfig {
            return EditorConfig(
                primaryColor = aztecConfig.primaryColor,
                backgroundColor = aztecConfig.backgroundColor,
                textColor = aztecConfig.textColor,
                placeholder = aztecConfig.placeholder,
                characterLimit = aztecConfig.characterLimit,
                title = aztecConfig.title,
                theme = aztecConfig.theme ?: AztecEditorTheme.LIGHT,
                toolbarOptions = aztecConfig.toolbarOptions ?: AztecToolbarOption.entries,
                authHeaders = aztecConfig.authHeaders ?: emptyMap()
            )
        }
    }
}