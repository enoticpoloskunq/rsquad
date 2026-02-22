package com.raccoonsquad.core.xray

import android.content.Context
import android.util.Log
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wrapper for Xray-core via libv2ray.aar
 * 
 * Provides Kotlin-friendly API for Xray core operations:
 * - Start/Stop VPN proxy
 * - Generate Xray config from VlessConfig
 * - Measure latency
 * - Query traffic stats
 */
object XrayWrapper {
    
    private const val TAG = "XrayWrapper"
    
    // libv2ray controller
    private var coreController: libv2ray.CoreController? = null
    private var callbackHandler: XrayCallbackHandler? = null
    
    // State
    private var isRunning = false
    private var currentConfig: String? = null
    private var appContext: Context? = null
    
    /**
     * Initialize Xray wrapper with application context
     * Must be called before any other methods
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        
        // Initialize libv2ray environment
        val filesDir = context.filesDir.absolutePath
        
        // Create geo data directory
        val geoDir = File(filesDir, "xray")
        if (!geoDir.exists()) {
            geoDir.mkdirs()
        }
        
        // Initialize core environment (asset path, key)
        Libv2ray.initCoreEnv(geoDir.absolutePath, "")
        
        Log.i(TAG, "Xray wrapper initialized, version: ${Libv2ray.checkVersionX()}")
    }
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        val json = JSONObject()
        
        // Log
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        json.put("log", log)
        
        // Inbounds - SOCKS5 and HTTP proxy
        val inbounds = JSONArray()
        
        // SOCKS5 inbound (for VPN tunnel)
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("udp", true)
                put("auth", "noauth")
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("routeOnly", false)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        })
        
        // HTTP inbound (alternative)
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        })
        
        // TUN inbound (if using tunFd)
        inbounds.put(JSONObject().apply {
            put("tag", "tun-in")
            put("port", 10810)
            put("listen", "127.0.0.1")
            put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("network", "tcp,udp")
                put("followRedirect", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("routeOnly", false)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
            put("streamSettings", JSONObject().apply {
                put("sockopt", JSONObject().apply {
                    put("tproxy", "tun")
                })
            })
        })
        
        json.put("inbounds", inbounds)
        
        // Outbounds
        val outbounds = JSONArray()
        
        // VLESS outbound
        val vlessOut = buildVlessOutbound(config)
        outbounds.put(vlessOut)
        
        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        
        // Block outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply {
                    put("type", "http")
                })
            })
        })
        
        // DNS outbound (for DNS queries)
        outbounds.put(JSONObject().apply {
            put("tag", "dns-out")
            put("protocol", "dns")
        })
        
        json.put("outbounds", outbounds)
        
        // DNS
        val dns = JSONObject().apply {
            put("hosts", JSONObject().apply {
                // Block malware domains (optional)
            })
            put("servers", JSONArray().apply {
                put("https://1.1.1.1/dns-query")
                put("https://8.8.8.8/dns-query")
                put(JSONObject().apply {
                    put("address", "223.5.5.5")
                    put("domains", JSONArray().put("geosite:cn"))
                    put("expectIPs", JSONArray().put("geoip:cn"))
                })
            })
        }
        json.put("dns", dns)
        
        // Routing
        val routing = buildRouting()
        json.put("routing", routing)
        
        // Policy
        val policy = JSONObject().apply {
            put("levels", JSONObject().apply {
                put("0", JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 2)
                    put("downlinkOnly", 5)
                    put("bufferSize", 10240)
                })
            })
            put("system", JSONObject().apply {
                put("statsInboundUplink", true)
                put("statsInboundDownlink", true)
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        }
        json.put("policy", policy)
        
        return json.toString(2)
    }
    
    private fun buildVlessOutbound(config: VlessConfig): JSONObject {
        return JSONObject().apply {
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
            put("streamSettings", buildStreamSettings(config))
        }
    }
    
    private fun buildStreamSettings(config: VlessConfig): JSONObject {
        return JSONObject().apply {
            put("network", "tcp")
            put("security", when(config.securityMode) {
                SecurityMode.REALITY -> "reality"
                SecurityMode.TLS -> "tls"
                SecurityMode.NONE -> "none"
            })
            
            // Reality settings
            if (config.securityMode == SecurityMode.REALITY) {
                put("realitySettings", JSONObject().apply {
                    put("show", false)
                    put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                    if (config.sni.isNotEmpty()) {
                        put("serverName", config.sni)
                    }
                    if (config.realityPublicKey.isNotEmpty()) {
                        put("publicKey", config.realityPublicKey)
                    }
                    if (config.realityShortId.isNotEmpty()) {
                        put("shortId", config.realityShortId)
                    }
                    if (config.realitySpiderX.isNotEmpty()) {
                        put("spiderX", config.realitySpiderX)
                    }
                })
            } 
            // TLS settings
            else if (config.securityMode == SecurityMode.TLS) {
                put("tlsSettings", JSONObject().apply {
                    put("allowInsecure", false)
                    if (config.sni.isNotEmpty()) {
                        put("serverName", config.sni)
                    }
                    put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                })
            }
            
            // Socket options with fragmentation
            put("sockopt", buildSocketOptions(config))
        }
    }
    
    private fun buildSocketOptions(config: VlessConfig): JSONObject {
        return JSONObject().apply {
            // TCP options
            if (config.tcpFastOpen) {
                put("tcpFastOpen", true)
            }
            if (config.tcpNoDelay) {
                put("tcpNoDelay", true)
            }
            if (config.tcpKeepAliveInterval > 0) {
                put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
            }
            
            // Fragmentation settings (key feature!)
            if (config.fragmentationEnabled) {
                put("dialer", JSONObject().apply {
                    put("domainStrategy", "AsIs")
                    
                    // Build fragment parameters based on format
                    val fragParams = JSONObject().apply {
                        put("packets", when(config.fragmentType) {
                            "tlshello" -> "tlshello"
                            else -> config.fragmentPackets.ifEmpty { "1-3" }
                        })
                        put("length", config.fragmentLength.ifEmpty { "10-20" })
                        put("interval", config.fragmentInterval.ifEmpty { "10-20" })
                    }
                    put("fragment", fragParams)
                })
            }
            
            // Noise settings (for packet obfuscation)
            if (config.noiseEnabled) {
                put("dialer", (opt("dialer") as? JSONObject ?: JSONObject()).apply {
                    put("domainStrategy", "AsIs")
                    put("noise", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "rand")
                            put("packet", config.noisePacketSize.ifEmpty { "10-20" })
                            put("delay", config.noiseDelay.ifEmpty { "10-20" })
                        })
                    })
                })
            }
        }
    }
    
    private fun buildRouting(): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // DNS queries -> DNS outbound
                put(JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().put("dns-in"))
                    put("outboundTag", "dns-out")
                })
                
                // Block private IPs from going through proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                
                // China sites -> direct (optional, can be configured)
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().put("geosite:cn"))
                    put("outboundTag", "direct")
                })
                
                // China IPs -> direct
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:cn"))
                    put("outboundTag", "direct")
                })
                
                // Default -> proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                })
            })
        }
    }
    
    /**
     * Start Xray with config
     * @param configJson Xray JSON config
     * @param tunFd TUN file descriptor (0 = use SOCKS proxy instead)
     * @return true if started successfully
     */
    fun start(configJson: String, tunFd: Int = 0): Boolean {
        if (isRunning) {
            Log.w(TAG, "Xray already running, stopping first")
            stop()
        }
        
        try {
            currentConfig = configJson
            
            // Create callback handler
            callbackHandler = XrayCallbackHandler().apply {
                onStarted = {
                    Log.i(TAG, "Xray core started callback")
                }
                onStopped = {
                    Log.i(TAG, "Xray core stopped callback")
                    isRunning = false
                }
                onError = { msg ->
                    Log.e(TAG, "Xray error: $msg")
                }
            }
            
            // Create core controller
            coreController = libv2ray.Libv2ray.newCoreController(callbackHandler!!)
            
            // Start core with config
            val error = coreController!!.startLoop(configJson, tunFd.toLong())
            if (error != null) {
                Log.e(TAG, "Failed to start Xray: ${error.message}")
                return false
            }
            
            isRunning = true
            Log.i(TAG, "Xray started successfully")
            Log.d(TAG, "Config preview: ${configJson.take(200)}...")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray", e)
            return false
        }
    }
    
