package com.raccoonsquad

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.raccoonsquad.ui.RaccoonApp
import com.raccoonsquad.ui.screens.onVpnPermissionResult
import com.raccoonsquad.ui.theme.RaccoonSquadTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    
    // Keep splash visible until data is ready
    private var isDataReady by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Log signature for debugging (helps set up correct expected signature)
        logAppSignature()
        
        // Install splash screen before super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Keep splash screen visible while loading data
        splashScreen.setKeepOnScreenCondition {
            !isDataReady
        }
        
        // Simulate minimum splash time + data loading
        CoroutineScope(Dispatchers.Main).launch {
            delay(600) // Minimum splash time for branding
            isDataReady = true
        }
        
        handleIntent(intent)
        
        setContent {
            RaccoonSquadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaccoonApp()
                }
            }
        }
    }
    
    /**
     * Log app signature SHA-256 for debugging
     * Use this to get the correct signature for release builds
     */
    private fun logAppSignature() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.forEach { signature ->
                val sha256 = getSignatureSha256(signature)
                Log.i("RaccoonApp", "App Signature SHA-256: $sha256")
            }
        } catch (e: Exception) {
            Log.e("RaccoonApp", "Failed to get signature", e)
        }
    }
    
    private fun getSignatureSha256(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            onVpnPermissionResult(resultCode == RESULT_OK, this)
        }
    }
    
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "vless") {
                // TODO: Handle VLESS URI import
            }
        }
    }
}
