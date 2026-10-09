package com.example.localai.engine

import com.example.localai.model.ModelConfig
import com.example.localai.model.PerfStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * 模拟推理引擎。
 *
 * 作用：在尚未接入 llama.cpp 时，让整个 App 的 UI、状态流、性能指标都能跑通。
 * 这样可以把「UI 问题」和「native 问题」分开调试，降低风险。
 */
class MockEngine : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(PerfStats())
    override val stats: StateFlow<PerfStats> = _stats.asStateFlow()

    override suspend fun loadModel(config: ModelConfig): Result<Unit> {
        _state.value = EngineState.Loading
        delay(600) // 模拟加载耗时
        _state.value = EngineState.Ready(config.name)
        return Result.success(Unit)
    }

    override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
        val reply = buildReply(prompt)
        val startTime = System.currentTimeMillis()
        var firstLatencyMs = 0L
        var tokenCount = 0

        reply.forEachIndexed { index, ch ->
            if (index == 0) {
                delay(Random.nextLong(200L, 600L))
                firstLatencyMs = System.currentTimeMillis() - startTime
            } else {
                delay(Random.nextLong(15L, 40L))
            }

            tokenCount++
            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            _stats.value = PerfStats(
                firstTokenLatencyMs = firstLatencyMs,
                tokensPerSecond = tokenCount * 1000f / elapsedMs,
                generatedTokens = tokenCount,
                memoryMb = Random.nextInt(1180, 1520)
            )

            emit(ch.toString())

            if (tokenCount >= params.maxTokens) return@flow
        }
    }

    override fun unload() {
        _state.value = EngineState.Idle
        _stats.value = PerfStats()
    }

    private fun buildReply(prompt: String): String {
        val clipped = if (prompt.length > 50) prompt.take(50) + "…" else prompt
        return """
            这是一条来自本地模型的模拟回复（Mock 模式）。

            你的输入是：「$clipped」

            当前运行的是 MockEngine，用于在未接入真实模型时验证界面与数据流。
            接入 llama.cpp 后，这里会替换为真实的端侧推理输出。

            整个过程不联网，数据不会离开这台设备。
        """.trimIndent()
    }
}