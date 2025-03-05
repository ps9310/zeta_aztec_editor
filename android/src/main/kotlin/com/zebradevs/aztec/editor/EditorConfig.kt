package com.zebradevs.aztec.editor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class EditorConfig(
    val primaryColor: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val placeholder: String? = null,
    val title: String,
    val theme: AztecEditorTheme,
    val fileExtensions: List<String>? = null,
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
                fileExtensions = aztecConfig.fileExtensions,
                title = aztecConfig.title,
                theme = aztecConfig.theme,
                toolbarOptions = aztecConfig.toolbarOptions ?: AztecToolbarOption.entries,
                authHeaders = aztecConfig.authHeaders ?: emptyMap()
            )
        }
    }
}