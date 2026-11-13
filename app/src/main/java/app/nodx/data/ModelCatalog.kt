package app.nodx.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One curated downloadable GGUF model. */
@Serializable
data class CatalogModel(
    val id: String,
    val name: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long,
    val minRamGb: Double,
    val paramsBn: Double,
    val quant: String,
    val description: String = "",
)

/** Bundled catalog of recommended GGUF models (Phase 4). Loaded from
 * assets/models.json so it can evolve without code changes. */
class ModelCatalog(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val models: List<CatalogModel> by lazy {
        runCatching {
            val text = context.assets.open("models.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<CatalogModel>>(text)
        }.getOrDefault(emptyList())
    }

    /** Catalog entries that fit the device's RAM tier, best first. */
    fun forDevice(maxGb: Double): List<CatalogModel> =
        models.filter { it.minRamGb <= maxGb }.sortedBy { it.minRamGb }
}
