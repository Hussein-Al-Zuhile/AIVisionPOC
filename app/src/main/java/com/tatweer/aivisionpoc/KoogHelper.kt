package com.tatweer.aivisionpoc

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.executor.clients.litert.LiteRTClientConfig
import ai.koog.prompt.executor.clients.litert.LiteRTLLMClient
import ai.koog.prompt.executor.clients.litert.LiteRTLLModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import android.content.Context
import android.util.Log
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
            ),
        ).also {
            client = it
            activeVariant = variant
        }
    }

    @OptIn(InternalAgentsApi::class)
    suspend fun run(prompt: String, variant: ModelVariant): String {
        val model = variant.koogModel()
        val executor = MultiLLMPromptExecutor(clientFor(variant))

        val twoStageStrategy = strategy<String, String>("two-stage") {
            // Stage 1: distill raw user prompt into a single clear sentence (no device tools needed)
            val distill by subgraphWithTask<String, String>(
                name = "distill",
                inputType = typeToken<String>(),
                outputType = typeToken<String>(),
                toolSelectionStrategy = ToolSelectionStrategy.NONE,
            ) { rawPrompt ->
                Log.d("KoogSubgraph", "Stage 1 (distill) running")
                "Restate the following as one clear, standalone question or request. " +
                        "Call finalize_task_result with only that restated sentence, nothing else. " +
                        "Input: \"$rawPrompt\""
            }

            // Stage 2: answer the distilled question with device tools available
            val answer by subgraphWithTask<String, String>(
                name = "answer",
                inputType = typeToken<String>(),
                outputType = typeToken<String>(),
                toolSelectionStrategy = ToolSelectionStrategy.ALL,
            ) { distilledQuestion ->
                Log.d("KoogSubgraph", "Stage 2 (answer) received: $distilledQuestion")
                "Answer the following question concisely. " +
                        "Use the getCurrentDateTime tool if the question involves time or date. " +
                        "Call finalize_task_result with your answer when done. " +
                        "Question: \"$distilledQuestion\""
            }

            nodeStart then distill then answer then nodeFinish
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = twoStageStrategy,
            toolRegistry = ToolRegistry {
                tools(DeviceTools())
            },
            systemPrompt = "You are a helpful AI assistant running locally on this Android device.",
            maxIterations = 100,
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
    fun getCurrentDateTime(): String {
        val result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        Log.d("KoogTool", "getCurrentDateTime() called → $result")
        return result
    }
}
