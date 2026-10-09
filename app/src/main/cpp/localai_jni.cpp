#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "LocalAI-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================
//  两种实现：
//    USE_LLAMA_CPP 未定义 -> stub（验证调用链）
//    USE_LLAMA_CPP 已定义 -> llama.cpp 真实推理
//
//  ✅ 已核对 llama.cpp commit 8a1a9b5 (2026-10-09)。
//     若你更新 llama.cpp 后编译报错，请对照 include/llama.h 核对函数签名。
//
//  ⚠️ 性能关键：decode 时必须显式设置 batch.pos。
//     用 llama_batch_get_one() 会把位置固定为 0，
//     导致每个 token 都重算整个序列（实测慢 10-30 倍）。
// ============================================================

#ifdef USE_LLAMA_CPP
#include "llama.h"

static llama_model*       g_model   = nullptr;
static llama_context*     g_ctx     = nullptr;
static const llama_vocab* g_vocab   = nullptr;
static llama_sampler*     g_sampler = nullptr;

static int g_n_generated = 0;   // 本轮已生成 token 数
static int g_max_tokens  = 512; // 本轮上限
static int g_n_past      = 0;   // KV cache 游标（已 decode 的位置数）

static void release_all() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);           g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);   g_model   = nullptr; }
    g_vocab       = nullptr;
    g_n_generated = 0;
    g_n_past      = 0;
}
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_localai_engine_NativeBridge_nativeVersion(JNIEnv *env, jobject /* this */) {
#ifdef USE_LLAMA_CPP
    return env->NewStringUTF("localai-native 0.3.0 (llama.cpp)");
#else
    return env->NewStringUTF("localai-native 0.1.0 (stub)");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_localai_engine_NativeBridge_nativeInit(
        JNIEnv *env, jobject /* this */,
        jstring modelPath, jint threads, jint contextSize) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

#ifdef USE_LLAMA_CPP
    release_all();
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;              // 先做纯 CPU

    // Android 上 mmap 的模型页容易被系统回收（或被 lazy 加载拖慢），
    // 导致每次前向传播都回落到 flash 读权重。
    // LLAMA_LOAD_MODE_MLOCK：直接读入物理内存并锁定，不换出、不按需加载。
    // 可选值见 llama.h 的 enum llama_load_mode。
    mparams.load_mode = LLAMA_LOAD_MODE_MLOCK;

    LOGI("加载模型: %s", path);
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("模型加载失败");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = contextSize;
    cparams.n_threads       = threads;
    cparams.n_threads_batch = threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("上下文创建失败");
        release_all();
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("获取 vocab 失败");
        release_all();
        return JNI_FALSE;
    }

    // 采样链：top-k -> top-p -> temperature -> 随机分布
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("模型就绪: threads=%d ctx=%d", threads, contextSize);
    return JNI_TRUE;
#else
    LOGI("nativeInit(stub): path=%s threads=%d ctx=%d", path, threads, contextSize);
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
#endif
}

/**
 * 设置 prompt 并预填充（prefill）。
 * @return 消耗的 prompt token 数，<0 表示失败
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_localai_engine_NativeBridge_nativeSetPrompt(
        JNIEnv *env, jobject /* this */, jstring prompt, jint maxTokens) {

    const char *text = env->GetStringUTFChars(prompt, nullptr);
    std::string s = text ? text : "";
    if (text) env->ReleaseStringUTFChars(prompt, text);

#ifdef USE_LLAMA_CPP
    if (!g_ctx || !g_vocab) return -1;

    g_n_generated = 0;
    g_max_tokens  = maxTokens;

    // 清空 KV cache。新版 llama.cpp 已移除 llama_kv_cache_clear，改用 memory API。
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // tokenize：先问长度，再填充
    int n_tokens = -llama_tokenize(g_vocab, s.c_str(), (int32_t)s.size(),
                                   nullptr, 0, true, true);
    if (n_tokens <= 0) return -1;

    std::vector<llama_token> tokens(n_tokens);
    if (llama_tokenize(g_vocab, s.c_str(), (int32_t)s.size(),
                       tokens.data(), (int32_t)tokens.size(), true, true) < 0) {
        return -1;
    }

    // prefill：把整个 prompt 从位置 0 开始喂入。
    // 注意：这里用 llama_batch_get_one 是安全的——它会自动把 pos 设为 0..n-1。
    // 真正需要手写位置的是「增量 decode」（见 nativeNextToken）。
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());

    LOGI("prefill 开始: %d tokens", n_tokens);
    int rc = llama_decode(g_ctx, batch);
    LOGI("prefill decode 返回: rc=%d", rc);

    if (rc != 0) {
        LOGE("prefill 失败 (rc=%d)", rc);
        return -1;
    }

    g_n_past = (int)tokens.size();
    LOGI("prefill 完成: %d tokens", n_tokens);
    return n_tokens;
