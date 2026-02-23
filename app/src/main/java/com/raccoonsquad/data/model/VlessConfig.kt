package com.raccoonsquad.data.model

import android.os.Parcel
import android.os.Parcelable

/**
 * VLESS configuration with full support for cosmetic settings
 * 
 * Includes fragmentation, noise, and other parameters that most
 * VPN clients ignore but are crucial for bypassing censorship.
 */
data class VlessConfig(
    // Core settings
    val uuid: String,
    val serverAddress: String,
    val port: Int,
    val name: String = "VLESS Node",
    
    // Security
    val securityMode: SecurityMode = SecurityMode.REALITY,
    val sni: String = "",
    val fingerprint: String = "chrome",
    
    // Reality
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "/",
    
    // Flow
    val flow: FlowMode = FlowMode.XTLS_RPRX_VISION,
    
    // Fragmentation - THE KEY FEATURE!
    val fragmentationEnabled: Boolean = false,
    val fragmentType: String = "", // "tlshello" or packets range
    val fragmentPackets: String = "1-3",
    val fragmentLength: String = "10-20",
    val fragmentInterval: String = "10-20",
    
    // Noise (packet obfuscation)
    val noiseEnabled: Boolean = false,
    val noiseType: String = "random",
    val noisePacketSize: String = "10-20",
    val noiseDelay: String = "10-20",
    
    // Socket options
    val tcpFastOpen: Boolean = false,
    val tcpNoDelay: Boolean = true,
    val tcpKeepAlive: Boolean = true,
    val tcpKeepAliveInterval: Int = 30,
    
    // MTU
    val mtu: String = "1500",
    
    // Status
    val isActive: Boolean = false,
    val latency: Long? = null,
    val lastConnected: Long? = null,
    val isFavorite: Boolean = false
) : Parcelable {
    
    // Legacy property aliases for backward compatibility
    val fragmentationPackets: String get() = fragmentPackets
    val fragmentationLength: String get() = fragmentLength
    val fragmentationInterval: String get() = fragmentInterval
    val noisePacketCount: String get() = noisePacketSize
    
    /**
     * Display name with cosmetics info
     */
    fun getDisplayName(): String {
        val parts = mutableListOf<String>()
        
        // Add fragmentation indicator
        if (fragmentationEnabled) {
            parts.add("F")
        }
        
        // Add noise indicator
        if (noiseEnabled) {
            parts.add("N")
        }
        
        // Build name
        val baseName = name.ifEmpty { "$serverAddress:$port" }
        
        return if (parts.isNotEmpty()) {
            "$baseName [${parts.joinToString(", ")}]"
        } else {
            baseName
        }
    }
    
    /**
     * Short description of cosmetics
     */
    fun getCosmeticsInfo(): String {
        val parts = mutableListOf<String>()
        
        if (fragmentationEnabled) {
            parts.add("frag:${fragmentPackets}")
        }
        
        if (noiseEnabled) {
            parts.add("noise:${noiseType}")
        }
        
        if (mtu != "1500") {
            parts.add("mtu:$mtu")
        }
        
        if (flow != FlowMode.NONE) {
            parts.add(flow.name.lowercase().replace("_", "-"))
        }
        
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "no cosmetics"
    }
    
    constructor(parcel: Parcel) : this(
        uuid = parcel.readString() ?: "",
        serverAddress = parcel.readString() ?: "",
        port = parcel.readInt(),
        name = parcel.readString() ?: "VLESS Node",
        securityMode = SecurityMode.valueOf(parcel.readString() ?: "REALITY"),
        sni = parcel.readString() ?: "",
        fingerprint = parcel.readString() ?: "chrome",
        realityPublicKey = parcel.readString() ?: "",
        realityShortId = parcel.readString() ?: "",
        realitySpiderX = parcel.readString() ?: "/",
        flow = FlowMode.valueOf(parcel.readString() ?: "XTLS_RPRX_VISION"),
        fragmentationEnabled = parcel.readByte() != 0.toByte(),
        fragmentType = parcel.readString() ?: "",
        fragmentPackets = parcel.readString() ?: "1-3",
        fragmentLength = parcel.readString() ?: "10-20",
        fragmentInterval = parcel.readString() ?: "10-20",
        noiseEnabled = parcel.readByte() != 0.toByte(),
        noiseType = parcel.readString() ?: "random",
        noisePacketSize = parcel.readString() ?: "10-20",
        noiseDelay = parcel.readString() ?: "10-20",
        tcpFastOpen = parcel.readByte() != 0.toByte(),
        tcpNoDelay = parcel.readByte() != 0.toByte(),
        tcpKeepAlive = parcel.readByte() != 0.toByte(),
        tcpKeepAliveInterval = parcel.readInt(),
        mtu = parcel.readString() ?: "1500",
        isFavorite = parcel.readByte() != 0.toByte()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uuid)
        parcel.writeString(serverAddress)
        parcel.writeInt(port)
        parcel.writeString(name)
        parcel.writeString(securityMode.name)
        parcel.writeString(sni)
        parcel.writeString(fingerprint)
        parcel.writeString(realityPublicKey)
        parcel.writeString(realityShortId)
        parcel.writeString(realitySpiderX)
        parcel.writeString(flow.name)
        parcel.writeByte(if (fragmentationEnabled) 1 else 0)
        parcel.writeString(fragmentType)
        parcel.writeString(fragmentPackets)
        parcel.writeString(fragmentLength)
        parcel.writeString(fragmentInterval)
        parcel.writeByte(if (noiseEnabled) 1 else 0)
        parcel.writeString(noiseType)
        parcel.writeString(noisePacketSize)
        parcel.writeString(noiseDelay)
        parcel.writeByte(if (tcpFastOpen) 1 else 0)
        parcel.writeByte(if (tcpNoDelay) 1 else 0)
        parcel.writeByte(if (tcpKeepAlive) 1 else 0)
        parcel.writeInt(tcpKeepAliveInterval)
        parcel.writeString(mtu)
        parcel.writeByte(if (isFavorite) 1 else 0)
    }
    
    override fun describeContents(): Int = 0
    
    companion object CREATOR : Parcelable.Creator<VlessConfig> {
        override fun createFromParcel(parcel: Parcel): VlessConfig = VlessConfig(parcel)
        override fun newArray(size: Int): Array<VlessConfig?> = arrayOfNulls(size)
    }
}

enum class SecurityMode {
    REALITY, TLS, NONE
}

enum class FlowMode {
    NONE, XTLS_RPRX_VISION, XTLS_RPRX_VISION_UDP443
}

/**
 * Group of nodes for organization
 */
data class NodeGroup(
    val id: String,
    val name: String,
    val nodes: List<VlessConfig> = emptyList()
)
