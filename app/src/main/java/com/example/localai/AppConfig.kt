package com.example.localai

/**
 * 全局配置开关。
 *
 * 作品 A 默认跑 Mock 引擎（开箱即用）。
 * 启用真实端侧推理需要三步：
 *   1. 把 llama.cpp 放到 third_party/llama.cpp
 *   2. 在 app/build.gradle.kts 的 cmake arguments 里加 "-DUSE_LLAMA_CPP=ON"
 *   3. 把 .gguf 模型推到设备：adb push xxx.gguf /data/local/tmp/
 *      然后拷进 app 私有目录 files/models/（或用 adb run-as）
 *   最后把 [USE_REAL_INFERENCE] 改为 true
 */
object AppConfig {

    /** 是否启用 llama.cpp 真实推理 */
    const val USE_REAL_INFERENCE = true

    /** 模型子目录（相对 app filesDir） */
    const val MODEL_DIR = "models"

    /** 推理线程数，建议 = CPU 大核数 */
    const val THREADS = 4

    /** 上下文窗口大小 */
    const val CONTEXT_SIZE = 2048

    /** 单次生成最大 token 数 */
    const val MAX_TOKENS = 512
}