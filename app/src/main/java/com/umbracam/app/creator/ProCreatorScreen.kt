package com.umbracam.app.creator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.umbracam.app.NeonPanel
import com.umbracam.app.R
import com.umbracam.app.UmbraCamApplication
import com.umbracam.core.recording.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class CreatorOffer(val name: String, val priceLabel: String? = null, val billingAvailable: Boolean = false)
fun interface CreatorOfferSource { fun offer(): CreatorOffer }
class ConfiguredCreatorOfferSource(private val current: CreatorOffer = CreatorOffer("UmbraCam Pro")) : CreatorOfferSource {
    override fun offer() = current
}

@Composable
fun ProCreatorScreen(onOpenCamera: () -> Unit, onOpenReaction: () -> Unit) {
    val app=LocalContext.current.applicationContext as UmbraCamApplication
    val context=LocalContext.current
    val preferences=remember(context) { context.getSharedPreferences("creator_tools",android.content.Context.MODE_PRIVATE) }
    var reactionLayout by remember(preferences) { mutableStateOf(preferences.getString("reaction_layout",ReactionLayout.PICTURE_IN_PICTURE.name)!!) }
    val offer=app.creatorOfferSource.offer()
    val tier=app.entitlements.tier()
    // UmbraCamApp owns the route-level scroll container for this screen.
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        NeonPanel(Modifier.fillMaxWidth()) {
            Text(offer.name,style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.pro_intro),style=MaterialTheme.typography.bodyMedium)
            Text(if(offer.priceLabel==null) stringResource(R.string.pro_pricing_unavailable) else stringResource(R.string.pro_price,offer.priceLabel),style=MaterialTheme.typography.titleSmall)
            Text(stringResource(if(offer.billingAvailable) R.string.pro_billing_configured else R.string.pro_no_checkout),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(if(tier==EntitlementTier.PRO) R.string.pro_debug_access else R.string.pro_free_tier),style=MaterialTheme.typography.labelLarge)
        }
        premiumFeatureCatalog.forEach { item ->
            val built=item.feature in setOf(PremiumFeature.CREATOR_TOOLS,PremiumFeature.FILTERS_EFFECTS)
            val available=built && app.entitlements.has(item.feature)
            NeonPanel(Modifier.fillMaxWidth()) {
                Text(item.title,style=MaterialTheme.typography.titleMedium)
                Text(item.description,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(when { available -> R.string.pro_available_in_build; built -> R.string.pro_access_pending; else -> R.string.pro_planned }),style=MaterialTheme.typography.labelMedium,
                    color=if(available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        NeonPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pro_layout_presets),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pro_layout_presets_detail),style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(ReactionLayout.PICTURE_IN_PICTURE to R.string.reaction_layout_pip,ReactionLayout.SPLIT to R.string.reaction_layout_split).forEach { (layout,label) ->
                    FilterChip(selected=reactionLayout==layout.name,onClick={reactionLayout=layout.name;preferences.edit().putString("reaction_layout",layout.name).apply()},label={Text(stringResource(label))})
                }
            }
        }
        Text(stringResource(R.string.pro_unlimited_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onOpenCamera,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.camera_title)) }
            OutlinedButton(onClick=onOpenReaction,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.reaction_title)) }
        }
    }
}

