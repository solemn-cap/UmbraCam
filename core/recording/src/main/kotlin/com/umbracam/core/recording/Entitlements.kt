package com.umbracam.core.recording

/** Product access tiers. Billing is intentionally outside this milestone. */
enum class EntitlementTier { FREE, PRO, CLOUD }

/** Centralized, deterministic access provider used by feature gates. */
interface EntitlementProvider {
    fun tier(): EntitlementTier
    fun has(feature: PremiumFeature): Boolean
}

/** Development provider: all core recording remains free; premium is discoverable only. */
class DevelopmentEntitlementProvider(
    private val currentTier: EntitlementTier = EntitlementTier.FREE
) : EntitlementProvider {
    override fun tier() = currentTier
    override fun has(feature: PremiumFeature) = when (feature) {
        PremiumFeature.CORE_RECORDING, PremiumFeature.SOS_RECORDING -> true
        else -> currentTier == EntitlementTier.PRO
    }
}

enum class PremiumFeature { CORE_RECORDING, SOS_RECORDING, PROFESSIONAL_RECORDING, CREATOR_TOOLS, FILTERS_EFFECTS, REACTION_PRO, AI_STUDIO, ADVANCED_PROTECTION }

data class PremiumFeatureInfo(val feature: PremiumFeature, val title: String, val description: String)

val premiumFeatureCatalog = listOf(
    PremiumFeatureInfo(PremiumFeature.PROFESSIONAL_RECORDING, "Professional recording", "Advanced quality, camera controls and export presets."),
    PremiumFeatureInfo(PremiumFeature.CREATOR_TOOLS, "Creator tools", "Teleprompter, layouts, overlays and creator presets."),
    PremiumFeatureInfo(PremiumFeature.FILTERS_EFFECTS, "Filters & effects", "Deterministic color treatments and saved presets."),
    PremiumFeatureInfo(PremiumFeature.REACTION_PRO, "Reaction Studio Pro", "Independent tracks, layouts and audio mixing."),
    PremiumFeatureInfo(PremiumFeature.AI_STUDIO, "AI Studio", "Captions, transcription and highlight tools when implemented."),
    PremiumFeatureInfo(PremiumFeature.ADVANCED_PROTECTION, "Advanced protection", "Encryption, backup and integrity features when implemented.")
)
