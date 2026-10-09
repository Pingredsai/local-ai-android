package com.example.localai.engine

import android.util.Log

/**
 * Kotlin <-> C++ 的桥接入口。
 *
 * 设计要点：**加载失败不崩溃**。如果 native 库缺失（例如 ABI 不匹配），
 * App 仍能以 [isAvailable] == false 的状态运行，方便定位问题。
 *
 * ⚠️ [isAvailable] 为 false 时调用任何 native* 方法都会抛 UnsatisfiedLinkError，
 * 调用方必须先判断。
 */
object NativeBridge {

    private const val TAG = "LocalAI-Native"

    var isAvailable: Boolean = false
        private set

    init {
        isAvailable = try {
            System.loadLibrary("localai")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "native 库加载失败: ${e.message}")
            false
        }
    }

    /** native 层版本号，用于验证调用链是否打通 */
    external fun nativeVersion(): String

    /** 初始化模型 */
    external fun nativeInit(modelPath: String, threads: Int, contextSize: Int): Boolean

    /**
     * 设置 prompt 并预填充。
     * @return 消耗的 prompt token 数，< 0 表示失败
     */
    external fun nativeSetPrompt(prompt: String, maxTokens: Int): Int

    /**
     * 生成下一个 token。
     * @return 文本片段；null 表示生成结束
     */
    external fun nativeNextToken(): String?

    /** 当前进程 RSS（MB），包含 llama.cpp 占用的 native 内存 */
    external fun nativeMemoryMb(): Int

    /** 释放模型 */
    external fun nativeFree()

    /** 回声测试，验证 JNI 参数传递正常 */
    external fun nativeEcho(input: String): String
}
