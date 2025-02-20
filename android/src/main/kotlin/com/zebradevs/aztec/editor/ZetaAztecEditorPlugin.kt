package com.zebradevs.aztec.editor

import android.content.Intent
import com.zebradevs.aztec.AztecEditorApi
import com.zebradevs.aztec.EditorConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/** ZetaAztecEditorPlugin */
class ZetaAztecEditorPlugin : FlutterPlugin, ActivityAware, AztecEditorApi, ActivityResultListener {

    var pendingResult: Result<String>? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        AztecEditorApi.setUp(
            binaryMessenger = flutterPluginBinding.binaryMessenger,
            api = this
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        AztecEditorApi.setUp(
            binaryMessenger = binding.binaryMessenger,
            api = null
        )
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        TODO("Not yet implemented")
    }

    override fun onDetachedFromActivityForConfigChanges() {
        TODO("Not yet implemented")
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        TODO("Not yet implemented")
    }

    override fun onDetachedFromActivity() {
        TODO("Not yet implemented")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun launch(
        initialHtml: String?,
        config: EditorConfig?,
        callback: (Result<String>) -> Unit
    ) {
        callback(Result.success("Hello from Android: $initialHtml"))
    }
}
