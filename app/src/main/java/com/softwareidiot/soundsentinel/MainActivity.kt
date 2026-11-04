package com.softwareidiot.soundsentinel

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.softwareidiot.soundsentinel.ui.CctvTab
import com.softwareidiot.soundsentinel.ui.MainViewModel
import com.softwareidiot.soundsentinel.ui.components.CctvTopBar
import com.softwareidiot.soundsentinel.ui.components.SoundSentinelDrawerContent
import com.softwareidiot.soundsentinel.ui.screens.PreferencesScreen
import com.softwareidiot.soundsentinel.ui.screens.RecordingsVaultScreen
import com.softwareidiot.soundsentinel.ui.screens.StorageManagerScreen
import com.softwareidiot.soundsentinel.ui.screens.SurveillanceScreen
import com.softwareidiot.soundsentinel.ui.theme.DarkBackground
import com.softwareidiot.soundsentinel.ui.theme.SoundSentinelTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()

        setContent {
            SoundSentinelTheme {
                SoundSentinelAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRate()
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    this.display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                } ?: return

                val modes = display.supportedModes
                val currentMode = display.mode
                val bestMode = modes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                    ?: modes.maxByOrNull { it.refreshRate }

                if (bestMode != null) {
                    val params: WindowManager.LayoutParams = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = params
                }
            } catch (ignored: Exception) {}
        }
    }
}

@Composable
fun SoundSentinelAppContent(viewModel: MainViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val liveState by viewModel.liveMonitorState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Back button closes drawer if open, or returns to Home screen if on another section
    BackHandler(enabled = drawerState.isOpen || activeTab != CctvTab.SURVEILLANCE) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            viewModel.setTab(CctvTab.SURVEILLANCE)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SoundSentinelDrawerContent(
                selectedTab = activeTab,
                onSelectTab = { tab ->
                    viewModel.setTab(tab)
                    coroutineScope.launch { drawerState.close() }
                },
                isArmed = liveState.isRunning,
                recordingsCount = recordings.size,
                usedPercentage = storageStats.usedPercentage
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CctvTopBar(
                    currentSectionTitle = activeTab.title,
                    isArmed = liveState.isRunning,
                    isRecording = liveState.isRecording,
                    recordingDuration = liveState.recordingDuration,
                    onMenuClick = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    modifier = Modifier.statusBarsPadding()
                )
            },
            containerColor = DarkBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "screen_transition"
                ) { currentTab ->
                    when (currentTab) {
                        CctvTab.SURVEILLANCE -> {
                            SurveillanceScreen(
                                isArmed = liveState.isRunning,
                                onToggleArmed = { viewModel.toggleArmed() },
                                onTriggerInstantRecord = { viewModel.triggerInstantRecord() },
                                onOpenVault = { viewModel.setTab(CctvTab.VAULT) },
                                onOpenPreferences = { viewModel.setTab(CctvTab.PREFERENCES) },
                                isMicCapturing = liveState.isCapturing,
                                activeSampleRate = liveState.activeSampleRate,
                                soundLevel = liveState.soundLevel,
                                currentDb = liveState.currentDb,
                                noiseFloorDb = liveState.noiseFloorDb,
                                effectiveThreshold = liveState.effectiveThreshold,
                                liveWaveformHistory = liveState.liveWaveformHistory,
                                isRecording = liveState.isRecording,
                                recordingDuration = liveState.recordingDuration,
                                silenceCountdown = liveState.silenceRemaining,
                                triggersInSession = liveState.triggersInSession,
                                micError = liveState.error,
                                settings = settings,
                                storageStats = storageStats,
                                onUpdateSettings = { viewModel.updateSettings(it) }
                            )
                        }

                        CctvTab.VAULT -> {
                            RecordingsVaultScreen(
                                recordings = recordings,
                                playbackState = playbackState,
                                onPlay = { viewModel.playRecording(it) },
                                onPause = { viewModel.pausePlayback() },
                                onResume = { viewModel.resumePlayback() },
                                onSeek = { viewModel.seekPlayback(it) },
                                onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                                onToggleProtect = { viewModel.toggleProtect(it) },
                                onRename = { id, title -> viewModel.renameRecording(id, title) },
                                onExport = { viewModel.exportRecording(it) },
                                onShare = { viewModel.shareRecording(it) },
                                onDelete = { viewModel.deleteRecording(it) },
                                onClearUnprotected = { viewModel.clearAllUnprotected() },
                                onReclassify = { viewModel.reclassifyRecording(it) }
                            )
                        }

                        CctvTab.STORAGE -> {
                            StorageManagerScreen(
                                settings = settings,
                                storageStats = storageStats,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onClearUnprotected = { viewModel.clearAllUnprotected() },
                                onRefreshStorage = { viewModel.refreshStorageStats() },
                                onSendTestAlert = { viewModel.sendTestStorageWarning() }
                            )
                        }

                        CctvTab.PREFERENCES -> {
                            PreferencesScreen(
                                settings = settings,
                                effectiveThreshold = liveState.effectiveThreshold,
                                onUpdateSettings = { viewModel.updateSettings(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
