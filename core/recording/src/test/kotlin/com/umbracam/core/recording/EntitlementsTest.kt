package com.umbracam.core.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    @Test fun freeTierKeepsCoreAndSosFree() {
        val provider = DevelopmentEntitlementProvider()
        assertTrue(provider.has(PremiumFeature.CORE_RECORDING))
        assertTrue(provider.has(PremiumFeature.SOS_RECORDING))
        assertFalse(provider.has(PremiumFeature.FILTERS_EFFECTS))
    }

    @Test fun proTierUnlocksCatalogFeatures() {
        val provider = DevelopmentEntitlementProvider(EntitlementTier.PRO)
        assertTrue(premiumFeatureCatalog.all { provider.has(it.feature) })
    }
}
