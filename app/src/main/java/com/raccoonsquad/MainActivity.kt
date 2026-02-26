package com.raccoonsquad

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.os.Process
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
    
    // Expected SHA-256 signature (debug keystore)
    // Update this when using release keystore
    private val expectedSignatureSha256 = "5D:B3:AB:4E:8F:0C:5F:4E:0A:1B:2C:3D:4E:5F:6A:7B:8C:9D:0E:1F:2A:3B:4C:5D:6E:7F:8A:9B:0C:1D:2E:3F"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Signature verification - security check
        if (!verifySignature()) {
            Log.e("MainActivity", "Signature verification failed! Exiting...")
            finish()
            Process.killProcess(Process.myPid())
            return
        }
        
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
     * Verify app signature to prevent tampering
     * Returns true if signature matches expected value
     */
    private fun verifySignature(): Boolean {
        return try {
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
            
            if (signatures.isNullOrEmpty()) {
                Log.e("MainActivity", "No signatures found!")
                return false
            }
            
            for (signature in signatures) {
                val sha256 = getSignatureSha256(signature)
                Log.d("MainActivity", "App signature SHA-256: $sha256")
                
                // For now, accept any signature in debug builds
                // In production, compare with expectedSignatureSha256
                if (BuildConfig.DEBUG) {
                    return true
                }
                
                // In release builds, verify signature
                if (sha256.replace(" ", "").equals(
                        expectedSignatureSha256.replace(" ", ""), 
                        ignoreCase = true
                    )) {
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e("MainActivity", "Signature verification error", e)
            false
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
