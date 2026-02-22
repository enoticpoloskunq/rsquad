package com.raccoonsquad.data.parser

import android.net.Uri
import android.util.Base64
import com.raccoonsquad.data.model.*

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
            
            val security = params["security"] ?: params["type"] ?: "reality"
            val securityMode = when (security.lowercase()) {
                "reality" -> SecurityMode.REALITY
                "tls" -> SecurityMode.TLS
                else -> SecurityMode.NONE
            }
            
            val flowParam = params["flow"] ?: ""
            val flow = when {
                flowParam.contains("vision", ignoreCase = true) && flowParam.contains("udp443") -> FlowMode.XTLS_RPRX_VISION_UDP443
                flowParam.contains("vision", ignoreCase = true) -> FlowMode.XTLS_RPRX_VISION
                else -> FlowMode.NONE
            }
            
            val sni = params["sni"] ?: params["serverName"] ?: ""
            val realityPublicKey = params["pbk"] ?: params["publicKey"] ?: ""
            val realityShortId = params["sid"] ?: params["shortId"] ?: ""
            val realitySpiderX = params["spx"] ?: params["spiderX"] ?: "/"
            
            val fragmentationEnabled = params.containsKey("fragment") || 
                                       params.containsKey("fragmentPackets") ||
                                       params.containsKey("fragmentSize")
            
            var fragmentationPackets = "1-3"
            var fragmentationLength = "10-20"
            var fragmentationInterval = "10"
            
            params["fragment"]?.let { frag ->
                if (frag.contains(",")) {
                    val parts = frag.split(",")
                    if (parts.isNotEmpty()) fragmentationPackets = parts[0]
                    if (parts.size > 1) fragmentationLength = parts[1]
                    if (parts.size > 2) fragmentationInterval = parts[2]
                } else if (frag != "true" && frag != "false") {
                    fragmentationPackets = frag
                }
            }
            
            params["fragmentPackets"]?.let { fragmentationPackets = it }
            params["fragmentLength"]?.let { fragmentationLength = it }
            params["fragmentInterval"]?.let { fragmentationInterval = it }
            params["fragmentSize"]?.let { fragmentationLength = it }
            params["fragmentSleep"]?.let { fragmentationInterval = it }
            
            val noiseEnabled = params.containsKey("noise") || params.containsKey("noiseType")
            val noiseType = params["noiseType"] ?: params["noise"]?.split(",")?.firstOrNull() ?: "random"
            val noisePacketCount = params["noisePacket"] ?: params["noise"]?.split(",")?.lastOrNull() ?: "5-10"
            
            val tcpFastOpen = params["tcpFastOpen"]?.toBoolean() ?: params["tfo"]?.toBoolean() ?: false
            val tcpNoDelay = params["tcpNoDelay"]?.toBoolean() ?: true
            val tcpKeepAlive = params["tcpKeepAlive"]?.toBoolean() ?: true
            val tcpKeepAliveInterval = params["tcpKeepAliveInterval"]?.toIntOrNull() ?: 30
            
            val mtu = params["mtu"] ?: "default"
            
            VlessConfig(
                uuid = uuid,
                serverAddress = serverAddress,
                port = port,
                name = name,
                securityMode = securityMode,
                sni = sni,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                realitySpiderX = realitySpiderX,
                flow = flow,
                fragmentationEnabled = fragmentationEnabled,
                fragmentationPackets = fragmentationPackets,
                fragmentationLength = fragmentationLength,
                fragmentationInterval = fragmentationInterval,
                noiseEnabled = noiseEnabled,
                noiseType = noiseType,
                noisePacketCount = noisePacketCount,
                tcpFastOpen = tcpFastOpen,
                tcpNoDelay = tcpNoDelay,
                tcpKeepAlive = tcpKeepAlive,
                tcpKeepAliveInterval = tcpKeepAliveInterval,
                mtu = mtu
            )
        } catch (e: Exception) {
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
            
            if (cleanInput.startsWith("vless://")) {
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
            .forEachNotNull { line ->
                parse(line)?.let { configs.add(it) }
            }
        
        return configs
    }
    
    fun generateUri(config: VlessConfig): String {
        val queryParams = mutableListOf<String>()
        
        queryParams.add("security=${config.securityMode.name.lowercase()}")
        queryParams.add("type=tcp")
        
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
            queryParams.add("fp=chrome")
        }
        
        if (config.flow != FlowMode.NONE) {
            queryParams.add("flow=${config.flow.name.lowercase().replace("_", "-")}")
        }
        
        if (config.fragmentationEnabled) {
            queryParams.add("fragment=${config.fragmentationPackets},${config.fragmentationLength}")
        }
        
        val query = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
        val fragment = if (config.name.isNotEmpty()) "#${Uri.encode(config.name)}" else ""
        
        return "vless://${config.uuid}@${config.serverAddress}:${config.port}$query$fragment"
    }
}

// Helper extension
inline fun <T, R : Any> Iterable<T>.forEachNotNull(action: (T) -> R?) {
    for (element in this) action(element)
}
