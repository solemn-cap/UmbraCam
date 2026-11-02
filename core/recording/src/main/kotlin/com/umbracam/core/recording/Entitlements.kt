package com.umbracam.core.recording

/** Product access tiers. Billing is intentionally outside this milestone. */
enum class EntitlementTier { FREE, PRO, CLOUD }

/** Centralized, deterministic access provider used by feature gates. */
interface EntitlementProvider {
    fun tier(): EntitlementTier
    fun has(feature: PremiumFeature): Boolean
}

/** Local provider. Debug configuration may grant Pro; it never represents a billing entitlement. */
class DevelopmentEntitlementProvider(
    private val currentTier: EntitlementTier = EntitlementTier.FREE
) : EntitlementProvider {
    override fun tier() = currentTier
    override fun has(feature: PremiumFeature) = when (feature) {
        PremiumFeature.CORE_RECORDING, PremiumFeature.SOS_RECORDING -> true
        PremiumFeature.PROFESSIONAL_RECORDING, PremiumFeature.CREATOR_TOOLS,
        PremiumFeature.FILTERS_EFFECTS, PremiumFeature.REACTION_PRO -> currentTier == EntitlementTier.PRO
        PremiumFeature.AI_STUDIO, PremiumFeature.ADVANCED_PROTECTION -> false
    }
}

enum class PremiumFeature { CORE_RECORDING, SOS_RECORDING, PROFESSIONAL_RECORDING, CREATOR_TOOLS, FILTERS_EFFECTS, REACTION_PRO, AI_STUDIO, ADVANCED_PROTECTION }

data class PremiumFeatureInfo(val feature: PremiumFeature, val title: String, val description: String)

val premiumFeatureCatalog = listOf(
    PremiumFeatureInfo(PremiumFeature.PROFESSIONAL_RECORDING, "Professional recording", "Advanced quality, camera controls and export presets."),
    PremiumFeatureInfo(PremiumFeature.CREATOR_TOOLS, "Creator tools", "Teleprompter and reusable Reaction layouts; more tools are planned."),
    PremiumFeatureInfo(PremiumFeature.FILTERS_EFFECTS, "Filters & effects", "Deterministic color filters and non-destructive filtered copies."),
    PremiumFeatureInfo(PremiumFeature.REACTION_PRO, "Reaction Studio Pro", "Independent tracks, layouts and audio mixing."),
    PremiumFeatureInfo(PremiumFeature.AI_STUDIO, "AI Studio", "Captions, transcription and highlight tools when implemented."),
    PremiumFeatureInfo(PremiumFeature.ADVANCED_PROTECTION, "Advanced protection", "Encryption, backup and integrity features when implemented.")
)
