package com.umbracam.core.recording

/** Stable identifiers shared by post-recording filter selection and export. */
enum class FilterId { NATURAL, WARM, COOL, MONOCHROME, CINEMATIC, VINTAGE }
data class FilterConfig(val id: FilterId, val intensity: Float = 1f)
data class FilterTone(val red: Float, val green: Float, val blue: Float, val monochrome: Boolean = false)

/** Pure deterministic tone parameters. Intensity is clamped to [0, 1]. */
fun FilterConfig.tone(): FilterTone {
    val amount = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 1f
    return when (id) {
        FilterId.NATURAL -> FilterTone(1f, 1f, 1f)
        FilterId.WARM -> FilterTone(1f + .12f * amount, 1f + .025f * amount, 1f - .12f * amount)
        FilterId.COOL -> FilterTone(1f - .08f * amount, 1f + .01f * amount, 1f + .12f * amount)
        FilterId.MONOCHROME -> FilterTone(1f, 1f, 1f, true)
        FilterId.CINEMATIC -> FilterTone(1f - .04f * amount, 1f, 1f + .07f * amount)
        FilterId.VINTAGE -> FilterTone(1f + .13f * amount, 1f + .035f * amount, 1f - .16f * amount)
    }
}
object BuiltInFilterDiscovery : FilterDiscovery {
    override fun available() = FilterId.entries
}
interface FilterDiscovery { fun available(): List<FilterId> }
interface FilterPreview { fun preview(config: FilterConfig, source: Any): Any }
interface FilterApplication { fun apply(config: FilterConfig, source: Any, destination: Any): Result<Unit> }
interface EffectCompatibility { fun supports(config: FilterConfig, sourceKind: String): Boolean }
interface EffectPresetStore { fun save(name: String, config: FilterConfig); fun load(name: String): FilterConfig? }
