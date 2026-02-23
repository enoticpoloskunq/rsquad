package com.raccoonsquad.core.xray

import android.content.Context
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import com.raccoonsquad.core.log.LogManager
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wrapper for Xray-core via libv2ray.aar
 * API: Libv2ray.newV2RayPoint() -> V2RayPoint.runLoop()/stopLoop()
 */
object XrayWrapper {
    
    private const val TAG = "Xray"
    
    private var v2rayPoint: V2RayPoint? = null
    private var isRunning = false
    private var currentConfig: String? = null
    private var runThread: Thread? = null
    
    /**
     * Initialize Xray wrapper with application context
     */
    fun init(context: Context) {
        LogManager.i(TAG, "=== XrayWrapper.init() START ===")
        LogManager.flush()
        
        try {
            val filesDir = context.filesDir.absolutePath
            LogManager.d(TAG, "filesDir: $filesDir")
            
            // Create xray directory for assets
            val xrayDir = File(filesDir, "xray")
            if (!xrayDir.exists()) {
                val created = xrayDir.mkdirs()
                LogManager.d(TAG, "xrayDir created: $created")
            }
            
            // Copy geoip.dat and geosite.dat from assets to files dir
            copyAssetsIfNeeded(context, xrayDir)
            
            LogManager.i(TAG, "Calling Libv2ray.initV2Env...")
            LogManager.flush()
            
            // API: initV2Env(assetsPath, externalAssetsPath)
            Libv2ray.initV2Env(xrayDir.absolutePath, "")
            
            LogManager.i(TAG, "Libv2ray.initV2Env DONE")
            
            val version = Libv2ray.checkVersionX()
            LogManager.i(TAG, "Xray version: $version")
            LogManager.i(TAG, "=== XrayWrapper.init() SUCCESS ===")
            LogManager.flush()
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "=== XrayWrapper.init() FAILED ===", e)
            LogManager.flush()
            throw e
        }
    }
    
    /**
     * Copy geoip.dat and geosite.dat from assets to files directory
     */
    private fun copyAssetsIfNeeded(context: Context, destDir: File) {
        try {
            val assets = listOf("geoip.dat", "geosite.dat")
            
            for (asset in assets) {
                val destFile = File(destDir, asset)
                
                // Only copy if not exists
                if (!destFile.exists()) {
                    LogManager.d(TAG, "Copying $asset from assets...")
                    
                    context.assets.open(asset).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    LogManager.d(TAG, "$asset copied (${destFile.length()} bytes)")
                } else {
                    LogManager.d(TAG, "$asset already exists (${destFile.length()} bytes)")
                }
            }
        } catch (e: Throwable) {
            LogManager.e(TAG, "Failed to copy assets", e)
        }
    }
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        LogManager.d(TAG, "generateConfig() for ${config.name}")
        
        val json = JSONObject()
        
        // Log settings
        json.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        
        // Inbounds - SOCKS5 and HTTP proxy
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
        
        // Outbounds
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
                
                // Socket options (fragment, etc)
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) put("tcpFastOpen", true)
                    if (config.tcpNoDelay) put("tcpNoDelay", true)
                    if (config.tcpKeepAliveInterval > 0) put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                    
                    // FRAGMENTATION - the key feature!
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
        
        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        
        // Block outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        })
        
        json.put("outbounds", outbounds)
        
        // Routing
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Private IPs -> direct
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                // Everything else -> proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                })
            })
        })
        
        return json.toString(2)
    }
    
    /**
     * Start Xray with given config
     * Uses V2RayPoint.runLoop() which is BLOCKING - run in separate thread!
     */
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
            
            // Create V2RayVPNServiceSupportsSet callback
            val supportsSet = object : V2RayVPNServiceSupportsSet() {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    LogManager.d(TAG, "Xray status: $p0 - ${p1 ?: "null"}")
                    return 0
                }
                
                override fun prepare(): Long {
                    LogManager.i(TAG, "Xray prepare()")
                    return 0
                }
                
                override fun protect(p0: Long): Boolean {
                    LogManager.d(TAG, "Xray protect($p0)")
                    return true
                }
                
                override fun setup(p0: String?): Long {
                    LogManager.i(TAG, "Xray setup: ${p0?.take(100)}...")
                    return 0
                }
                
                override fun shutdown() {
                    LogManager.i(TAG, "Xray shutdown()")
                }
            }
            
            LogManager.d(TAG, "Creating V2RayPoint...")
            LogManager.flush()
            
            // API: newV2RayPoint(supportsSet, isVPN)
            // isVPN = false for proxy mode (SOCKS5/HTTP), true for VPN mode
            v2rayPoint = Libv2ray.newV2RayPoint(supportsSet, false)
            
            if (v2rayPoint == null) {
                LogManager.e(TAG, "V2RayPoint is NULL!")
                LogManager.flush()
                return false
            }
            
            LogManager.d(TAG, "V2RayPoint created OK")
            
            // Set config
            v2rayPoint!!.configureFileContent = configJson
            v2rayPoint!!.domainName = "RaccoonVPN"
            
            LogManager.i(TAG, "Starting runLoop in background thread...")
            LogManager.flush()
            
            // runLoop is BLOCKING - run in separate thread
            runThread = Thread {
                try {
                    LogManager.i(TAG, "runLoop() STARTING")
                    v2rayPoint?.runLoop()
                    LogManager.i(TAG, "runLoop() EXITED")
                } catch (e: Throwable) {
                    LogManager.e(TAG, "runLoop() CRASHED", e)
                }
            }.apply {
                name = "XrayRunLoop"
                isDaemon = true
                start()
            }
            
            // Wait a bit to check if it started
            Thread.sleep(500)
            
            if (runThread?.isAlive == true) {
                isRunning = true
                LogManager.i(TAG, "=== XrayWrapper.start() SUCCESS ===")
                LogManager.flush()
                return true
            } else {
                LogManager.e(TAG, "runLoop thread died immediately!")
                LogManager.flush()
                return false
            }
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "=== XrayWrapper.start() FAILED ===", e)
            LogManager.flush()
            return false
        }
    }
    
    fun stop() {
        if (!isRunning && v2rayPoint == null) return
        
        LogManager.i(TAG, "XrayWrapper.stop()")
        
        try {
            v2rayPoint?.stopLoop()
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error stopping", e)
        }
        
        runThread?.interrupt()
        runThread = null
        
        v2rayPoint = null
        isRunning = false
        currentConfig = null
        
        LogManager.i(TAG, "Xray stopped")
        LogManager.flush()
    }
    
    fun isRunning(): Boolean = isRunning
}
