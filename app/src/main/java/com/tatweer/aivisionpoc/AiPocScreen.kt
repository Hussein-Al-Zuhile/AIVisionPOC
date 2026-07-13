package com.tatweer.aivisionpoc

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import java.io.File

@Composable
fun AiPocScreen(vm: AiViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val response by vm.response.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        vm.onEvent(AiUiEvent.ImagePicked(uri, bitmap))
    }

    val cameraFile = remember { File(context.cacheDir, "poc_camera.jpg") }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val bitmap = BitmapFactory.decodeFile(cameraFile.absolutePath)
            vm.onEvent(AiUiEvent.ImagePicked(cameraUri, bitmap))
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(response) {
        if (state.isGenerating) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI Vision POC", style = MaterialTheme.typography.headlineSmall)

            ImagePreview(imageUri = state.selectedImageUri, imageVersion = state.imageVersion)

            ImageSourceButtons(
                onGalleryClick = { imagePicker.launch("image/*") },
                onCameraClick = { cameraLauncher.launch(cameraUri) },
            )

            OutlinedTextField(
                value = state.prompt,
                onValueChange = { vm.onEvent(AiUiEvent.PromptChanged(it)) },
                label = { Text("Prompt") },
                placeholder = { Text("Describe the scene, ask a question…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            ModelSelector(
                selectedModel = state.selectedModel,
                onModelSelected = { vm.onEvent(AiUiEvent.ModelSelected(it)) },
            )

            KoogModeRow(
                isKoogMode = state.isKoogMode,
                onToggle = { vm.onEvent(AiUiEvent.ToggleKoogMode) },
            )

            if (!state.modelAvailable) {
                DownloadBanner(
                    selectedModel = state.selectedModel,
                    downloadState = state.downloadState,
                    onDownload = { vm.onEvent(AiUiEvent.DownloadModel) },
                    onCancel = { vm.onEvent(AiUiEvent.CancelDownload) },
                )
            }

            GenerateButtons(
                isGenerating = state.isGenerating,
                modelAvailable = state.modelAvailable,
                isKoogMode = state.isKoogMode,
                onGenerateText = { vm.onEvent(AiUiEvent.GenerateText) },
                onGenerateWithImage = { vm.onEvent(AiUiEvent.GenerateWithImage) },
                onStop = { vm.onEvent(AiUiEvent.StopGeneration) },
            )

            if (state.generationError.isNotBlank()) {
                Text(
                    state.generationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (response.isNotBlank() || state.isGenerating) {
                ResponseSection(
                    response = response,
                    generationId = state.generationId,
                )
            }
        }
    }
}

@Composable
private fun KoogModeRow(isKoogMode: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = isKoogMode,
            onClick = onToggle,
            label = { Text(if (isKoogMode) "Koog Agent" else "Direct LiteRT") },
        )
        if (isKoogMode) {
            Text(
                "Running via Koog 1.0 · tool calling enabled",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ImagePreview(imageUri: Uri?, imageVersion: Int) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .memoryCacheKey("img_$imageVersion")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "No image selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImageSourceButtons(onGalleryClick: () -> Unit, onCameraClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onGalleryClick, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Gallery")
        }
        Button(onClick = onCameraClick, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Camera")
        }
    }
}

@Composable
private fun ModelSelector(selectedModel: ModelVariant, onModelSelected: (ModelVariant) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ModelVariant.entries.forEachIndexed { index, variant ->
            SegmentedButton(
                selected = selectedModel == variant,
                onClick = { onModelSelected(variant) },
                shape = SegmentedButtonDefaults.itemShape(index, ModelVariant.entries.size),
                label = { Text(if (variant == ModelVariant.FAST) "Fast (E2B)" else "Thinking (E4B)") },
            )
        }
    }
}

@Composable
private fun DownloadBanner(
    selectedModel: ModelVariant,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    if (downloadState is DownloadState.Downloading && downloadState.variant == selectedModel) {
        val progress = downloadState.progress
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (progress > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(progress * 100).toInt()}%  —  ${selectedModel.modelFile.substringAfterLast('/')}",
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Connecting…", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    } else {
        if (downloadState is DownloadState.Failed && downloadState.variant == selectedModel) {
            Text(
                downloadState.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("Download model (${selectedModel.sizeGb} GB)")
        }
    }
}

@Composable
private fun GenerateButtons(
    isGenerating: Boolean,
    modelAvailable: Boolean,
    isKoogMode: Boolean,
    onGenerateText: () -> Unit,
    onGenerateWithImage: () -> Unit,
    onStop: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onGenerateText,
            enabled = !isGenerating && modelAvailable,
            modifier = Modifier.weight(1f),
        ) {
            if (isGenerating && isKoogMode) CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            else Text(if (isKoogMode) "Run Agent" else "Text only")
        }

        if (!isKoogMode) {
            Button(
                onClick = onGenerateWithImage,
                enabled = !isGenerating && modelAvailable,
                modifier = Modifier.weight(1f),
            ) {
                if (isGenerating) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                else Text("Generate")
            }
        }
    }

    if (isGenerating) {
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Stop") }
    }
}

@Composable
private fun ResponseSection(response: String, generationId: Int) {
    Text("Response", style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        key(generationId) {
            val streamingState = rememberStreamingMarkdownState()
            val lastLength = remember { mutableIntStateOf(0) }
            LaunchedEffect(response) {
                val prev = lastLength.intValue
                if (response.length > prev) {
                    streamingState.append(response.substring(prev))
                    lastLength.intValue = response.length
                }
            }
            Markdown(streamingState)
        }
    }
}
