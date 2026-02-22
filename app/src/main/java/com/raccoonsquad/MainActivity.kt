package com.raccoonsquad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.raccoonsquad.ui.RaccoonApp
import com.raccoonsquad.ui.theme.RaccoonSquadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle intent (VLESS URI import)
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
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: android.content.Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "vless") {
                // TODO: Parse VLESS URI and add to nodes
            }
        }
    }
}
