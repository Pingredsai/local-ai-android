package com.example.localai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localai.AppConfig
import com.example.localai.engine.EngineState
import com.example.localai.engine.InferenceParams
import com.example.localai.engine.LlamaCppEngine
import com.example.localai.engine.LlmEngine
import com.example.localai.engine.MockEngine
import com.example.localai.engine.NativeBridge
import com.example.localai.model.ChatMessage
import com.example.localai.model.ModelConfig
import com.example.localai.model.PerfStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 聊天页 ViewModel。
 *
 * 引擎选择逻辑（自动降级，永不崩溃）：
 *   USE_REAL_INFERENCE = false                -> MockEngine
 *   native 库不可用                             -> MockEngine
 *   找不到 .gguf 模型                           -> MockEngine
 *   以上都满足                                  -> LlamaCppEngine
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: LlmEngine = buildEngine(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val engineState: StateFlow<EngineState> = engine.state
    val stats: StateFlow<PerfStats> = engine.stats

    private val idGen = AtomicLong(0L)
    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            engine.loadModel(
                ModelConfig(
                    name = displayName(application),
                    path = findModelPath(application) ?: "",
                    contextSize = AppConfig.CONTEXT_SIZE,
                    threads = AppConfig.THREADS
                )
            )
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isGenerating.value) return

        _input.value = ""
        appendMessage(ChatMessage.Role.USER, text)

        val assistantId = idGen.incrementAndGet()
        _messages.update { it + ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "") }
        _isGenerating.value = true

        generateJob = viewModelScope.launch {
            try {
                engine.generate(text, InferenceParams(maxTokens = AppConfig.MAX_TOKENS))
                    .collect { chunk ->
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantId) {
                                    msg.copy(content = msg.content + chunk)
                                } else {
                                    msg
                                }
                            }
                        }
                    }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stop() {
        generateJob?.cancel()
        _isGenerating.value = false
    }

    fun clear() {
        generateJob?.cancel()
        _messages.value = emptyList()
        _isGenerating.value = false
    }

    private fun appendMessage(role: ChatMessage.Role, content: String) {
        _messages.update { it + ChatMessage(idGen.incrementAndGet(), role, content) }
    }

    private fun buildEngine(app: Application): LlmEngine {
        if (!AppConfig.USE_REAL_INFERENCE) return MockEngine()
        if (!NativeBridge.isAvailable) return MockEngine()
        val path = findModelPath(app) ?: return MockEngine()
        return LlamaCppEngine(path)
    }

    private fun displayName(app: Application): String {
        val path = findModelPath(app)
        return if (path != null) File(path).name else "Mock 模型（未找到 .gguf）"
    }

    /** 在 app 私有目录 files/models/ 下查找第一个 .gguf 模型 */
    private fun findModelPath(app: Application): String? {
        val dir = File(app.filesDir, AppConfig.MODEL_DIR)
        if (!dir.exists()) return null
        return dir.listFiles { f -> f.isFile && f.extension.equals("gguf", ignoreCase = true) }
            ?.firstOrNull()
            ?.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
        engine.unload()
    }
}