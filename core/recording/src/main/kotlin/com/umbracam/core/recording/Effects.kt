package com.umbracam.core.recording

/** Stable contracts for future live and post-recording effects. No capture implementation lives here. */
enum class FilterId { NATURAL, WARM, COOL, MONOCHROME, CINEMATIC, VINTAGE }
data class FilterConfig(val id: FilterId, val intensity: Float = 1f)
interface FilterDiscovery { fun available(): List<FilterId> }
interface FilterPreview { fun preview(config: FilterConfig, source: Any): Any }
interface FilterApplication { fun apply(config: FilterConfig, source: Any, destination: Any): Result<Unit> }
interface EffectCompatibility { fun supports(config: FilterConfig, sourceKind: String): Boolean }
interface EffectPresetStore { fun save(name: String, config: FilterConfig); fun load(name: String): FilterConfig? }
