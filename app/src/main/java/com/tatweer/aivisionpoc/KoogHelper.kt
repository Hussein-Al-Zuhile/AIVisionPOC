package com.tatweer.aivisionpoc

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.clients.litert.LiteRTClientConfig
import ai.koog.prompt.executor.clients.litert.LiteRTLLMClient
import ai.koog.prompt.executor.clients.litert.LiteRTLLModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class KoogHelper(context: Context) : AutoCloseable {

    private val modelsPath = "${context.filesDir.absolutePath}/llm"
    private var client: LiteRTLLMClient? = null
    private var activeVariant: ModelVariant? = null

    private fun clientFor(variant: ModelVariant): LiteRTLLMClient {
        if (activeVariant != variant) {
            client?.close()
            client = null
        }
        return client ?: LiteRTLLMClient(
            LiteRTClientConfig(
                defaultModel = variant.koogModel(),
                modelsPath = modelsPath,
            )
        ).also {
            client = it
            activeVariant = variant
        }
    }

    suspend fun run(prompt: String, variant: ModelVariant): String {
        val model = variant.koogModel()
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(clientFor(variant)),
            llmModel = model,
            systemPrompt = "You are a helpful AI assistant running locally on this Android device.",
            toolRegistry = ToolRegistry { tools(DeviceTools()) },
            maxIterations = 5,
        )
        return agent.run(prompt)
    }

    override fun close() {
        client?.close()
        client = null
    }

    private fun ModelVariant.koogModel(): LLModel = when (this) {
        ModelVariant.FAST -> LiteRTLLModels.Gemma4E2B
        ModelVariant.THINKING -> LiteRTLLModels.Gemma4E4B
    }
}

@LLMDescription("Tools that provide real-time information from the Android device")
class DeviceTools : ToolSet {
    @Tool
    @LLMDescription("Returns the current date and time on the device")
    fun getCurrentDateTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
