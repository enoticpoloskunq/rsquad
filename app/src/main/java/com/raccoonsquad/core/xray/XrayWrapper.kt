package com.raccoonsquad.core.xray

import android.content.Context
import android.util.Log
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import com.raccoonsquad.core.log.LogManager
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wrapper for Xray-core via libv2ray.aar
 */
object XrayWrapper {
    
    private const val TAG = "Xray"
    
    private var coreController: Any? = null
    private var isRunning = false
    private var currentConfig: String? = null
    
    /**
     * Initialize Xray wrapper with application context
     */
    fun init(context: Context) {
        LogManager.i(TAG, "=== XrayWrapper.init() START ===")
        LogManager.flush()
        
        try {
            val filesDir = context.filesDir.absolutePath
            LogManager.d(TAG, "filesDir: $filesDir")
            
            val geoDir = File(filesDir, "xray")
            LogManager.d(TAG, "geoDir: ${geoDir.absolutePath}, exists: ${geoDir.exists()}")
            
            if (!geoDir.exists()) {
                val created = geoDir.mkdirs()
                LogManager.d(TAG, "geoDir created: $created")
            }
            
            LogManager.i(TAG, "Calling Libv2ray.initCoreEnv...")
            LogManager.flush()
            
            Libv2ray.initCoreEnv(geoDir.absolutePath, "")
            
            LogManager.i(TAG, "Libv2ray.initCoreEnv DONE")
            LogManager.flush()
            
            val version = Libv2ray.checkVersionX()
            LogManager.i(TAG, "Xray version: $version")
            LogManager.i(TAG, "=== XrayWrapper.init() SUCCESS ===")
            LogManager.flush()
            
        } catch (e: Exception) {
            LogManager.e(TAG, "=== XrayWrapper.init() FAILED ===", e)
            LogManager.flush()
        }
    }
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        LogManager.d(TAG, "generateConfig() for ${config.name}")
        
        val json = JSONObject()
        
        json.put("log", JSONObject().put("loglevel", "warning"))
        
        val inbounds = JSONArray()
        
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("udp", true)
                put("auth", "noauth")
            })
        })
        
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        })
        
        json.put("inbounds", inbounds)
        
        val outbounds = JSONArray()
        
        outbounds.put(JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", config.serverAddress)
                    put("port", config.port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", config.uuid)
                        put("encryption", "none")
                        if (config.flow != FlowMode.NONE) {
                            put("flow", when(config.flow) {
                                FlowMode.XTLS_RPRX_VISION -> "xtls-rprx-vision"
                                FlowMode.XTLS_RPRX_VISION_UDP443 -> "xtls-rprx-vision-udp443"
                                else -> ""
                            })
                        }
                    }))
                }))
            })
            put("streamSettings", JSONObject().apply {
                put("network", "tcp")
                put("security", when(config.securityMode) {
                    SecurityMode.REALITY -> "reality"
                    SecurityMode.TLS -> "tls"
                    SecurityMode.NONE -> "none"
                })
                
                if (config.securityMode == SecurityMode.REALITY) {
                    put("realitySettings", JSONObject().apply {
                        put("show", false)
                        put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                        if (config.sni.isNotEmpty()) put("serverName", config.sni)
                        if (config.realityPublicKey.isNotEmpty()) put("publicKey", config.realityPublicKey)
                        if (config.realityShortId.isNotEmpty()) put("shortId", config.realityShortId)
                        if (config.realitySpiderX.isNotEmpty()) put("spiderX", config.realitySpiderX)
                    })
                } else if (config.securityMode == SecurityMode.TLS) {
                    put("tlsSettings", JSONObject().apply {
                        put("allowInsecure", false)
                        if (config.sni.isNotEmpty()) put("serverName", config.sni)
                        put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                    })
                }
                
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) put("tcpFastOpen", true)
                    if (config.tcpNoDelay) put("tcpNoDelay", true)
                    if (config.tcpKeepAliveInterval > 0) put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                    
                    if (config.fragmentationEnabled) {
                        put("dialer", JSONObject().apply {
                            put("domainStrategy", "AsIs")
                            put("fragment", JSONObject().apply {
                                put("packets", config.fragmentPackets)
                                put("length", config.fragmentLength)
                                put("interval", config.fragmentInterval)
                            })
                        })
                    }
                })
            })
        })
        
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        })
        
        json.put("outbounds", outbounds)
        
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                })
            })
        })
        
        return json.toString(2)
    }
    
    fun start(configJson: String): Boolean {
        LogManager.i(TAG, "=== XrayWrapper.start() ===")
        LogManager.d(TAG, "Config length: ${configJson.length}")
        LogManager.flush()
        
        if (isRunning) {
            LogManager.w(TAG, "Already running, stopping first")
            stop()
        }
        
        try {
            currentConfig = configJson
            
            LogManager.d(TAG, "Creating CoreCallbackHandler...")
            LogManager.flush()
            
            val callback = object : CoreCallbackHandler {
                override fun startup(): Long {
                    LogManager.i(TAG, "Xray core STARTED callback")
                    return 0
                }
                
                override fun shutdown(): Long {
                    LogManager.i(TAG, "Xray core SHUTDOWN callback")
                    return 0
                }
                
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    LogManager.d(TAG, "Xray status: $p0 - ${p1 ?: "null"}")
                    return 0
                }
            }
            
            LogManager.d(TAG, "Creating CoreController...")
            LogManager.flush()
            
            coreController = Libv2ray.newCoreController(callback)
            
            LogManager.d(TAG, "CoreController created: ${coreController != null}")
            LogManager.flush()
            
            val controller = coreController as? libv2ray.CoreController
            LogManager.d(TAG, "Casting controller: ${controller != null}")
            LogManager.flush()
            
            LogManager.i(TAG, "Calling startLoop...")
            LogManager.flush()
            
            controller?.startLoop(configJson, 0)
            
            isRunning = true
            LogManager.i(TAG, "=== XrayWrapper.start() SUCCESS ===")
            LogManager.flush()
            return true
            
        } catch (e: Exception) {
            LogManager.e(TAG, "=== XrayWrapper.start() FAILED ===", e)
            LogManager.flush()
            return false
        }
    }
    
    fun stop() {
        if (!isRunning) return
        
        LogManager.i(TAG, "XrayWrapper.stop()")
        
        try {
            val controller = coreController as? libv2ray.CoreController
            controller?.stopLoop()
            coreController = null
            isRunning = false
            currentConfig = null
            LogManager.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to stop Xray", e)
        }
        
        LogManager.flush()
    }
    
    fun isRunning(): Boolean = isRunning
}
