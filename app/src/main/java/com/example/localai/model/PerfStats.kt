package com.example.localai.model

/** 推理性能指标，展示在 UI 顶部，也是作品的核心卖点证据 */
data class PerfStats(
    val firstTokenLatencyMs: Long = 0L,
    val tokensPerSecond: Float = 0f,
    val generatedTokens: Int = 0,
    val memoryMb: Int = 0
)