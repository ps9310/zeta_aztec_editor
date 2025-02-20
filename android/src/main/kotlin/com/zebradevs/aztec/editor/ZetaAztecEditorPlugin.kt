package com.zebradevs.aztec.editor

import android.app.Activity
import android.content.Intent
import com.zebradevs.aztec.AztecEditorApi
import com.zebradevs.aztec.EditorConfig
import com.zebradevs.aztec.utils.runOnUi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/** ZetaAztecEditorPlugin */
class ZetaAztecEditorPlugin : FlutterPlugin, ActivityAware, AztecEditorApi, ActivityResultListener {

    private var pendingResult: ((Result<String>) -> Unit)? = null
    private var activity: Activity? = null

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
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == AztecEditorActivity.REQUEST_CODE) {
            val result = if (resultCode == Activity.RESULT_OK) {
                Result.success(data?.getStringExtra("result") ?: "")
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
        config: EditorConfig?,
        callback: (Result<String>) -> Unit
    ) {
        runOnUi {
            activity?.let { activity ->
                pendingResult = callback
                activity.startActivityForResult(
                    AztecEditorActivity.createIntent(activity, initialHtml),
                    AztecEditorActivity.REQUEST_CODE
                )
            }
        }
    }
}
