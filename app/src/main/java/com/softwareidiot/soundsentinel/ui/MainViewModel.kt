package com.softwareidiot.soundsentinel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softwareidiot.soundsentinel.data.model.AudioRecording
import com.softwareidiot.soundsentinel.data.model.CCTVEvent
import com.softwareidiot.soundsentinel.data.model.CCTVSettings
import com.softwareidiot.soundsentinel.data.model.StorageStats
import com.softwareidiot.soundsentinel.data.repository.CctvRepository
import com.softwareidiot.soundsentinel.player.AudioPlayerManager
import com.softwareidiot.soundsentinel.player.PlaybackState
import com.softwareidiot.soundsentinel.service.AcousticMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CctvTab(val title: String, val subtitle: String) {
    SURVEILLANCE("Home • Audio Monitor", "Live acoustic detection & background control"),
    VAULT("Recordings Vault", "Play, seek, rename, or share saved audio clips"),
    STORAGE("Storage & Quota", "Manage vault capacity and circular retention"),
    PREFERENCES("Preferences", "Auto/Manual detection, quality & permissions")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CctvRepository.getInstance(application)
    val playerManager = AudioPlayerManager()

    private val _activeTab = MutableStateFlow(CctvTab.SURVEILLANCE)
    val activeTab: StateFlow<CctvTab> = _activeTab.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val settings: StateFlow<CCTVSettings> = repository.settings
    val recordings: StateFlow<List<AudioRecording>> = repository.recordings
    val events: StateFlow<List<CCTVEvent>> = repository.events
    val storageStats: StateFlow<StorageStats> = repository.storageStats
    val liveMonitorState = AcousticMonitorService.liveState
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    fun setTab(tab: CctvTab) {
        _activeTab.value = tab
        if (tab == CctvTab.VAULT || tab == CctvTab.STORAGE) {
            repository.reconcileDiskAndMetadata()
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun toggleArmed() {
        val app = getApplication<Application>()
        val currentlyRunning = liveMonitorState.value.isRunning
        if (currentlyRunning) {
            AcousticMonitorService.stop(app)
            repository.updateSettings(settings.value.copy(isArmed = false))
        } else {
            repository.updateSettings(settings.value.copy(isArmed = true))
            AcousticMonitorService.start(app)
        }
    }

    fun triggerInstantRecord() {
        val app = getApplication<Application>()
        if (liveMonitorState.value.isRunning) {
            AcousticMonitorService.triggerInstantRecord(app)
            _toastMessage.value = "Manual trigger activated — recording clip now"
        }
    }

    fun updateSettings(newSettings: CCTVSettings) {
        val oldQuality = settings.value.audioQuality
        repository.updateSettings(newSettings)
        if (oldQuality != newSettings.audioQuality && liveMonitorState.value.isRunning) {
            val app = getApplication<Application>()
            AcousticMonitorService.stop(app)
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                AcousticMonitorService.start(app)
            }
        }
    }

    fun toggleProtect(id: String) {
        repository.toggleProtect(id)
    }

    fun renameRecording(id: String, newTitle: String) {
        repository.renameRecording(id, newTitle)
        _toastMessage.value = "Recording renamed"
    }

    fun exportRecording(recording: AudioRecording) {
        viewModelScope.launch {
            val savedPath = repository.exportRecordingToDevice(recording)
            if (savedPath != null) {
                _toastMessage.value = "Saved to phone: $savedPath"
            } else {
                _toastMessage.value = "Could not export file to public storage"
            }
        }
    }

    fun shareRecording(recording: AudioRecording) {
        repository.shareRecording(recording)
    }

    fun deleteRecording(id: String) {
        if (playbackState.value.activeRecordingId == id) {
            playerManager.stop()
        }
        repository.deleteRecording(id)
        _toastMessage.value = "Recording deleted"
    }

    fun clearAllUnprotected() {
        playerManager.stop()
        repository.clearAllUnprotected()
        _toastMessage.value = "Purged unlocked recordings"
    }

    fun reclassifyRecording(id: String) {
        repository.reclassifyRecording(id)
        _toastMessage.value = "Re-scanned audio signal"
    }

    fun clearEvents() {
        repository.clearEvents()
    }

    fun playRecording(recording: AudioRecording) {
        playerManager.playRecording(recording.id, recording.filePath, playbackState.value.speed)
    }

    fun pausePlayback() {
        playerManager.pause()
    }

    fun resumePlayback() {
        playerManager.resume()
    }

    fun seekPlayback(seconds: Double) {
        playerManager.seekTo(seconds)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun refreshStorageStats() {
        repository.reconcileDiskAndMetadata()
        _toastMessage.value = "Storage verified with device disk"
    }

    fun sendTestStorageWarning() {
        val stats = storageStats.value
        repository.sendStorageWarningNotification(
            stats.usedPercentage,
            stats.usedBytes,
            stats.quotaBytes
        )
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stop()
    }
}
