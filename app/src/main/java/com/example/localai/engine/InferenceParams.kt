package com.example.localai.engine

/** 单次生成的采样参数 */
data class InferenceParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val contextSize: Int = 2048
)