package com.raccoonsquad.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import com.raccoonsquad.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Backup & Export functionality
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long,
    val appName: String = "Raccoon Squad",
    val appVersion: String,
    val nodes: List<NodeBackup>,
    val settings: SettingsBackup,
    val groups: List<GroupBackup>
)

@Serializable
data class NodeBackup(
    val id: String,
    val name: String,
    val serverAddress: String,
    val port: Int,
    val uuid: String,
    val securityMode: String,
    val sni: String,
    val fingerprint: String,
    val flow: String?,
    val isFavorite: Boolean,
    val latency: Long?,
    val fragmentationEnabled: Boolean,
    val fragmentType: String,
    val fragmentPackets: String,
    val fragmentLength: String,
    val fragmentInterval: String,
    val noiseEnabled: Boolean,
    val noiseType: String,
    val noisePacketSize: String,
    val noiseDelay: String,
    val mtu: String
)

@Serializable
data class SettingsBackup(
    val theme: String,
    val developerMode: Boolean,
    val killSwitch: Boolean,
    val autoReconnect: Boolean
)

@Serializable
data class GroupBackup(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val nodeIds: Set<String>
)

object BackupManager {
    private const val TAG = "BackupManager"
    private const val BACKUP_VERSION = 1
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun createBackup(
        nodes: List<VlessConfig>,
        settings: SettingsManager,
        appVersion: String
    ): BackupData {
        val nodeBackups = nodes.map { config ->
            NodeBackup(
                id = config.id,
                name = config.name,
                serverAddress = config.serverAddress,
                port = config.port,
                uuid = config.uuid,
                securityMode = config.securityMode.name,
                sni = config.sni,
                fingerprint = config.fingerprint,
                flow = if (config.flow != FlowMode.NONE) config.flow.name else null,
                isFavorite = config.isFavorite,
                latency = config.latency,
                fragmentationEnabled = config.fragmentationEnabled,
                fragmentType = config.fragmentType,
                fragmentPackets = config.fragmentPackets,
                fragmentLength = config.fragmentLength,
                fragmentInterval = config.fragmentInterval,
                noiseEnabled = config.noiseEnabled,
                noiseType = config.noiseType,
                noisePacketSize = config.noisePacketSize,
                noiseDelay = config.noiseDelay,
                mtu = config.mtu
            )
        }
        
        val settingsBackup = SettingsBackup(
            theme = "PURPLE",
            developerMode = false,
            killSwitch = true,
            autoReconnect = true
        )
        
        return BackupData(
            version = BACKUP_VERSION,
            timestamp = System.currentTimeMillis(),
            appVersion = appVersion,
            nodes = nodeBackups,
            settings = settingsBackup,
            groups = emptyList()
        )
    }
    
    suspend fun exportBackup(
        context: Context,
        backupData: BackupData
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "raccoon_backup_${dateFormat.format(Date(backupData.timestamp))}.json"
            
            val backupDir = File(context.cacheDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            
            val backupFile = File(backupDir, fileName)
            backupFile.writeText(json.encodeToString(backupData))
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )
            
            LogManager.i(TAG, "Backup created: ${backupFile.absolutePath}")
            Result.success(uri)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to create backup", e)
            Result.failure(e)
        }
    }
    
    suspend fun importBackup(
        context: Context,
        uri: Uri
    ): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open file")
            
            val content = inputStream.bufferedReader().readText()
            inputStream.close()
            
            val backupData = json.decodeFromString<BackupData>(content)
            
            if (backupData.version > BACKUP_VERSION) {
                throw IllegalArgumentException("Backup version ${backupData.version} is newer than supported ($BACKUP_VERSION)")
            }
            
            LogManager.i(TAG, "Backup imported: ${backupData.nodes.size} nodes")
            Result.success(backupData)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to import backup", e)
            Result.failure(e)
        }
    }
    
    fun restoreNodes(backupData: BackupData): List<VlessConfig> {
        return backupData.nodes.map { backup ->
            VlessConfig(
                id = backup.id,
                name = backup.name,
                serverAddress = backup.serverAddress,
                port = backup.port,
                uuid = backup.uuid,
                securityMode = try { SecurityMode.valueOf(backup.securityMode) } catch(e: Exception) { SecurityMode.REALITY },
                sni = backup.sni,
                fingerprint = backup.fingerprint,
                flow = backup.flow?.let { try { FlowMode.valueOf(it) } catch(e: Exception) { FlowMode.XTLS_RPRX_VISION } } ?: FlowMode.XTLS_RPRX_VISION,
                fragmentationEnabled = backup.fragmentationEnabled,
                fragmentType = backup.fragmentType,
                fragmentPackets = backup.fragmentPackets,
                fragmentLength = backup.fragmentLength,
                fragmentInterval = backup.fragmentInterval,
                noiseEnabled = backup.noiseEnabled,
                noiseType = backup.noiseType,
                noisePacketSize = backup.noisePacketSize,
                noiseDelay = backup.noiseDelay,
                mtu = backup.mtu,
                isFavorite = backup.isFavorite,
                latency = backup.latency
            )
        }
    }
    
    fun exportNodesAsVless(nodes: List<VlessConfig>): String {
        // Simple VLESS URL export
        return nodes.map { config ->
            buildVlessUrl(config)
        }.joinToString("\n")
    }
    
    private fun buildVlessUrl(config: VlessConfig): String {
        val flowParam = if (config.flow != FlowMode.NONE) "&flow=${config.flow.name.lowercase().replace("_", "-")}" else ""
        return "vless://${config.uuid}@${config.serverAddress}:${config.port}?security=${config.securityMode.name.lowercase()}&sni=${config.sni}&fp=${config.fingerprint}&type=tcp$flowParam#${config.name}"
    }
    
    fun exportNodesAsClash(nodes: List<VlessConfig>): String {
        val proxies = nodes.mapIndexed { index, config ->
            """
            - name: "${config.getDisplayName()}"
              type: vless
              server: ${config.serverAddress}
              port: ${config.port}
              uuid: ${config.uuid}
              network: tcp
              tls: true
              flow: ${if (config.flow != FlowMode.NONE) config.flow.name.lowercase().replace("_", "-") else ""}
              servername: ${config.sni.ifEmpty { config.serverAddress }}
            """.trimIndent()
        }.joinToString("\n")
        
        return """
        proxies:
        $proxies
        
        proxy-groups:
          - name: "Auto"
            type: url-test
            proxies:
              - ${nodes.firstOrNull()?.getDisplayName() ?: "None"}
            url: 'http://www.gstatic.com/generate_204'
            interval: 300
        """.trimIndent()
    }
    
    fun shareBackup(context: Context, uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Raccoon Squad Backup")
            putExtra(Intent.EXTRA_TEXT, "Backup from Raccoon Squad VPN")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share Backup"))
    }
    
    // QR Code generation (returns content for QR)
    fun generateQrContent(config: VlessConfig): String {
        return buildVlessUrl(config)
    }
    
    // Generate subscription link content
    fun generateSubscriptionContent(nodes: List<VlessConfig>): String {
        return nodes.map { buildVlessUrl(it) }
            .joinToString("\n")
            .let { android.util.Base64.encodeToString(it.toByteArray(), android.util.Base64.DEFAULT) }
    }
}
