package com.umbracam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun Destination.suiteIcon(): Int = when(this) {
    Destination.CAMERA -> R.drawable.ic_suite_camera
    Destination.REACTION -> R.drawable.ic_suite_screen
    Destination.SOS -> R.drawable.ic_suite_shield
    Destination.VAULT -> R.drawable.ic_suite_folder
    Destination.PRO -> R.drawable.ic_suite_star
    Destination.SETTINGS -> R.drawable.ic_suite_settings
    else -> R.drawable.ic_suite_home
}

@Composable
fun SuiteHome(onOpen: (Destination) -> Unit, onPro: () -> Unit = {}) {
    val columns = if(LocalDensity.current.fontScale >= 1.5f && LocalConfiguration.current.screenWidthDp < 600) 1 else 2
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Destination.studios.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach { studio ->
                    val colors = when(studio) {
                        Destination.CAMERA -> listOf(Color(0xFFB522E1),Color(0xFF5410A5),Color(0xFF21073D))
                        Destination.REACTION -> listOf(Color(0xFF17BDE2),Color(0xFF0056CA),Color(0xFF061B52))
                        Destination.SOS -> listOf(Color(0xFFFF705A),Color(0xFFB41535),Color(0xFF3A0715))
                        else -> listOf(Color(0xFF1DD89C),Color(0xFF007A60),Color(0xFF05372E))
                    }
                    Column(Modifier.weight(1f).heightIn(min=176.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(colors)).border(1.dp,colors.first().copy(alpha=.8f),RoundedCornerShape(24.dp))
                        .testTag("home-${studio.route}").clickable(role=Role.Button,onClick={onOpen(studio)}).padding(16.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        SuiteIcon(studio.suiteIcon(),modifier=Modifier.size(36.dp))
                        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center) {
                            Text(stringResource(studio.title),Modifier.widthIn(max=112.dp * LocalDensity.current.fontScale.coerceAtMost(1.8f)),style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center,color=Color.White)
                        }
                        Text(stringResource(when(studio) {
                            Destination.CAMERA -> R.string.suite_camera_detail
                            Destination.REACTION -> R.string.suite_screen_detail
                            Destination.SOS -> R.string.suite_sos_detail
                            else -> R.string.suite_recordings_detail
                        }),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,color=Color.White)
                    }
                }
            }
        }
        NeonPanel(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onPro)) {
            Text(stringResource(R.string.pro_title),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pro_home_detail),style=MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.tagline),Modifier.fillMaxWidth().padding(top=8.dp),style=MaterialTheme.typography.bodySmall,
            textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SuiteNavigation(current: Destination, onOpen: (Destination) -> Unit) {
    Row(Modifier.navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp).clip(RoundedCornerShape(24.dp))
        .background(NeonPanelColor).border(1.dp,NeonOutline,RoundedCornerShape(24.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf(Destination.HOME,Destination.VAULT,Destination.SETTINGS).forEach { item ->
            val selected = current == item
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                .background(if(selected) Brush.verticalGradient(listOf(Color(0xFF71319F),Color(0xFF28133C))) else Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent)))
                .semantics { this.selected=selected }.clickable(role=Role.Tab,onClick={onOpen(item)}).padding(horizontal=4.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                SuiteIcon(item.suiteIcon(),stringResource(item.title))
                if(LocalDensity.current.fontScale < 1.5f || selected) Text(stringResource(item.title),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuiteRail(current: Destination, onOpen: (Destination) -> Unit) {
    val showLabels=LocalDensity.current.fontScale<1.5f
    NavigationRail(containerColor=NeonPanelColor,modifier=Modifier.width(96.dp).fillMaxHeight()) {
        Spacer(Modifier.weight(1f))
        listOf(Destination.HOME,Destination.VAULT,Destination.SETTINGS).forEach { item ->
            val title=stringResource(item.title)
            TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip { Text(title) }},state=rememberTooltipState()) {
                NavigationRailItem(selected=current==item,onClick={onOpen(item)},
                    icon={SuiteIcon(item.suiteIcon(),title)},label=if(showLabels) ({Text(title,style=MaterialTheme.typography.labelSmall)}) else null,
                    colors=NavigationRailItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,indicatorColor=MaterialTheme.colorScheme.primaryContainer))
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SuiteSettings(onCamera: () -> Unit, onRecordings: () -> Unit, onSupport: () -> Unit, onSos: () -> Unit, onWelcome: () -> Unit, onPro: () -> Unit = {}) {
    var information by remember { mutableStateOf<Pair<String,String>?>(null) }
    val context=LocalContext.current
    val details=stringResource(R.string.settings_details)
    val appearance=stringResource(R.string.suite_appearance)
    val privacy=stringResource(R.string.suite_privacy)
    val about=stringResource(R.string.suite_about)
    val privacyText=stringResource(R.string.library_privacy)
    val packageVersion=remember { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SuiteSettingRow(R.drawable.ic_suite_camera,stringResource(R.string.suite_recording),stringResource(R.string.suite_recording_hint),onCamera)
        SuiteSettingRow(R.drawable.ic_suite_mic,stringResource(R.string.suite_audio),stringResource(R.string.suite_audio_hint),onCamera)
        SuiteSettingRow(R.drawable.ic_suite_storage,stringResource(R.string.suite_storage),stringResource(R.string.suite_storage_hint),onRecordings)
        SuiteSettingRow(R.drawable.ic_suite_lock,privacy,stringResource(R.string.suite_privacy_hint)) { information=privacy to privacyText }
        SuiteSettingRow(R.drawable.ic_suite_shield,stringResource(R.string.sos_title),stringResource(R.string.suite_sos_detail),onSos)
        SuiteSettingRow(R.drawable.ic_suite_star,stringResource(R.string.pro_title),stringResource(R.string.pro_home_detail),onPro)
        SuiteSettingRow(R.drawable.ic_suite_settings,appearance,stringResource(R.string.suite_appearance_hint)) { information=appearance to details }
        SuiteSettingRow(R.drawable.ic_suite_info,about,packageVersion) { information=about to "UmbraCam $packageVersion" }
        SuiteSettingRow(R.drawable.ic_suite_camera,stringResource(R.string.camera_support_check),stringResource(R.string.suite_support_hint),onSupport)
        OutlinedButton(onClick=onWelcome,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.suite_welcome_guide)) }
        Text(details,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    information?.let { info -> AlertDialog(onDismissRequest={information=null},title={Text(info.first)},text={Text(info.second)},
        confirmButton={TextButton(onClick={information=null}) { Text(stringResource(R.string.suite_close)) }}) }
}

@Composable
private fun SuiteSettingRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    NeonPanel(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            SuiteIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SuiteIcon(R.drawable.ic_arrow_back,modifier=Modifier.size(16.dp).graphicsLayer { rotationZ=180f })
        }
    }
}

@Composable
fun SuiteWelcome(onDone: () -> Unit) {
    var setup by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        if(!setup) {
            Image(painterResource(R.drawable.umbracam_icon),null,Modifier.size(152.dp).clip(androidx.compose.foundation.shape.CircleShape))
            Text(stringResource(R.string.suite_welcome_title),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
            Text(stringResource(R.string.suite_welcome_detail),style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
        } else {
            Text(stringResource(R.string.suite_setup_title),style=MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.suite_setup_detail),style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
            val permissions=listOf(Triple(R.drawable.ic_suite_camera,R.string.suite_camera_permission,Manifest.permission.CAMERA),
                Triple(R.drawable.ic_suite_mic,R.string.camera_microphone,Manifest.permission.RECORD_AUDIO),
                Triple(R.drawable.ic_suite_info,R.string.suite_notifications,Manifest.permission.POST_NOTIFICATIONS))
            permissions.forEach { (icon,label,name) ->
                val granted=remember(refresh,name) {
                    if(name==Manifest.permission.POST_NOTIFICATIONS)
                        (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED) && context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
                    else context.checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED
                }
                NeonPanel(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        SuiteIcon(icon); Text(stringResource(label),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        if(granted) Text(stringResource(R.string.suite_enabled),style=MaterialTheme.typography.labelMedium,color=Color(0xFF7BE5AF))
                        else TextButton(onClick={
                            if(name==Manifest.permission.POST_NOTIFICATIONS && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED))
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
                            else permission.launch(name)
                        }) { Text(stringResource(R.string.suite_enable)) }
                    }
                }
            }
            NeonPanel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { SuiteIcon(R.drawable.ic_suite_screen); Text(stringResource(R.string.suite_consent_each_time)) }
            }
        }
        }
        NeonButton(stringResource(if(setup) R.string.suite_continue else R.string.suite_get_started),
            { if(setup) onDone() else setup=true },modifier=Modifier.fillMaxWidth())
    }
}

@Composable
fun SuiteSosPreview(onQuickRecord: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Box(Modifier.padding(top=24.dp).size(160.dp).background(Brush.radialGradient(listOf(Color(0xFFB81548).copy(alpha=.4f),Color.Transparent))),contentAlignment=Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Color(0xFFFF6480)) { SuiteIcon(R.drawable.ic_suite_shield,modifier=Modifier.size(112.dp)) }
        }
        Text(stringResource(R.string.sos_title),style=MaterialTheme.typography.headlineMedium)
        NeonPanel { Text(stringResource(R.string.sos_title),style=MaterialTheme.typography.titleMedium); Text(stringResource(R.string.sos_preview)) }
        NeonButton(stringResource(R.string.sos_quick_record), onQuickRecord, modifier=Modifier.fillMaxWidth())
        Text(stringResource(R.string.suite_sos_honest),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

