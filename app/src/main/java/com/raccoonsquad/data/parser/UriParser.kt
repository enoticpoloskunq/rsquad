package com.raccoonsquad.data.parser

import android.net.Uri
import com.raccoonsquad.data.model.*

/**
 * Proper VLESS URI Parser
 * Actually reads ALL parameters including fragment, noise, mtu!
 */
object UriParser {
    
    fun parse(uriString: String): VlessConfig? {
        if (!uriString.startsWith("vless://")) return null
        
        return try {
            val uri = Uri.parse(uriString)
            
            // Basic info
            val uuid = uri.userInfo ?: return null
            val serverAddress = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: 443
            
            // Name from fragment (# part)
            val name = Uri.decode(uri.fragment ?: "VLESS Node")
            
            // Query parameters
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
            
            // SNI
            val sni = params["sni"] ?: params["serverName"] ?: ""
            
            // Reality params
            val realityPublicKey = params["pbk"] ?: params["publicKey"] ?: ""
            val realityShortId = params["sid"] ?: params["shortId"] ?: ""
            val realitySpiderX = params["spx"] ?: params["spiderX"] ?: "/"
            
            // FRAGMENTATION - Parse from multiple formats!
            val fragmentationEnabled = params.containsKey("fragment") || 
                                       params.containsKey("fragmentPackets") ||
                                       params.containsKey("fragmentSize")
            
            var fragmentationPackets = "1-3"
            var fragmentationLength = "10-20"
            var fragmentationInterval = "10"
            
            // Format 1: fragment=packets,length (NekoBox/Husi style)
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
            
            // Format 2: fragmentPackets + fragmentLength (v2rayNG style)
            params["fragmentPackets"]?.let { fragmentationPackets = it }
            params["fragmentLength"]?.let { fragmentationLength = it }
            params["fragmentInterval"]?.let { fragmentationInterval = it }
            
            // Format 3: Hiddify style - fragment=size,sleep,mode
            params["fragmentSize"]?.let { fragmentationLength = it }
            params["fragmentSleep"]?.let { fragmentationInterval = it }
            
            // NOISE
            val noiseEnabled = params.containsKey("noise") || params.containsKey("noiseType")
            val noiseType = params["noiseType"] ?: params["noise"]?.split(",")?.firstOrNull() ?: "random"
            val noisePacketCount = params["noisePacket"] ?: params["noise"]?.split(",")?.lastOrNull() ?: "5-10"
            
            // Socket options
            val tcpFastOpen = params["tcpFastOpen"]?.toBoolean() ?: params["tfo"]?.toBoolean() ?: false
            val tcpNoDelay = params["tcpNoDelay"]?.toBoolean() ?: true
            val tcpKeepAlive = params["tcpKeepAlive"]?.toBoolean() ?: true
            val tcpKeepAliveInterval = params["tcpKeepAliveInterval"]?.toIntOrNull() ?: 30
            
            // MTU
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
     * Generate VLESS URI from config
     */
    fun generateUri(config: VlessConfig): String {
        val uri = Uri.Builder()
            .scheme("vless")
            .encodedUserInfo(config.uuid)
            .encodedAuthority("${config.serverAddress}:${config.port}")
        
        // Security
        uri.appendQueryParameter("security", config.securityMode.name.lowercase())
        uri.appendQueryParameter("type", "tcp")
        
        if (config.sni.isNotEmpty()) {
            uri.appendQueryParameter("sni", config.sni)
        }
        
        // Reality
        if (config.securityMode == SecurityMode.REALITY) {
            if (config.realityPublicKey.isNotEmpty()) {
                uri.appendQueryParameter("pbk", config.realityPublicKey)
            }
            if (config.realityShortId.isNotEmpty()) {
                uri.appendQueryParameter("sid", config.realityShortId)
            }
            uri.appendQueryParameter("fp", "chrome")
        }
        
        // Flow
        if (config.flow != FlowMode.NONE) {
            uri.appendQueryParameter("flow", config.flow.name.lowercase().replace("_", "-"))
        }
        
        // Fragmentation - NekoBox format
        if (config.fragmentationEnabled) {
            uri.appendQueryParameter(
                "fragment",
                "${config.fragmentationPackets},${config.fragmentationLength}"
            )
        }
        
        // Fragment
        uri.fragment(config.name)
        
        return uri.build().toString()
    }
}
