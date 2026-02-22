package com.raccoonsquad.data.model

import android.os.Parcel
import android.os.Parcelable

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
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        uuid = parcel.readString() ?: "",
        serverAddress = parcel.readString() ?: "",
        port = parcel.readInt(),
        name = parcel.readString() ?: "VLESS Node",
        securityMode = SecurityMode.valueOf(parcel.readString() ?: "REALITY"),
        sni = parcel.readString() ?: "",
        realityPublicKey = parcel.readString() ?: "",
        realityShortId = parcel.readString() ?: "",
        realitySpiderX = parcel.readString() ?: "/",
        flow = FlowMode.valueOf(parcel.readString() ?: "XTLS_RPRX_VISION"),
        fragmentationEnabled = parcel.readByte() != 0.toByte(),
        fragmentationPackets = parcel.readString() ?: "1-3",
        fragmentationLength = parcel.readString() ?: "10-20",
        fragmentationInterval = parcel.readString() ?: "10",
        noiseEnabled = parcel.readByte() != 0.toByte(),
        noiseType = parcel.readString() ?: "random",
        noisePacketCount = parcel.readString() ?: "5-10",
        tcpFastOpen = parcel.readByte() != 0.toByte(),
        tcpNoDelay = parcel.readByte() != 0.toByte(),
        tcpKeepAlive = parcel.readByte() != 0.toByte(),
        tcpKeepAliveInterval = parcel.readInt(),
        mtu = parcel.readString() ?: "default"
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uuid)
        parcel.writeString(serverAddress)
        parcel.writeInt(port)
        parcel.writeString(name)
        parcel.writeString(securityMode.name)
        parcel.writeString(sni)
        parcel.writeString(realityPublicKey)
        parcel.writeString(realityShortId)
        parcel.writeString(realitySpiderX)
        parcel.writeString(flow.name)
        parcel.writeByte(if (fragmentationEnabled) 1 else 0)
        parcel.writeString(fragmentationPackets)
        parcel.writeString(fragmentationLength)
        parcel.writeString(fragmentationInterval)
        parcel.writeByte(if (noiseEnabled) 1 else 0)
        parcel.writeString(noiseType)
        parcel.writeString(noisePacketCount)
        parcel.writeByte(if (tcpFastOpen) 1 else 0)
        parcel.writeByte(if (tcpNoDelay) 1 else 0)
        parcel.writeByte(if (tcpKeepAlive) 1 else 0)
        parcel.writeInt(tcpKeepAliveInterval)
        parcel.writeString(mtu)
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

data class NodeGroup(
    val id: String,
    val name: String,
    val nodes: List<VlessConfig> = emptyList()
)
