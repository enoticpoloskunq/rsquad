package com.raccoonsquad.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raccoonsquad.ui.viewmodel.NodeUiState
import kotlinx.coroutines.launch

/**
 * Animated connection status widget with pulsing effect
 */
@Composable
fun AnimatedConnectionWidget(
    isActive: Boolean,
    activeNode: NodeUiState?,
    isConnecting: Boolean = false,
    downloadSpeed: String = "0 B/s",
    uploadSpeed: String = "0 B/s",
    totalDownload: String = "0 B",
    totalUpload: String = "0 B",
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Pulsing animation for active connection
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    // Rotation animation for connecting state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main connection button with animation
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer pulse ring (only when connected)
                if (isActive && !isConnecting) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulseScale)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                    )
                }
                
                // Secondary pulse ring
                if (isActive && !isConnecting) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale * 0.95f)
                            .alpha(pulseAlpha * 0.5f)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                    )
                }
                
                // Main button
                FilledIconButton(
                    onClick = { 
                        scope.launch {
                            if (isActive) onDisconnect() else onConnect()
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = when {
                            isConnecting -> MaterialTheme.colorScheme.tertiary
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = when {
                            isConnecting -> MaterialTheme.colorScheme.onTertiary
                            isActive -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    when {
                        isConnecting -> {
                            // Spinning connecting indicator
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer { rotationZ = rotation }
                            ) {
                                drawArc(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    startAngle = 0f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 4.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }
                        }
                        isActive -> {
                            Text("🛡️", style = MaterialTheme.typography.displaySmall)
                        }
                        else -> {
                            Text("🦝", style = MaterialTheme.typography.displaySmall)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status text
            AnimatedContent(
                targetState = Triple(isActive, isConnecting, activeNode?.name),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith 
                    fadeOut(animationSpec = tween(300))
                },
                label = "status_content"
            ) { (connected, connecting, nodeName) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            connecting -> "Подключение..."
                            connected -> "Подключено"
                            else -> "Отключено"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            connecting -> MaterialTheme.colorScheme.tertiary
                            connected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    if (connected && nodeName != null) {
                        Text(
                            text = nodeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // Speed stats (only when connected)
            AnimatedVisibility(
                visible = isActive && !isConnecting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Download
                    SpeedStatColumn(
                        emoji = "⬇️",
                        speed = downloadSpeed,
                        total = totalDownload,
                        label = "Download"
                    )
                    
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    
                    // Upload
                    SpeedStatColumn(
                        emoji = "⬆️",
                        speed = uploadSpeed,
                        total = totalUpload,
                        label = "Upload"
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedStatColumn(
    emoji: String,
    speed: String,
    total: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(
            text = speed,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
