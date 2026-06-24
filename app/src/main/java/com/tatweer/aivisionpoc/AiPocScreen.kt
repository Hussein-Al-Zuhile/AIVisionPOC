package com.tatweer.aivisionpoc

import android.graphics.BitmapFactory
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Box(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI Vision POC", style = MaterialTheme.typography.headlineSmall)

            // Image preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.selectedImageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state.selectedImageUri)
                            .memoryCacheKey("img_${state.imageVersion}")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No image selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Image source buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Gallery")
                }
                Button(onClick = { cameraLauncher.launch(cameraUri) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Camera")
                }
            }

            // Prompt
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { vm.onEvent(AiUiEvent.PromptChanged(it)) },
                label = { Text("Prompt") },
                placeholder = { Text("Describe the scene, ask a question…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // Model selection
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ModelVariant.entries.forEachIndexed { index, variant ->
                    SegmentedButton(
                        selected = state.selectedModel == variant,
                        onClick = { vm.onEvent(AiUiEvent.ModelSelected(variant)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ModelVariant.entries.size),
                        label = { Text(if (variant == ModelVariant.FAST) "Fast (E2B)" else "Thinking (E4B)") },
                    )
                }
            }

            // Download banner
            if (!state.modelAvailable) {
                val downloadingThis = state.downloadState is DownloadState.Downloading &&
                    (state.downloadState as DownloadState.Downloading).variant == state.selectedModel

                if (downloadingThis) {
                    val progress = (state.downloadState as DownloadState.Downloading).progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (progress > 0f) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("${(progress * 100).toInt()}%  —  ${state.selectedModel.modelFile.substringAfterLast('/')}", style = MaterialTheme.typography.labelSmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Connecting…", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { vm.onEvent(AiUiEvent.CancelDownload) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Cancel") }
                    }
                } else {
                    val failedThis = state.downloadState is DownloadState.Failed &&
                        (state.downloadState as DownloadState.Failed).variant == state.selectedModel
                    if (failedThis) {
                        Text((state.downloadState as DownloadState.Failed).message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { vm.onEvent(AiUiEvent.DownloadModel) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download model (${state.selectedModel.sizeGb} GB)") }
                }
            }

            // Generate buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.onEvent(AiUiEvent.GenerateText) },
                    enabled = !state.isGenerating && state.modelAvailable,
                    modifier = Modifier.weight(1f),
                ) { Text("Text only") }

                Button(
                    onClick = { vm.onEvent(AiUiEvent.GenerateWithImage) },
                    enabled = !state.isGenerating && state.modelAvailable,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Generate")
                }
            }

            if (state.isGenerating) {
                Button(
                    onClick = { vm.onEvent(AiUiEvent.StopGeneration) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Stop") }
            }

            if (state.generationError.isNotBlank()) {
                Text(state.generationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (response.isNotBlank()) {
                Text("Response", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                ) { Markdown(response) }
            }
        }
    }
}
