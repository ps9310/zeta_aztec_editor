package com.zebradevs.aztec.editor

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.zebradevs.aztec.editor.messages.AztecEditorApi
import com.zebradevs.aztec.editor.messages.AztecEditorConfig
import com.zebradevs.aztec.editor.messages.AztecFlutterApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/** ZetaAztecEditorPlugin */
class ZetaAztecEditorPlugin : FlutterPlugin, ActivityAware, AztecEditorApi, ActivityResultListener {

    private var pendingResult: ((Result<String>) -> Unit)? = null
    private var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.i("ZetaAztecEditorPlugin", "onAttachedToEngine: Called")
        if (AztecFlutterContainer.flutterApi == null) {
            AztecFlutterContainer.flutterApi = AztecFlutterApi(flutterPluginBinding.binaryMessenger)
        }

        AztecEditorApi.setUp(
            binaryMessenger = flutterPluginBinding.binaryMessenger,
            api = this
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.i("ZetaAztecEditorPlugin", "onDetachedFromEngine: Called")
        AztecFlutterContainer.flutterApi = null
        AztecEditorApi.setUp(
            binaryMessenger = binding.binaryMessenger,
            api = null
        )
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.i("ZetaAztecEditorPlugin", "onAttachedToActivity: Called")
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.i("ZetaAztecEditorPlugin", "onDetachedFromActivityForConfigChanges: Called")
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.i("ZetaAztecEditorPlugin", "onReattachedToActivityForConfigChanges: Called")
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        Log.i("ZetaAztecEditorPlugin", "onDetachedFromActivity: Called")
        activity = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        Log.i(
            "ZetaAztecEditorPlugin",
            "onActivityResult: Called with requestCode: $requestCode, resultCode: $resultCode"
        )
        if (requestCode == AztecEditorActivity.REQUEST_CODE) {
            val result = if (resultCode == Activity.RESULT_OK) {
                Result.success(data?.getStringExtra("html") ?: "")
            } else {
                Result.failure(Exception("Editor was cancelled"))
            }

            pendingResult?.invoke(result)
            pendingResult = null
            return true
        }

        return false
    }

    override fun launch(
        initialHtml: String?,
        config: AztecEditorConfig,
        callback: (Result<String?>) -> Unit
    ) {
        Log.i("ZetaAztecEditorPlugin", "launch: Called with initialHtml: $initialHtml")
        runOnUi {
            activity?.let { activity ->
                pendingResult = callback

                val editorConfig = EditorConfig.from(config)

                val intent = AztecEditorActivity.createIntent(
                    activity,
                    initialHtml = initialHtml,
                    editorConfig = editorConfig,
                )

                activity.startActivityForResult(
                    intent,
                    AztecEditorActivity.REQUEST_CODE
                )
            }
        }
    }
}