package com.raccoonsquad.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raccoonsquad.data.model.*
import com.raccoonsquad.data.parser.UriParser
import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raccoon_nodes")

class NodeRepository(private val context: Context) {
    
    private val nodesKey = stringPreferencesKey("saved_nodes")
    private val activeNodeKey = stringPreferencesKey("active_node_id")
    
    private var cachedNodes: List<VlessConfig> = emptyList()
    
    init {
        // Log DataStore file location
        try {
            val dataStoreFile = File(context.filesDir, "datastore/raccoon_nodes.preferences_pb")
            LogManager.i(TAG, "DataStore file: ${dataStoreFile.absolutePath}, exists=${dataStoreFile.exists()}")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to check DataStore file", e)
        }
    }
    
    companion object {
        private const val TAG = "NodeRepository"
        
        @Volatile
        private var instance: NodeRepository? = null
        
        fun getInstance(context: Context): NodeRepository {
            return instance ?: synchronized(this) {
                instance ?: NodeRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    val nodes: Flow<List<VlessConfig>> = context.dataStore.data.map { prefs ->
        val nodesJson = prefs[nodesKey] ?: "[]"
        LogManager.d(TAG, "Flow: reading nodes, JSON length=${nodesJson.length}")
        val parsed = parseNodesFromJson(nodesJson)
        cachedNodes = parsed
        LogManager.i(TAG, "Flow: emitting ${parsed.size} nodes")
        parsed
    }
    
    val activeNodeId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[activeNodeKey]
    }
    
    suspend fun addNode(config: VlessConfig) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            
            // Check for duplicate ID
            var exists = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getJSONObject(i).getString("id") == config.id) {
                    exists = true
                    break
                }
            }
            
