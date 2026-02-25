package com.raccoonsquad.data.parser

import android.net.Uri
import android.util.Base64
import com.raccoonsquad.data.model.*

/**
 * Parser for VLESS URI format with full cosmetic settings support
 * 
 * Supports multiple formats:
 * - Standard VLESS URI
 * - NekoBox format (fragment parameters)
 * - v2rayNG format
 * - Hiddify format
 */
object UriParser {
    
    /**
     * Parse single VLESS URI
     */
    fun parse(uriString: String): VlessConfig? {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("vless://")) return null
        
        return try {
            val uri = Uri.parse(trimmed)
            
            val uuid = uri.userInfo ?: return null
            val serverAddress = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: 443
            val name = Uri.decode(uri.fragment ?: "VLESS Node")
            
            val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
            
            // Security mode
            val security = params["security"] ?: params["type"] ?: "reality"
            val securityMode = when (security.lowercase()) {
                "reality" -> SecurityMode.REALITY
                "tls" -> SecurityMode.TLS
                else -> SecurityMode.NONE
            }
            
            // Flow
            val flowParam = params["flow"] ?: ""
            val flow = when {
                flowParam.contains("vision", ignoreCase = true) && flowParam.contains("udp443") -> FlowMode.XTLS_RPRX_VISION_UDP443
                flowParam.contains("vision", ignoreCase = true) -> FlowMode.XTLS_RPRX_VISION
                else -> FlowMode.NONE
            }
            
            // Basic params
            val sni = params["sni"] ?: params["serverName"] ?: ""
            val fingerprint = params["fp"] ?: params["fingerprint"] ?: "chrome"
            
            // Reality params
            val realityPublicKey = params["pbk"] ?: params["publicKey"] ?: ""
            val realityShortId = params["sid"] ?: params["shortId"] ?: ""
            val realitySpiderX = params["spx"] ?: params["spiderX"] ?: "/"
            
            // Fragmentation - THE KEY FEATURE!
            val fragmentationEnabled = params.containsKey("fragment") || 
                                       params.containsKey("fragmentPackets") ||
                                       params.containsKey("fragmentSize") ||
                                       params[" fragmentation"].let { it != null && it != "false" }
            
            var fragmentType = ""
            var fragmentPackets = "1-3"
            var fragmentLength = "10-20"
            var fragmentInterval = "10-20"
            
            // Parse fragment parameter (multiple formats)
            params["fragment"]?.let { frag ->
                when {
                    // Format: "tlshello" (NekoBox style)
                    frag.equals("tlshello", ignoreCase = true) -> {
                        fragmentType = "tlshello"
                        fragmentPackets = "tlshello"
                    }
                    // Format: "packets,length,interval"
                    frag.contains(",") -> {
                        val parts = frag.split(",")
                        if (parts.isNotEmpty()) fragmentPackets = parts[0]
                        if (parts.size > 1) fragmentLength = parts[1]
                        if (parts.size > 2) fragmentInterval = parts[2]
                    }
                    // Format: just packets count
                    frag != "true" && frag != "false" -> {
                        fragmentPackets = frag
                    }
                }
            }
            
            // NekoBox format: fragmentation=tlshello
            params["fragmentation"]?.let { frag ->
                if (frag.equals("tlshello", ignoreCase = true)) {
                    fragmentType = "tlshello"
                    fragmentPackets = "tlshello"
                }
            }
            
            // Individual fragment params
            params["fragmentPackets"]?.let { fragmentPackets = it }
            params["fragmentLength"]?.let { fragmentLength = it }
            params["fragmentInterval"]?.let { fragmentInterval = it }
            params["fragmentSize"]?.let { fragmentLength = it }
            params["fragmentSleep"]?.let { fragmentInterval = it }
            
            // Fragment type specific
            params["fragmentType"]?.let { fragmentType = it }
            
            // Noise settings
            val noiseEnabled = params.containsKey("noise") || 
                              params.containsKey("noiseType") ||
                              params.containsKey("noisePacket")
            
            var noiseType = "random"
            var noisePacketSize = "10-20"
            var noiseDelay = "10-20"
            
            params["noise"]?.let { noise ->
                if (noise.contains(",")) {
                    val parts = noise.split(",")
                    if (parts.isNotEmpty()) noiseType = parts[0]
                    if (parts.size > 1) noisePacketSize = parts[1]
                    if (parts.size > 2) noiseDelay = parts[2]
                }
            }
            
            params["noiseType"]?.let { noiseType = it }
            params["noisePacket"]?.let { noisePacketSize = it }
            params["noisePacketSize"]?.let { noisePacketSize = it }
            params["noiseDelay"]?.let { noiseDelay = it }
            
            // Socket options
            val tcpFastOpen = params["tcpFastOpen"]?.toBoolean() ?: 
                             params["tfo"]?.toBoolean() ?: false
            val tcpNoDelay = params["tcpNoDelay"]?.toBoolean() ?: true
            val tcpKeepAlive = params["tcpKeepAlive"]?.toBoolean() ?: true
            val tcpKeepAliveInterval = params["tcpKeepAliveInterval"]?.toIntOrNull() ?: 30
            
            // MTU
            val mtu = params["mtu"] ?: "1500"
            
            VlessConfig(
                uuid = uuid,
                serverAddress = serverAddress,
                port = port,
                name = name,
                securityMode = securityMode,
                sni = sni,
                fingerprint = fingerprint,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                realitySpiderX = realitySpiderX,
                flow = flow,
                fragmentationEnabled = fragmentationEnabled,
                fragmentType = fragmentType,
                fragmentPackets = fragmentPackets,
                fragmentLength = fragmentLength,
                fragmentInterval = fragmentInterval,
                noiseEnabled = noiseEnabled,
                noiseType = noiseType,
                noisePacketSize = noisePacketSize,
                noiseDelay = noiseDelay,
                tcpFastOpen = tcpFastOpen,
                tcpNoDelay = tcpNoDelay,
                tcpKeepAlive = tcpKeepAlive,
                tcpKeepAliveInterval = tcpKeepAliveInterval,
                mtu = mtu
            )
        } catch (e: Exception) {
            android.util.Log.e("UriParser", "Failed to parse URI: ${e.message}")
            null
        }
    }
    
    /**
     * Parse multiple VLESS URIs from text (subscription format)
     * Supports: plain URIs, base64 encoded, mixed formats
     */
    fun parseMultiple(text: String): List<VlessConfig> {
        val configs = mutableListOf<VlessConfig>()
        
        // Try base64 decode first
        val decodedText = try {
            val cleanInput = text.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
            
            if (cleanInput.startsWith("vless://") || cleanInput.startsWith("vmess://")) {
                text // Not base64, plain URIs
            } else {
                String(Base64.decode(cleanInput, Base64.DEFAULT))
            }
        } catch (e: Exception) {
            text // Not valid base64, use original
        }
        
        // Split by lines and parse each
        decodedText.lines()
            .map { it.trim() }
            .filter { it.startsWith("vless://") }
            .forEach { line ->
                parse(line)?.let { configs.add(it) }
            }
        
        return configs
    }
    
    /**
     * Generate VLESS URI from config
     */
    fun generateUri(config: VlessConfig): String {
        val queryParams = mutableListOf<String>()
        
        queryParams.add("security=${config.securityMode.name.lowercase()}")
        queryParams.add("type=tcp")
        queryParams.add("fp=${config.fingerprint}")
        
        if (config.sni.isNotEmpty()) {
            queryParams.add("sni=${config.sni}")
        }
        
        if (config.securityMode == SecurityMode.REALITY) {
            if (config.realityPublicKey.isNotEmpty()) {
                queryParams.add("pbk=${config.realityPublicKey}")
            }
            if (config.realityShortId.isNotEmpty()) {
                queryParams.add("sid=${config.realityShortId}")
            }
            if (config.realitySpiderX.isNotEmpty()) {
                queryParams.add("spx=${config.realitySpiderX}")
            }
        }
        
        if (config.flow != FlowMode.NONE) {
            queryParams.add("flow=${config.flow.name.lowercase().replace("_", "-")}")
        }
        
        // Fragmentation
        if (config.fragmentationEnabled) {
            val fragValue = if (config.fragmentType == "tlshello") {
                "tlshello"
            } else {
                "${config.fragmentPackets},${config.fragmentLength},${config.fragmentInterval}"
            }
            queryParams.add("fragment=$fragValue")
        }
        
        // Noise
        if (config.noiseEnabled) {
            queryParams.add("noise=${config.noiseType},${config.noisePacketSize},${config.noiseDelay}")
        }
        
        // MTU
        if (config.mtu != "1500") {
            queryParams.add("mtu=${config.mtu}")
        }
        
        val query = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
        val fragment = if (config.name.isNotEmpty()) "#${Uri.encode(config.name)}" else ""
        
        return "vless://${config.uuid}@${config.serverAddress}:${config.port}$query$fragment"
    }
}
