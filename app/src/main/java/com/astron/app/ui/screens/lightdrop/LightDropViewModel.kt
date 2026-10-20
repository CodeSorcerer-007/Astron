package com.astron.app.ui.screens.lightdrop

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.lightdrop.OpticalDensity
import com.astron.app.transport.lightdrop.OpticalFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LightDropViewModel @Inject constructor(
    private val lightDropManager: LightDropManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _fps = MutableStateFlow(12f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _density = MutableStateFlow(OpticalDensity.BALANCED)
    val density: StateFlow<OpticalDensity> = _density.asStateFlow()

    private val _frames = MutableStateFlow<List<OpticalFrame>>(emptyList())
    val frames: StateFlow<List<OpticalFrame>> = _frames.asStateFlow()

    private val _currentFrameIndex = MutableStateFlow(0)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex.asStateFlow()

    private val _currentFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val currentFrameBitmap: StateFlow<Bitmap?> = _currentFrameBitmap.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _receivedFrames = MutableStateFlow<Map<Int, OpticalFrame>>(emptyMap())
    val receivedFrames: StateFlow<Map<Int, OpticalFrame>> = _receivedFrames.asStateFlow()

    private val _totalFramesExpected = MutableStateFlow(0)
    val totalFramesExpected: StateFlow<Int> = _totalFramesExpected.asStateFlow()

    private val _completedFile = MutableStateFlow<File?>(null)
    val completedFile: StateFlow<File?> = _completedFile.asStateFlow()

    private var currentActiveFile: File? = null
    private var transmitJob: Job? = null

    init {
        prepareDemoData()
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setFps(newFps: Float) {
        _fps.value = newFps
    }

    fun setDensity(newDensity: OpticalDensity) {
        _density.value = newDensity
        val file = currentActiveFile
        if (file != null) {
            prepareFile(file)
        } else {
            prepareDemoData()
        }
    }

    fun prepareDemoData() {
        currentActiveFile = null
        val demoPayload = "Astron Optical LightDrop — Ultra-secure air-gapped zero-radio transfer protocol. SHA-256 verified.".toByteArray()
        val generatedFrames = lightDropManager.encodeBytesToFrames(demoPayload, density = _density.value)
        _frames.value = generatedFrames
        _currentFrameIndex.value = 0
        if (generatedFrames.isNotEmpty()) {
            _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(generatedFrames[0])
        }
    }

    fun prepareFile(file: File) {
        try {
            currentActiveFile = file
            val generatedFrames = lightDropManager.encodeFileToFrames(file, density = _density.value)
            _frames.value = generatedFrames
            _currentFrameIndex.value = 0
            if (generatedFrames.isNotEmpty()) {
                _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(generatedFrames[0])
            }
        } catch (e: Exception) {
            // file too large or unreadable
        }
    }

    fun startTransmission() {
        if (_frames.value.isEmpty()) prepareDemoData()
        _isTransmitting.value = true
        transmitJob?.cancel()
        transmitJob = viewModelScope.launch {
            while (_isTransmitting.value && _frames.value.isNotEmpty()) {
                val delayMs = (1000f / _fps.value).toLong().coerceIn(10L, 500L)
                delay(delayMs)
                val nextIdx = (_currentFrameIndex.value + 1) % _frames.value.size
                _currentFrameIndex.value = nextIdx
                _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(_frames.value[nextIdx])
            }
        }
    }

    fun stopTransmission() {
        _isTransmitting.value = false
        transmitJob?.cancel()
    }

    fun onFrameScanned(rawString: String) {
        val frame = lightDropManager.parseFrame(rawString) ?: return
        _totalFramesExpected.value = frame.totalFrames
        _receivedFrames.update { current ->
            if (current.containsKey(frame.frameIndex)) current
            else current + (frame.frameIndex to frame)
        }

        if (_receivedFrames.value.size >= frame.totalFrames && _completedFile.value == null) {
            assembleReceivedFile()
        }
    }

    fun getMissingFrames(): List<Int> {
        val total = _totalFramesExpected.value
        if (total <= 0) return emptyList()
        val received = _receivedFrames.value.keys
        return (0 until total).filter { !received.contains(it) }
    }

    private fun assembleReceivedFile() {
        try {
            val sortedFrames = _receivedFrames.value.values.sortedBy { it.frameIndex }
            val target = File(storageManager.getAstronDownloadDirectory(), "lightdrop_${System.currentTimeMillis()}.bin")
            target.outputStream().use { fos ->
                for (f in sortedFrames) {
                    fos.write(f.payload)
                }
            }
            _completedFile.value = target
        } catch (t: Throwable) {
            android.util.Log.e("LightDropVM", "Failed assembling optical frames", t)
        }
    }

    fun resetReceiver() {
        _receivedFrames.value = emptyMap()
        _totalFramesExpected.value = 0
        _completedFile.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTransmission()
    }
}
