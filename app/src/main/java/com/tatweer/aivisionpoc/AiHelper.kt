package com.tatweer.aivisionpoc

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_IMAGE_PX = 768

enum class ModelVariant(val modelFile: String, val downloadUrl: String, val sizeGb: Float) {
    FAST(
        modelFile = "llm/gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeGb = 2.6f,
    ),
    THINKING(
        modelFile = "llm/gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeGb = 3.7f,
    ),
}

class AiHelper(private val context: Context) {

    var model: ModelVariant = ModelVariant.FAST
        set(value) {
            if (field != value) { close(); field = value }
        }

    private var engine: Engine? = null

    fun isModelAvailable(variant: ModelVariant = model): Boolean =
        File(context.filesDir, variant.modelFile).exists()

    suspend fun initialize() {
        if (engine != null) return
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, model.modelFile)
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                maxNumTokens = 2048,
                maxNumImages = 1,
            )
            engine = Engine(config).also {
                it.initialize()
            }
        }
    }

    suspend fun download(variant: ModelVariant = model, onProgress: (Float) -> Unit) =
        downloadModel(context, variant, onProgress)

    companion object {
        suspend fun downloadModel(context: Context, variant: ModelVariant, onProgress: (Float) -> Unit) {
            withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "llm").also { it.mkdirs() }
                val fileName = variant.modelFile.substringAfterLast('/')
                val target = File(dir, fileName)
                val tmp = File(dir, "$fileName.tmp")
                val connection = (URL(variant.downloadUrl).openConnection() as HttpURLConnection).also {
                    it.instanceFollowRedirects = true
                    it.connect()
                }
                try {
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) throw IOException("Server returned HTTP $code")
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                ensureActive()
                                output.write(buf, 0, read)
                                downloaded += read
                                onProgress(if (total > 0L) downloaded.toFloat() / total else 0f)
                            }
                        }
                    }
                    check(tmp.renameTo(target)) { "Failed to save model file" }
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    fun generate(prompt: String): Flow<String> = flow {
        val conv = freshConversation()
        conv.sendMessageAsync(Contents.of(Content.Text(prompt)))
            .collect { message -> message.extractText().let { if (it.isNotEmpty()) emit(it) } }
    }.flowOn(Dispatchers.IO)

    fun generate(prompt: String, image: Bitmap): Flow<String> = flow {
        val conv = freshConversation()
        val tmpFile = File(context.cacheDir, "ai_img.jpg").also { file ->
            file.outputStream().use { out ->
                image.scaleToMax(MAX_IMAGE_PX).compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }
        conv.sendMessageAsync(
            Contents.of(Content.ImageFile(tmpFile.absolutePath), Content.Text(prompt)),
        ).collect { message -> message.extractText().let { if (it.isNotEmpty()) emit(it) } }
    }.flowOn(Dispatchers.IO)

    fun close() {
        engine?.close()
        engine = null
    }

    private fun freshConversation(): Conversation {
        val e = requireNotNull(engine) { "Call initialize() first" }
        return e.createConversation(ConversationConfig())
    }

    private fun Message.extractText(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun Bitmap.scaleToMax(maxPx: Int): Bitmap {
        if (width <= maxPx && height <= maxPx) return this
        val scale = maxPx.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }
}
