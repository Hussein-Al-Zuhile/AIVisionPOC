package com.tatweer.aivisionpoc

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiUiState(
    val selectedModel: ModelVariant = ModelVariant.FAST,
    val selectedImageUri: Uri? = null,
    val imageVersion: Int = 0,
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val generationError: String = "",
    val modelAvailable: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
)

sealed class AiUiEvent {
    data class ModelSelected(val variant: ModelVariant) : AiUiEvent()
    data class PromptChanged(val text: String) : AiUiEvent()
    data class ImagePicked(val uri: Uri, val bitmap: Bitmap?) : AiUiEvent()
    data object GenerateText : AiUiEvent()
    data object GenerateWithImage : AiUiEvent()
    data object StopGeneration : AiUiEvent()
    data object DownloadModel : AiUiEvent()
    data object CancelDownload : AiUiEvent()
}

class AiViewModel(app: Application) : AndroidViewModel(app) {

    private val aiHelper = AiHelper(app)
    private var selectedBitmap: Bitmap? = null
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(
        AiUiState(modelAvailable = aiHelper.isModelAvailable(ModelVariant.FAST))
    )
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val _response = MutableStateFlow("")
    val response: StateFlow<String> = _response.asStateFlow()

    init {
        viewModelScope.launch {
            DownloadService.state.collect { downloadState ->
                _uiState.update {
                    it.copy(
                        downloadState = downloadState,
                        modelAvailable = aiHelper.isModelAvailable(it.selectedModel),
                    )
                }
            }
        }
    }

    fun onEvent(event: AiUiEvent) {
        when (event) {
            is AiUiEvent.ModelSelected -> onModelSelected(event.variant)
            is AiUiEvent.PromptChanged -> _uiState.update { it.copy(prompt = event.text) }
            is AiUiEvent.ImagePicked -> onImagePicked(event.uri, event.bitmap)
            is AiUiEvent.GenerateText -> generate(image = null)
            is AiUiEvent.GenerateWithImage -> generate(image = selectedBitmap)
            is AiUiEvent.StopGeneration -> stopGeneration()
            is AiUiEvent.DownloadModel -> DownloadService.start(getApplication(), _uiState.value.selectedModel)
            is AiUiEvent.CancelDownload -> DownloadService.cancel(getApplication())
        }
    }

    private fun onModelSelected(variant: ModelVariant) {
        _uiState.update {
            it.copy(
                selectedModel = variant,
                modelAvailable = aiHelper.isModelAvailable(variant),
                downloadState = DownloadState.Idle,
            )
        }
    }

    private fun onImagePicked(uri: Uri, bitmap: Bitmap?) {
        selectedBitmap = bitmap
        _uiState.update { it.copy(
            selectedImageUri = uri,
            imageVersion = it.imageVersion + 1,
            generationError = "",
        ) }
    }

    private fun generate(image: Bitmap?) {
        val state = _uiState.value
        if (state.prompt.isBlank()) {
            _uiState.update { it.copy(generationError = "Please enter a prompt.") }
            return
        }
        if (image == null && _uiState.value.selectedImageUri != null) {
            // GenerateWithImage was called but bitmap wasn't decoded — just guard silently
        }
        _response.value = ""
        _uiState.update { it.copy(isGenerating = true, generationError = "") }
        generationJob = viewModelScope.launch {
            try {
                aiHelper.model = state.selectedModel
                aiHelper.initialize()
                val flow = if (image != null) aiHelper.generate(state.prompt, image)
                           else aiHelper.generate(state.prompt)
                flow.collect { chunk -> _response.value += chunk }
                _response.value = _response.value.trim()
            } catch (e: Exception) {
                _uiState.update { it.copy(generationError = e.message ?: "Unknown error") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    override fun onCleared() {
        aiHelper.close()
        super.onCleared()
    }
}
