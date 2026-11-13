package app.nodx.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class DeviceTier(val label: String) {
    ULTRA("ultra"), HIGH("high"), MID("mid"), LOW("low");

    /** Recommended KV-cache context for the tier. */
    val recommendedContext: Int
        get() = when (this) {
            ULTRA -> 8192
            HIGH -> 4096
            MID -> 3072
            LOW -> 2048
        }

    /** Recommended CPU threads (arm64 big-core count approximation). */
    val recommendedThreads: Int
        get() = when (this) {
            ULTRA -> 6
            HIGH -> 5
            MID -> 4
            LOW -> 4
        }
}

data class GpuInfo(
    val name: String,
    val recommendedLayers: Int,
)

/** Device-tier auto-configuration, ported from the reference client's
 * DeviceInfoService: RAM bucketing decides context size and threads;
 * Vulkan probe decides GPU layers. */
class DeviceProfile(private val context: Context) {

    val totalRamGb: Double by lazy {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        mi.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    val tier: DeviceTier by lazy {
        when {
            totalRamGb >= 11 -> DeviceTier.ULTRA
            totalRamGb >= 7 -> DeviceTier.HIGH
            totalRamGb >= 5 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    val is64Bit: Boolean get() = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    /** Largest model file size this tier should attempt, in GB. */
    val maxModelGb: Double
        get() = when (tier) {
            DeviceTier.ULTRA -> 6.0
            DeviceTier.HIGH -> 4.0
            DeviceTier.MID -> 2.5
            DeviceTier.LOW -> 1.6
        }

    override fun toString(): String =
        "DeviceTier(${tier.label}, ${"%.1f".format(totalRamGb)}GB RAM, 64bit=$is64Bit)"
}

/** Parses the GPU JSON emitted by the native detectGpu() call. */
fun parseGpuInfo(json: String): GpuInfo? = try {
    val obj = Json.parseToJsonElement(json).jsonObject
    GpuInfo(
        name = obj["name"]?.jsonPrimitive?.content ?: "GPU",
        recommendedLayers = obj["recommendedGpuLayers"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: obj["recommended_gpu_layers"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: 0,
    )
} catch (_: Exception) {
    null
}
