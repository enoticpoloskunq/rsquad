package com.raccoonsquad.core.cosmetic

import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.FlowMode
import kotlin.random.Random

/**
 * Cosmetic presets for bypassing censorship
 */
data class CosmeticPreset(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val apply: (VlessConfig) -> VlessConfig
)

object CosmeticPresets {
    
    val presets = listOf(
        CosmeticPreset(
            id = "minimal",
            name = "Минимальная",
            description = "Базовая конфигурация без маскировки",
            emoji = "🛡️"
        ) { config ->
            config.copy(
                fragmentationEnabled = false,
                noiseEnabled = false,
                tcpFastOpen = false,
                tcpNoDelay = true,
                flow = FlowMode.NONE,
                mtu = "1500"
            )
        },
        
        CosmeticPreset(
            id = "maximum",
            name = "Максимальный обход",
            description = "Максимальная маскировка для строгой цензуры",
            emoji = "🔥"
        ) { config ->
            config.copy(
                fragmentationEnabled = true,
                fragmentPackets = "tlshello",
                fragmentLength = "40-60",
                noiseEnabled = true,
                noiseType = "random",
                noisePacketSize = "5-10",
                tcpFastOpen = true,
                tcpNoDelay = true,
                tcpKeepAlive = true,
                tcpKeepAliveInterval = 30,
                flow = FlowMode.XTLS_RPRX_VISION,
                mtu = "1350"
            )
        },
        
        CosmeticPreset(
            id = "mobile",
            name = "Для мобильных",
            description = "Оптимизировано для мобильных сетей",
            emoji = "📱"
        ) { config ->
            config.copy(
                fragmentationEnabled = true,
                fragmentPackets = "1-2",
                fragmentLength = "50-100",
                noiseEnabled = false,
                tcpFastOpen = true,
                tcpNoDelay = true,
                tcpKeepAlive = true,
                tcpKeepAliveInterval = 15,
                flow = FlowMode.NONE,
                mtu = "1280"
            )
        },
        
        CosmeticPreset(
            id = "tspu",
            name = "Против ТСПУ",
            description = "Настройки для обхода DPI России",
            emoji = "🚫"
        ) { config ->
            config.copy(
                fragmentationEnabled = true,
                fragmentPackets = "1-3",
                fragmentLength = "10-20",
                noiseEnabled = true,
                noiseType = "random",
                noisePacketSize = "3-5",
                tcpFastOpen = true,
                tcpNoDelay = true,
                tcpKeepAlive = true,
                tcpKeepAliveInterval = 10,
                flow = FlowMode.XTLS_RPRX_VISION,
                mtu = "1280"
            )
        }
    )
    
    /**
     * Generate random cosmetic settings to avoid pattern detection
     */
    fun randomize(config: VlessConfig): VlessConfig {
        val random = Random.Default
        
        // Random fragment settings
        val fragmentPacketsOptions = listOf("1-1", "1-2", "1-3", "tlshello")
        val fragmentLengthOptions = listOf("10-30", "20-50", "30-60", "40-80")
        val noisePacketOptions = listOf("3-8", "5-10", "5-15", "8-20")
        val noiseTypeOptions = listOf("random", "base64")
        val mtuOptions = listOf("1280", "1350", "1400")
        
        val useFragment = random.nextBoolean()
        val useNoise = random.nextBoolean()
        
        return config.copy(
            fragmentationEnabled = useFragment,
            fragmentPackets = if (useFragment) fragmentPacketsOptions.random() else "1-3",
            fragmentLength = if (useFragment) fragmentLengthOptions.random() else "10-20",
            fragmentInterval = if (useFragment) "${random.nextInt(5, 20)}-${random.nextInt(20, 50)}" else "10-20",
            
            noiseEnabled = useNoise,
            noiseType = if (useNoise) noiseTypeOptions.random() else "random",
            noisePacketSize = if (useNoise) noisePacketOptions.random() else "5-10",
            noiseDelay = if (useNoise) "${random.nextInt(5, 15)}-${random.nextInt(15, 30)}" else "10-20",
            
            tcpFastOpen = random.nextBoolean(),
            tcpNoDelay = true,
            tcpKeepAlive = random.nextBoolean(),
            tcpKeepAliveInterval = listOf(10, 15, 20, 30).random(),
            
            mtu = mtuOptions.random()
        )
    }
    
    /**
     * Apply preset to config
     */
    fun applyPreset(presetId: String, config: VlessConfig): VlessConfig {
        return presets.find { it.id == presetId }?.apply?.invoke(config) ?: config
    }
    
    /**
     * Apply random cosmetics to multiple configs
     */
    fun randomizeAll(configs: List<VlessConfig>): List<VlessConfig> {
        return configs.map { randomize(it) }
    }
    
    /**
     * Apply preset to multiple configs
     */
    fun applyPresetToAll(presetId: String, configs: List<VlessConfig>): List<VlessConfig> {
        return configs.map { applyPreset(presetId, it) }
    }
}
