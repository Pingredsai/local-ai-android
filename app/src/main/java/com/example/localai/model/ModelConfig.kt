package com.example.localai.model

/**
 * 模型加载配置。
 *
 * @param name        显示名称，如 "Qwen2.5-1.5B-Instruct Q4_K_M"
 * @param path        模型文件在设备上的绝对路径（GGUF）
 * @param contextSize 上下文窗口大小，越大越吃内存
 * @param threads     推理线程数，一般取 CPU 大核数
 * @param backend     推理后端
 */
data class ModelConfig(
    val name: String,
    val path: String,
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val backend: Backend = Backend.CPU
) {
    enum class Backend { CPU, GPU }
}