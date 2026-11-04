package com.softwareidiot.soundsentinel.player

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val activeRecordingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val speed: Float = 1.0f
)

class AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playRecording(recordingId: String, filePath: String, initialSpeed: Float = 1.0f) {
        val file = File(filePath)
        if (!file.exists()) return

        if (_playbackState.value.activeRecordingId == recordingId && mediaPlayer != null) {
            if (_playbackState.value.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                val dur = duration.toDouble() / 1000.0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().apply { speed = initialSpeed }
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionSeconds = dur
                    )
                    progressJob?.cancel()
                }
                start()
                _playbackState.value = PlaybackState(
                    activeRecordingId = recordingId,
                    isPlaying = true,
                    currentPositionSeconds = 0.0,
                    durationSeconds = dur,
                    speed = initialSpeed
                )
            }
            startProgressLoop()
        } catch (e: Exception) {
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startProgressLoop()
        }
    }

    fun seekTo(seconds: Double) {
        mediaPlayer?.let {
            val ms = (seconds * 1000.0).toInt().coerceIn(0, it.duration)
            it.seekTo(ms)
            _playbackState.value = _playbackState.value.copy(currentPositionSeconds = seconds)
        }
    }

    fun setSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(speed = speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.playbackParams = it.playbackParams.apply { this.speed = speed }
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.toDouble() / 1000.0
                        _playbackState.value = _playbackState.value.copy(currentPositionSeconds = pos)
                    }
                }
                delay(16)
            }
        }
    }
}
