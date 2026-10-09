package com.example.localai.engine

import com.example.localai.model.ModelConfig
import com.example.localai.model.PerfStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 引擎状态 */
sealed interface EngineState {
    data object Idle : EngineState
    data object Loading : EngineState
    data class Ready(val modelName: String) : EngineState
    data class Error(val message: String) : EngineState
}

/**
 * 推理引擎抽象。
 *
 * 设计目的：把「推理后端」与「UI / 业务逻辑」解耦。
 * 实现类：
 *  - [MockEngine]       模拟输出，用于无模型时开发 UI
 *  - LlamaCppEngine     真实端侧推理（后续接入，见技术方案 D2/D3）
 *  - MediaPipeEngine    兜底实现（见技术方案 7.5）
 */
interface LlmEngine {

    val state: StateFlow<EngineState>

    val stats: StateFlow<PerfStats>

    /** 加载模型。耗时操作，必须在协程中调用 */
    suspend fun loadModel(config: ModelConfig): Result<Unit>

    /**
     * 生成回复。
     *
     * @param prompt 用户输入
     * @return 文本增量流（逐 token / 逐块），UI 层直接 append 即可实现流式显示
     */
    fun generate(prompt: String, params: InferenceParams = InferenceParams()): Flow<String>

    /** 释放模型，回收内存 */
    fun unload()
}