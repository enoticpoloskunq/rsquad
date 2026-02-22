package com.raccoonsquad.core.xray

import android.util.Log

/**
 * Callback handler for libv2ray CoreController
 * 
 * Implements CoreCallbackHandler interface from libv2ray.aar
 * Receives lifecycle callbacks from Xray core
 */
class XrayCallbackHandler : libv2ray.CoreCallbackHandler {
    
    companion object {
        private const val TAG = "XrayCallback"
    }
    
    // Callbacks for status updates
    var onStarted: (() -> Unit)? = null
    var onStopped: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * Called when Xray core starts successfully
     */
    override fun Startup(): Int {
        Log.i(TAG, "Xray core started successfully")
        onStarted?.invoke()
        return 0
    }
    
    /**
     * Called when Xray core shuts down
     */
    override fun Shutdown(): Int {
        Log.i(TAG, "Xray core shutdown")
        onStopped?.invoke()
        return 0
    }
    
    /**
     * Called to emit status updates
     * @param code Status code (0 = normal, non-zero = error)
     * @param message Status message
     */
    override fun OnEmitStatus(code: Int, message: String): Int {
        if (code == 0) {
            Log.d(TAG, "Xray status: $message")
        } else {
            Log.e(TAG, "Xray error ($code): $message")
            onError?.invoke(message)
        }
        return 0
    }
}
