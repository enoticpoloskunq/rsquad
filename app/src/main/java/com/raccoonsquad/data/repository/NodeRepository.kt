package com.raccoonsquad.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raccoonsquad.data.model.*
import com.raccoonsquad.data.parser.UriParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raccoon_nodes")

class NodeRepository(private val context: Context) {
    
    private val nodesKey = stringPreferencesKey("saved_nodes")
    private val activeNodeKey = stringPreferencesKey("active_node_uuid")
    
    val nodes: Flow<List<VlessConfig>> = context.dataStore.data.map { prefs ->
        val nodesJson = prefs[nodesKey] ?: "[]"
        parseNodesFromJson(nodesJson)
    }
    
    val activeNodeUuid: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[activeNodeKey]
    }
    
    suspend fun addNode(config: VlessConfig) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            
            // Check for duplicate UUID
            var exists = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getJSONObject(i).getString("uuid") == config.uuid) {
                    exists = true
                    break
                }
            }
            
            if (!exists) {
                jsonArray.put(configToJson(config))
                prefs[nodesKey] = jsonArray.toString()
            }
        }    }
    
    suspend fun addNodes(configs: List<VlessConfig>) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            
            // Get existing UUIDs
            val existingUuids = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                existingUuids.add(jsonArray.getJSONObject(i).getString("uuid"))
            }
            
            // Add only new nodes
            configs.forEach { config ->
                if (!existingUuids.contains(config.uuid)) {
                    jsonArray.put(configToJson(config))
                    existingUuids.add(config.uuid)
                }
            }
            
            prefs[nodesKey] = jsonArray.toString()
        }
    }
    
    suspend fun deleteNode(uuid: String) {
        context.dataStore.edit { prefs ->
            val currentNodes = prefs[nodesKey] ?: "[]"
            val jsonArray = JSONArray(currentNodes)
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val node = jsonArray.getJSONObject(i)
                if (node.getString("uuid") != uuid) {
                    newArray.put(node)
                }
            }
            prefs[nodesKey] = newArray.toString()
            
            // Clear active if this was the active node
            if (prefs[activeNodeKey] == uuid) {
                prefs.remove(activeNodeKey)
            }
        }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[nodesKey] = "[]"
            prefs.remove(activeNodeKey)
        }    }
    
    suspend fun setActiveNode(uuid: String?) {
        context.dataStore.edit { prefs ->
            if (uuid != null) {
                prefs[activeNodeKey] = uuid
            } else {
                prefs.remove(activeNodeKey)
            }
        }
    }
    
    fun parseMultiple(uriText: String): List<VlessConfig> {
        return UriParser.parseMultiple(uriText)
    }
    
    fun importFromUri(uri: String): VlessConfig? {
        return UriParser.parse(uri)
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
            // ✅ Читаем через legacy-геттеры (они работают на чтение)
            put("fragmentationPackets", config.fragmentationPackets)
            put("fragmentationLength", config.fragmentationLength)            put("fragmentationInterval", config.fragmentationInterval)
            put("noiseEnabled", config.noiseEnabled)
            put("noiseType", config.noiseType)
            put("noisePacketCount", config.noisePacketCount)
            put("tcpFastOpen", config.tcpFastOpen)
            put("tcpNoDelay", config.tcpNoDelay)
            put("tcpKeepAlive", config.tcpKeepAlive)
            put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
            put("mtu", config.mtu)
        }
    }
    
    private fun jsonToConfig(obj: JSONObject): VlessConfig {
        return VlessConfig(
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
            // ✅ FIXED: используем РЕАЛЬНЫЕ имена параметров конструктора VlessConfig
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
            mtu = obj.optString("mtu", "default")
        )
    }
}
