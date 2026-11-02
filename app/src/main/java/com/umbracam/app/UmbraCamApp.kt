package com.umbracam.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.umbracam.app.camera.*
import com.umbracam.core.recording.SingleCameraSessionCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmbraCamApp(cameraViewModel: CameraStudioViewModel, cameraOpenRequest: Int = 0,
    supportViewModel: CameraSupportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    screenViewModel: com.umbracam.app.screen.ScreenStudioViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    screenOpenRequest: Int = 0, onScreenStart: (com.umbracam.core.recording.ReactionRecordingRequest) -> Unit = {}) {
    val nav = rememberNavController()
    var welcome by rememberSaveable { mutableStateOf(false) }
    var notificationInfo by remember { mutableStateOf(false) }
    val expandedDevice=LocalConfiguration.current.screenWidthDp>=600
    val recording by cameraViewModel.session.collectAsStateWithLifecycle()
    val screenRecording by screenViewModel.session.collectAsStateWithLifecycle()
    val open: (Destination) -> Unit = { destination ->
        welcome=false
        nav.navigate(destination.route) { launchSingleTop=true }
    }
    BackHandler(welcome) { welcome=false }
    LaunchedEffect(screenOpenRequest) {
        if(screenOpenRequest>0) { welcome=false; nav.navigate(Destination.REACTION.route) { popUpTo(Destination.HOME.route); launchSingleTop=true } }
    }
    LaunchedEffect(cameraOpenRequest) {
        if(cameraOpenRequest>0) { welcome=false; nav.navigate(Destination.CAMERA.route) { popUpTo(Destination.HOME.route); launchSingleTop=true } }
    }
    NeonGround {
        NavHost(nav,startDestination=Destination.HOME.route) {
            Destination.entries.forEach { destination -> composable(destination.route) {
                val home=destination==Destination.HOME
                Scaffold(containerColor=Color.Transparent,topBar={
                    CenterAlignedTopAppBar(colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=Color.Transparent),title={
                        if(welcome) Text("")
                        else if(home) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Image(painterResource(R.drawable.umbracam_icon),null,Modifier.size(32.dp).clip(CircleShape))
                            Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge)
                        } else Text(stringResource(destination.title),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleLarge)
                    },navigationIcon={
                        if(!home) IconButton(onClick={if(welcome) welcome=false else nav.popBackStack()}) {
                            SuiteIcon(R.drawable.ic_arrow_back,stringResource(if(!welcome && nav.previousBackStackEntry?.destination?.route==Destination.HOME.route && destination !in setOf(Destination.CAMERA_SUPPORT,Destination.VAULT)) R.string.back_home else R.string.back))
                        }
                    },actions={
                        if(welcome) TextButton(onClick={welcome=false;open(Destination.HOME)}) { Text(stringResource(R.string.suite_skip)) }
                        else if(home) IconButton(onClick={notificationInfo=true}) { SuiteIcon(R.drawable.ic_suite_bell,stringResource(R.string.suite_notifications)) }
                        else if(destination in setOf(Destination.CAMERA,Destination.REACTION)) IconButton(onClick={open(Destination.SETTINGS)}) {
                            SuiteIcon(R.drawable.ic_suite_settings,stringResource(R.string.settings))
                        }
                    })
                },bottomBar={
                    if(!expandedDevice && !welcome && destination in setOf(Destination.HOME,Destination.VAULT,Destination.SETTINGS)) SuiteNavigation(destination) { target ->
                        nav.navigate(target.route) { popUpTo(Destination.HOME.route); launchSingleTop=true }
                    }
                }) { padding ->
                    Row(Modifier.padding(padding).fillMaxSize()) {
                    if(expandedDevice && !welcome && destination in setOf(Destination.HOME,Destination.VAULT,Destination.SETTINGS)) SuiteRail(destination) { target ->
                        nav.navigate(target.route) { popUpTo(Destination.HOME.route); launchSingleTop=true }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter) {
                        val scroll=rememberScrollState()
                        Column(Modifier.widthIn(max=720.dp).fillMaxSize()
                            .then(if(destination==Destination.VAULT || welcome) Modifier else Modifier.verticalScroll(scroll))
                            .padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            if(welcome) SuiteWelcome { welcome=false; open(Destination.HOME) }
                            else when(destination) {
                                Destination.HOME -> {
                                    if(recording?.state in SingleCameraSessionCoordinator.busyStates) NeonPanel {
                                        Text(stringResource(R.string.recording_home_notice)); TextButton(onClick={open(Destination.CAMERA)}) { Text(stringResource(R.string.open_recording)) }
                                    }
                                    if(screenRecording?.state in SingleCameraSessionCoordinator.busyStates) NeonPanel {
                                        Text(stringResource(R.string.screen_home_notice)); TextButton(onClick={open(Destination.REACTION)}) { Text(stringResource(R.string.screen_open)) }
                                    }
                                    SuiteHome(open)
                                }
                                Destination.CAMERA -> CameraStudioScreen(cameraViewModel,{open(Destination.CAMERA_SUPPORT)},{open(Destination.VAULT)})
                                Destination.CAMERA_SUPPORT -> {
                                    val support by supportViewModel.state.collectAsStateWithLifecycle()
                                    CameraSupportScreen(support,supportViewModel::checkSupport) { open(Destination.CAMERA) }
                                }
                                Destination.REACTION -> com.umbracam.app.screen.ScreenStudioScreen(screenViewModel,onScreenStart) { open(Destination.VAULT) }
                                Destination.VAULT -> com.umbracam.app.library.RecordingsScreen(androidx.lifecycle.viewmodel.compose.viewModel())
                                Destination.SOS -> SuiteSosPreview()
                                Destination.SETTINGS -> SuiteSettings({open(Destination.CAMERA)},{open(Destination.VAULT)},{open(Destination.CAMERA_SUPPORT)},{open(Destination.SOS)},{welcome=true})
                            }
                        }
                    }
                    }
                }
            } }
        }
    }
    if(notificationInfo) AlertDialog(onDismissRequest={notificationInfo=false},title={Text(stringResource(R.string.suite_notifications))},
        text={Text(stringResource(R.string.error_notification_permission))},confirmButton={TextButton(onClick={notificationInfo=false}) { Text(stringResource(R.string.suite_close)) }})
}
