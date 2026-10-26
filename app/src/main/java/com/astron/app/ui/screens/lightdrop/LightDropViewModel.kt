package com.astron.app.ui.screens.lightdrop

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferItem
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.lightdrop.OpticalDensity
import com.astron.app.transport.lightdrop.OpticalFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    @param:ApplicationContext private val context: Context,
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

    private val _activeFileName = MutableStateFlow<String?>("Air-Gapped Zero-Radio Demo.txt")
    val activeFileName: StateFlow<String?> = _activeFileName.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _receivedFrames = MutableStateFlow<Map<Int, OpticalFrame>>(emptyMap())
    val receivedFrames: StateFlow<Map<Int, OpticalFrame>> = _receivedFrames.asStateFlow()

    private val _totalFramesExpected = MutableStateFlow(0)
    val totalFramesExpected: StateFlow<Int> = _totalFramesExpected.asStateFlow()

    private val _completedFile = MutableStateFlow<File?>(null)
    val completedFile: StateFlow<File?> = _completedFile.asStateFlow()

    private var currentActiveFile: File? = null
    private var currentActiveItem: TransferItem? = null
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
        val item = currentActiveItem
        val file = currentActiveFile
        if (item != null) {
            prepareTransferItem(item)
        } else if (file != null) {
            prepareFile(file)
        } else {
            prepareDemoData()
        }
    }

    fun prepareDemoData() {
        currentActiveFile = null
        currentActiveItem = null
        _activeFileName.value = "Air-Gapped Zero-Radio Demo.txt"
        val demoPayload = "Astron Optical LightDrop — Ultra-secure air-gapped zero-radio transfer protocol. SHA-256 verified.".toByteArray()
        val generatedFrames = lightDropManager.encodeBytesToFrames(demoPayload, density = _density.value, fileName = "demo.txt")
        _frames.value = generatedFrames
        _currentFrameIndex.value = 0
        if (generatedFrames.isNotEmpty()) {
            _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(generatedFrames[0])
        }
    }

    fun prepareFile(file: File) {
        try {
            currentActiveFile = file
            currentActiveItem = null
            _activeFileName.value = file.name
            val generatedFrames = lightDropManager.encodeFileToFrames(file, density = _density.value, fileName = file.name)
            _frames.value = generatedFrames
            _currentFrameIndex.value = 0
            if (generatedFrames.isNotEmpty()) {
                _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(generatedFrames[0])
            }
        } catch (e: Exception) {
            Log.e("LightDropVM", "Error preparing file: ${e.message}")
        }
    }

    fun prepareTransferItem(item: TransferItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentActiveItem = item
                currentActiveFile = item.realFilePath?.let { File(it) }
                _activeFileName.value = item.name
                val stream = try {
                    if (item.uri != null) {
                        context.contentResolver.openInputStream(item.uri!!)
                    } else if (item.realFilePath != null) {
                        File(item.realFilePath!!).inputStream()
                    } else null
                } catch (_: Throwable) { null }

                val bytes = stream?.use { it.readBytes() } ?: item.name.toByteArray(Charsets.UTF_8)
                val generated = lightDropManager.encodeBytesToFrames(
                    bytes = bytes,
                    density = _density.value,
                    fileName = item.name
                )
                _frames.value = generated
                _currentFrameIndex.value = 0
                if (generated.isNotEmpty()) {
                    _currentFrameBitmap.value = lightDropManager.renderFrameBitmap(generated[0])
                }
            } catch (t: Throwable) {
                Log.e("LightDropVM", "Error preparing transfer item", t)
            }
        }
    }

    fun prepareFiles(items: List<TransferItem>) {
        if (items.isNotEmpty()) {
            prepareTransferItem(items.first())
        }
    }

    fun preparePickedUri(uri: Uri) {
        try {
            val item = storageManager.queryTransferItem(uri)
            prepareTransferItem(item)
        } catch (t: Throwable) {
            Log.e("LightDropVM", "Error preparing picked uri", t)
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
            val detectedName = sortedFrames.firstOrNull { !it.fileName.isNullOrBlank() }?.fileName
                ?: "lightdrop_${System.currentTimeMillis()}.bin"
            val target = storageManager.createDestinationFile(detectedName)
            target.outputStream().use { fos ->
                for (f in sortedFrames) {
                    fos.write(f.payload)
                }
            }
            _completedFile.value = target
        } catch (t: Throwable) {
            Log.e("LightDropVM", "Failed assembling optical frames", t)
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
