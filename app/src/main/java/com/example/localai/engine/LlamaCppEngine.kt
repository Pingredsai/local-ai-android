package com.example.localai.engine

import com.example.localai.model.ModelConfig
import com.example.localai.model.PerfStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * llama.cpp 真实端侧推理引擎。
 *
 * 工作方式：native 层维护推理状态，Kotlin 侧循环拉取 token，
 * 从而在不使用 JNI 回调的情况下实现**流式输出**。
 *
 * 需要在 native 层开启 USE_LLAMA_CPP 编译开关。
 */
class LlamaCppEngine(private val modelPath: String) : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(PerfStats())
    override val stats: StateFlow<PerfStats> = _stats.asStateFlow()

    override suspend fun loadModel(config: ModelConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!NativeBridge.isAvailable) {
                _state.value = EngineState.Error("native 库不可用")
                return@withContext Result.failure(IllegalStateException("native 库未加载"))
            }

            _state.value = EngineState.Loading
            val ok = try {
                NativeBridge.nativeInit(modelPath, config.threads, config.contextSize)
            } catch (t: Throwable) {
                false
            }

            if (ok) {
                _state.value = EngineState.Ready(config.name)
                Result.success(Unit)
            } else {
                _state.value = EngineState.Error("模型加载失败")
                Result.failure(IllegalStateException("nativeInit 返回 false"))
            }
        }

    override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
        if (!NativeBridge.isAvailable) return@flow

        val promptTokens = NativeBridge.nativeSetPrompt(applyChatTemplate(prompt), params.maxTokens)
        if (promptTokens < 0) {
            emit("[错误] prompt 预处理失败")
            return@flow
        }

        val startTime = System.currentTimeMillis()
        var firstLatencyMs = 0L
        var count = 0

        while (true) {
            val piece = NativeBridge.nativeNextToken() ?: break

            if (count == 0) firstLatencyMs = System.currentTimeMillis() - startTime
            count++

            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            _stats.value = PerfStats(
                firstTokenLatencyMs = firstLatencyMs,
                tokensPerSecond = count * 1000f / elapsedMs,
                generatedTokens = count,
                memoryMb = currentMemoryMb()
            )

            emit(piece)
        }
    }.flowOn(Dispatchers.Default)

    override fun unload() {
        if (NativeBridge.isAvailable) {
            runCatching { NativeBridge.nativeFree() }
        }
        _state.value = EngineState.Idle
        _stats.value = PerfStats()
    }

    /**
     * 套用 Qwen2.5 的 ChatML 模板。
     *
     * 不套模板的话，模型会把输入当成"待续写的文本"而不是"用户说的话"，
     * 表现就是答非所问、复读、胡乱接上下文。
     */
    private fun applyChatTemplate(userInput: String): String = buildString {
        append("<|im_start|>system").append('\n')
        append("你是Qwen，一个由阿里云开发的人工智能助手。你是一个乐于助人的助手，请用简洁的中文回答问题。")
        append("<|im_end|>").append('\n')
        append("<|im_start|>user").append('\n')
        append(userInput)
        append("<|im_end|>").append('\n')
        append("<|im_start|>assistant").append('\n')
    }

    /** 读取进程 RSS（含 native 内存），比 JVM 堆准确得多 */
    private fun currentMemoryMb(): Int {
        if (!NativeBridge.isAvailable) return 0
        return runCatching { NativeBridge.nativeMemoryMb() }.getOrDefault(0)
    }
}