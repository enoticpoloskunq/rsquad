package com.raccoonsquad.data.model

data class VlessConfig(
    val uuid: String,
    val serverAddress: String,
    val port: Int,
    val name: String = "VLESS Node",
    
    // Security
    val securityMode: SecurityMode = SecurityMode.REALITY,
    val sni: String = "",
    
    // Reality
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "/",
    
    // Flow
    val flow: FlowMode = FlowMode.XTLS_RPRX_VISION,
    
    // Fragmentation - THE IMPORTANT PART!
    val fragmentationEnabled: Boolean = false,
    val fragmentationPackets: String = "1-3",
    val fragmentationLength: String = "10-20",
    val fragmentationInterval: String = "10",
    
    // Noise
    val noiseEnabled: Boolean = false,
    val noiseType: String = "random",
    val noisePacketCount: String = "5-10",
    
    // Socket
    val tcpFastOpen: Boolean = false,
    val tcpNoDelay: Boolean = true,
    val tcpKeepAlive: Boolean = true,
    val tcpKeepAliveInterval: Int = 30,
    
    // MTU
    val mtu: String = "default",
    
    // Status
    val isActive: Boolean = false,
    val latency: Long? = null,
    val lastConnected: Long? = null
)

enum class SecurityMode {
    REALITY, TLS, NONE
}

enum class FlowMode {
    NONE, XTLS_RPRX_VISION, XTLS_RPRX_VISION_UDP443
}

data class NodeGroup(
    val id: String,
    val name: String,
    val nodes: List<VlessConfig> = emptyList()
)