    /**
     * Stop Xray
     */
    fun stop() {
        if (!isRunning) return
        
        try {
            coreController?.stopLoop()
            coreController = null
            callbackHandler = null
            isRunning = false
            currentConfig = null
            Log.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Xray", e)
        }
    }
    
    /**
     * Check if Xray is running
     */
    fun isRunning(): Boolean {
        return isRunning && (coreController?.isRunning ?: false)
    }
    
    /**
     * Get current config
     */
    fun getCurrentConfig(): String? = currentConfig
    
    /**
     * Measure latency to test URL
     * @param url Test URL (default: Google generate_204)
     * @return Latency in ms, or -1 on error
     */
    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long {
        if (!isRunning) {
            return -1
        }
        
        return try {
            coreController?.measureDelay(url) ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure delay", e)
            -1
        }
    }
    
    /**
     * Measure outbound delay (tests config before connecting)
     * @param configJson Xray config to test
     * @param url Test URL
     * @return Latency in ms, or -1 on error
     */
    fun measureOutboundDelay(configJson: String, url: String = "https://www.google.com/generate_204"): Long {
        return try {
            Libv2ray.measureOutboundDelay(configJson, url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure outbound delay", e)
            -1
        }
    }
    
    /**
     * Query traffic stats
     * @param tag Outbound tag (e.g., "proxy")
     * @param direction "uplink" or "downlink"
     * @return Traffic bytes since last query (resets counter)
     */
    fun queryStats(tag: String = "proxy", direction: String = "downlink"): Long {
        if (!isRunning) return 0
        
        return try {
            coreController?.queryStats(tag, direction) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query stats", e)
            0
        }
    }
    
    /**
     * Get Xray version
     */
    fun getVersion(): String {
        return Libv2ray.checkVersionX()
    }
}