            if (!exists) {
                jsonArray.put(configToJson(config))
                prefs[nodesKey] = jsonArray.toString()
            }
        }
    }
    
    suspend fun addNodes(configs: List<VlessConfig>) {
        LogManager.i(TAG, "=== addNodes() START: adding ${configs.size} nodes ===")
        
        try {
            context.dataStore.edit { prefs ->
                val currentNodes = prefs[nodesKey] ?: "[]"
                LogManager.d(TAG, "Current nodes JSON length: ${currentNodes.length}")
                
                val jsonArray = JSONArray(currentNodes)
                val initialCount = jsonArray.length()
                LogManager.d(TAG, "Initial count: $initialCount")
                
                // Add all nodes
                var addedCount = 0
                configs.forEach { config ->
                    try {
                        val json = configToJson(config)
                        jsonArray.put(json)
                        addedCount++
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Failed to serialize config: ${config.name}", e)
                    }
                }
                
                val finalJson = jsonArray.toString()
                prefs[nodesKey] = finalJson
                LogManager.i(TAG, "Saved JSON length: ${finalJson.length}")
                LogManager.i(TAG, "=== addNodes() SUCCESS: $initialCount -> ${jsonArray.length()} nodes ($addedCount added) ===")
            }
            
            // Verify after save
            LogManager.d(TAG, "Verifying save...")
            val dataStoreFile = File(context.filesDir, "datastore/raccoon_nodes.preferences_pb")
            LogManager.i(TAG, "DataStore file exists: ${dataStoreFile.exists()}, size: ${if (dataStoreFile.exists()) dataStoreFile.length() else 0}")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "=== addNodes() FAILED ===", e)
            throw e
        }
    }
    
    suspend fun deleteNode(id: String) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val node = jsonArray.getJSONObject(i)
                if (node.getString("id") != id) {
                    newArray.put(node)
                }
            }
            prefs[nodesKey] = newArray.toString()
            
            // Clear active if this was the active node
            if (prefs[activeNodeKey] == id) {
                prefs.remove(activeNodeKey)
            }
        }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[nodesKey] = "[]"
            prefs.remove(activeNodeKey)
        }
    }
    
    suspend fun setActiveNode(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) {
                prefs[activeNodeKey] = id
            } else {
                prefs.remove(activeNodeKey)
            }
        }
    }
    
    suspend fun updateNode(config: VlessConfig) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            val newArray = JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val node = jsonArray.getJSONObject(i)
                if (node.getString("id") == config.id) {
                    newArray.put(configToJson(config))
                } else {
                    newArray.put(node)
                }
            }
            
            prefs[nodesKey] = newArray.toString()
        }
    }
    
    suspend fun toggleFavorite(id: String) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            val newArray = JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val node = jsonArray.getJSONObject(i)
                if (node.getString("id") == id) {
                    val config = jsonToConfig(node)
                    val updated = config.copy(isFavorite = !config.isFavorite)
                    newArray.put(configToJson(updated))
                } else {
                    newArray.put(node)
                }
            }
            
            prefs[nodesKey] = newArray.toString()
        }
    }
    
    fun getNodesSync(): List<VlessConfig> {
        return cachedNodes
    }
    
    fun parseMultiple(uriText: String): List<VlessConfig> {
        return UriParser.parseMultiple(uriText)
    }
    
    private fun parseNodesFromJson(json: String): List<VlessConfig> {
        val nodes = mutableListOf<VlessConfig>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                nodes.add(jsonToConfig(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return nodes
    }
    
    private fun configToJson(config: VlessConfig): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("uuid", config.uuid)
            put("serverAddress", config.serverAddress)
            put("port", config.port)
            put("name", config.name)
            put("securityMode", config.securityMode.name)
            put("sni", config.sni)
            put("realityPublicKey", config.realityPublicKey)
            put("realityShortId", config.realityShortId)
            put("realitySpiderX", config.realitySpiderX)
            put("flow", config.flow.name)
            put("fragmentationEnabled", config.fragmentationEnabled)
            put("fragmentationPackets", config.fragmentationPackets)
            put("fragmentationLength", config.fragmentationLength)
            put("fragmentationInterval", config.fragmentationInterval)
            put("noiseEnabled", config.noiseEnabled)
            put("noiseType", config.noiseType)
            put("noisePacketCount", config.noisePacketCount)
            put("tcpFastOpen", config.tcpFastOpen)
            put("tcpNoDelay", config.tcpNoDelay)
            put("tcpKeepAlive", config.tcpKeepAlive)
            put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
            put("mtu", config.mtu)
            put("isFavorite", config.isFavorite)
        }
    }
    
    private fun jsonToConfig(obj: JSONObject): VlessConfig {
        return VlessConfig(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            uuid = obj.getString("uuid"),
            serverAddress = obj.getString("serverAddress"),
            port = obj.getInt("port"),
            name = obj.optString("name", "VLESS Node"),
            securityMode = SecurityMode.valueOf(obj.optString("securityMode", "REALITY")),
            sni = obj.optString("sni", ""),
            realityPublicKey = obj.optString("realityPublicKey", ""),
            realityShortId = obj.optString("realityShortId", ""),
            realitySpiderX = obj.optString("realitySpiderX", "/"),
            flow = FlowMode.valueOf(obj.optString("flow", "XTLS_RPRX_VISION")),
            fragmentationEnabled = obj.optBoolean("fragmentationEnabled", false),
            fragmentPackets = obj.optString("fragmentationPackets", "1-3"),
            fragmentLength = obj.optString("fragmentationLength", "10-20"),
            fragmentInterval = obj.optString("fragmentationInterval", "10"),
            noiseEnabled = obj.optBoolean("noiseEnabled", false),
            noiseType = obj.optString("noiseType", "random"),
            noisePacketSize = obj.optString("noisePacketCount", "5-10"),
            tcpFastOpen = obj.optBoolean("tcpFastOpen", false),
            tcpNoDelay = obj.optBoolean("tcpNoDelay", true),
            tcpKeepAlive = obj.optBoolean("tcpKeepAlive", true),
            tcpKeepAliveInterval = obj.optInt("tcpKeepAliveInterval", 30),
            mtu = obj.optString("mtu", "default"),
            isFavorite = obj.optBoolean("isFavorite", false)
        )
    }
}
