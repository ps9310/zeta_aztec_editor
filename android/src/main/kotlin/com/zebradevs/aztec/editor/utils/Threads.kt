package com.zebradevs.aztec.editor.utils

import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Executes the given block on the UI thread. If already on the UI thread, the block
 * is executed immediately.
 *
 * @param block The code to execute on the UI thread.
 */
fun runOnUi(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        // Already on UI thread, execute immediately.
        block()
    } else {
        // Post to UI thread.
        mainHandler.post(block)
    }
}