#else
    LOGI("nativeSetPrompt(stub): len=%d max=%d", (int)s.size(), maxTokens);
    return (jint)s.size();
#endif
}

/**
 * 生成下一个 token。
 * @return token 文本片段；返回 null 表示生成结束
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_localai_engine_NativeBridge_nativeNextToken(JNIEnv *env, jobject /* this */) {
#ifdef USE_LLAMA_CPP
    if (!g_ctx || !g_vocab || !g_sampler) return nullptr;
    if (g_n_generated >= g_max_tokens)  return nullptr;

    // 采样下一个 token
    llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);

    // 结束符
    if (llama_vocab_is_eog(g_vocab, id)) return nullptr;

    // token -> 文本片段
    char buf[512];
    int n = llama_token_to_piece(g_vocab, id, buf, sizeof(buf), 0, true);
    if (n < 0) return nullptr;

    // 增量 decode：与官方 simple-chat 一致，pos 由 llama.cpp 自动分配
    llama_batch batch = llama_batch_get_one(&id, 1);
    int rc = llama_decode(g_ctx, batch);
    if (rc != 0) {
        LOGE("增量 decode 失败 (rc=%d)", rc);
        return nullptr;
    }

    g_n_past++;
    g_n_generated++;

    return env->NewStringUTF(std::string(buf, n).c_str());
#else
    static const char* kStub = "[native-stub] 真实推理尚未启用，请设置 USE_LLAMA_CPP=ON。";
    static int i = 0;
    size_t len = strlen(kStub);
    if (i >= (int)len) { i = 0; return nullptr; }
    std::string piece(1, kStub[i++]);
    return env->NewStringUTF(piece.c_str());
#endif
}

/**
 * 返回当前进程的常驻内存（RSS，单位 MB）。
 * 用 /proc/self/statm 而不是 JVM 堆，这样才包含 llama.cpp 占用的 native 内存。
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_localai_engine_NativeBridge_nativeMemoryMb(JNIEnv * /* env */, jobject /* this */) {
    FILE *f = fopen("/proc/self/statm", "r");
    if (!f) return -1;

    long total = 0, resident = 0;
    if (fscanf(f, "%ld %ld", &total, &resident) != 2) {
        fclose(f);
        return -1;
    }
    fclose(f);

    long pageKb = sysconf(_SC_PAGESIZE) / 1024;
    return (jint)(resident * pageKb / 1024);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_localai_engine_NativeBridge_nativeFree(JNIEnv * /* env */, jobject /* this */) {
#ifdef USE_LLAMA_CPP
    release_all();
    llama_backend_free();
    LOGI("已释放模型");
#else
    LOGI("nativeFree(stub)");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_localai_engine_NativeBridge_nativeEcho(
        JNIEnv *env, jobject /* this */, jstring input) {

    const char *text = env->GetStringUTFChars(input, nullptr);
    std::string out = std::string("[native] ") + (text ? text : "");
    if (text) env->ReleaseStringUTFChars(input, text);
    return env->NewStringUTF(out.c_str());
}