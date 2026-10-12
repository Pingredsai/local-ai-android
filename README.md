# LocalAI · Android 端侧大模型助手

> 🇬🇧 English documentation: [README.en.md](README.en.md)

一台普通 Android 手机，**完全断网**也能对话的本地 AI 助手。

> 当前状态：**项目骨架 + 真实推理均已实现**（通过开关启用）。
> 默认以 `MockEngine` 运行，**开箱即可编译**（不需要 NDK）；通过开关启用 llama.cpp 真实推理（见第 7 节）。

---

## 演示视频

**[`screenshots/demo-fast.mp4`](screenshots/demo-fast.mp4)** —— 58 秒，4 倍速播放。

展示内容：
1. 开启飞行模式（确认无网络）
2. 启动 App，模型在本地加载
3. 完整对话过程，逐字流式输出
4. 顶部实时性能指标（首 token 延迟 / tok/s / 内存）

---
## 1. 当前能做什么

- ✅ 完整的聊天界面（Jetpack Compose）
- ✅ 流式输出（逐字显示）
- ✅ 实时性能指标（首 token 延迟 / tok/s / 内存）
- ✅ 引擎抽象层（`LlmEngine`），可无痛切换后端
- ✅ native 层已打通（JNI + llama.cpp 真实推理实现）
- ✅ 真实模型推理（llama.cpp JNI，编译开关控制，默认关闭）

**现在跑起来看到的是 Mock 输出，不是真模型。** 这是有意设计——先让整个 App 跑通，再替换引擎。

---

## 2. 环境要求

| 项 | 版本 | 说明 |
|---|---|---|
| Android Studio | 最新稳定版 | 必需 |
| JDK | 17 | Android Studio 自带 |
| Android SDK | API 35 (compileSdk) | AS 会提示安装 |
| NDK | 30.0.16248370 | ⚠️ **仅启用 native 时必需**（当前默认不需要） |
| CMake | 4.1.2 | ⚠️ **仅启用 native 时必需** |
| Gradle | 8.9 | 见 `gradle/wrapper/gradle-wrapper.properties` |
| 最低 Android | 10 (API 29) | minSdk |
| 设备 ABI | arm64-v8a | 只编这一种，见技术方案 7.5 |

---

## 3. 如何运行

### 方式一：Android Studio（推荐）

1. Android Studio → **Open** → 选择本目录
2. 首次 Sync 会自动下载 Gradle 与依赖（**默认不需要 NDK / CMake**）
3. 连接真机（开启 USB 调试）或启动模拟器
4. 点击 Run

### 方式二：命令行

> ⚠️ 本仓库**未包含 `gradle-wrapper.jar`**（二进制文件）。
> 你需要先生成 wrapper，或用本机已安装的 Gradle。

```bash
# 如果本机装了 Gradle 8.9+
gradle wrapper --gradle-version 8.9

# 然后
./gradlew installDebug
```

`gradle-wrapper.jar` 也可以从任意其他 Android 项目复制过来。

---

## 4. 项目结构

```
local-ai-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml          依赖版本集中管理
│   └── wrapper/gradle-wrapper.properties
├── 技术方案.md                     完整技术方案（必读）
└── app/
    ├── build.gradle.kts            NDK / CMake / Compose 配置
    ├── proguard-rules.pro          保留 JNI 方法
    └── src/main/
        ├── AndroidManifest.xml     无网络权限（离线是卖点）
        ├── cpp/
        │   ├── CMakeLists.txt
        │   └── localai_jni.cpp     JNI stub，待接入 llama.cpp
        ├── java/com/example/localai/
        │   ├── MainActivity.kt
        │   ├── engine/
        │   │   ├── LlmEngine.kt        引擎抽象接口
        │   │   ├── InferenceParams.kt  采样参数
        │   │   ├── MockEngine.kt       模拟引擎（当前默认）
        │   │   └── NativeBridge.kt     JNI 桥接
        │   ├── model/
        │   │   ├── ChatMessage.kt
        │   │   ├── ModelConfig.kt
        │   │   └── PerfStats.kt
        │   └── ui/
        │       ├── ChatScreen.kt       Compose 聊天界面
        │       ├── ChatViewModel.kt    状态与流式逻辑
        │       └── theme/Theme.kt
        └── res/values/{strings,themes}.xml
```

---

## 5. 架构说明

```
Compose UI  (ChatScreen)
     ↓  StateFlow
ViewModel   (ChatViewModel)
     ↓
LlmEngine   (接口)
     ├── MockEngine        默认（无需模型）
     ├── MediaPipeEngine   兜底方案
     └── LlamaCppEngine    真实端侧推理
              ↓
        NativeBridge → C++ / llama.cpp
```

**为什么先做 MockEngine**：把「UI 调试」和「native 调试」彻底分开。UI 有问题时，你不必怀疑是不是模型没加载好。

---

## 6. 常见问题

### Q: NDK / CMake 报错找不到？
**默认配置不需要它们。** 如果报错，检查 `gradle.properties` 里 `localai.enableNative` 是否为 `false`。
只有启用 native 时才需要：SDK Manager → SDK Tools → 勾选 **NDK (Side by side)** 和 **CMake**。

### Q: `ndkVersion` 不匹配？
仅当 `localai.enableNative=true` 时相关。此时把 `app/build.gradle.kts` 里的 `ndkVersion` 改成本机已装版本。

### Q: 编译报路径过长的错误？
Windows 260 字符限制。**把项目复制到短路径**（如 `D:\dev\localai`）再编译。

### Q: 编译很慢？
- 确认 `abiFilters` 只有 `arm64-v8a`
- 杀毒软件把项目目录加入排除项
- 用 Release 变体编译 native

### Q: `UnsatisfiedLinkError: dlopen failed`（仅 native 模式）
native 库没打进 APK。检查 ABI 是否匹配设备、`CMakeLists.txt` 是否被正确引用。

### Q: 想改包名？
全局替换 `com.example.localai`，并同步修改 `localai_jni.cpp` 里的 JNI 函数名（`Java_com_example_localai_engine_NativeBridge_*`）。

---

## 6.5 实测性能数据

**真机实测，不是估算。**

| 设备 | 芯片 | 模型 | 量化 | 首 token | 生成速度 | 峰值内存 |
|---|---|---|---|---|---|---|
| Google Pixel 4 | 骁龙 855（2019） | Qwen2.5-1.5B-Instruct | Q4_K_M | **52 ms** | **18.5 tok/s** | ~1.3 GB |

**结论**：
- ✅ 完全离线可用，18.5 tok/s，对话流畅
- ⚡ **给要自己编译的人提个醒**：务必用 `-O3` 构建。Debug（`-O0`）下 ggml 会慢 **37 倍**，我在这上面浪费了几天
- 📊 峰值内存约 1.3 GB；纯 CPU 推理，未使用 GPU/NPU

> 欢迎提交你设备上的实测数据。我特别想看到**骁龙 8 Gen 2/3** 的结果，
> 理论上同模型会快 10-20 倍。
## 7. 启用真实端侧推理（已实现，默认关闭）

native 层与 Kotlin 引擎**已全部写好**，默认通过编译开关关闭，保证项目开箱可编译。
要跑真实推理，做 4 件事：

### 步骤 1 · 放入 llama.cpp

```bash
cd <项目根>
git clone --recursive https://github.com/ggml-org/llama.cpp third_party/llama.cpp
```

> ⚠️ 用 `git clone --recursive`，不要依赖 submodule——Windows 下符号链接常出问题。

### 步骤 2 · 打开编译开关

编辑 `gradle.properties`，把开关打开：

```kotlin
localai.enableNative=true
```

### 步骤 3 · 准备模型文件

从 HuggingFace 下载一个 GGUF 小模型（搜 `Qwen2.5-1.5B-Instruct-GGUF`，选 `Q4_K_M`），推到设备：

```bash
adb push Qwen2.5-1.5B-Instruct-Q4_K_M.gguf /data/local/tmp/
adb shell run-as com.example.localai mkdir -p files/models
adb shell "cat /data/local/tmp/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf | run-as com.example.localai sh -c 'cat > files/models/model.gguf'"
```

App 启动时会自动扫描 `files/models/` 下的 `.gguf` 文件。

### 步骤 4 · 打开推理开关

编辑 `AppConfig.kt`：

```kotlin
const val USE_REAL_INFERENCE = true
```

重新 Run。

### 引擎自动降级（不会崩溃）

| 情况 | 结果 |
|---|---|
| `USE_REAL_INFERENCE = false` | MockEngine |
| native 库加载失败 | MockEngine |
| 找不到 `.gguf` | MockEngine |
| 全部就绪 | **LlamaCppEngine（真实推理）** |

### ⚠️ 关于 llama.cpp API 版本

`localai_jni.cpp` 基于较新的 llama.cpp API 风格（`llama_vocab`、sampler chain、`llama_batch_get_one`）。
**llama.cpp 的 API 演进很快**，若编译报 "unknown function / no matching function"，请对照你所用版本的 `llama.h` 调整签名。这一步遇到问题把报错发我。

---
## 8. 许可

MIT License —— 可自由使用、修改、商用，保留版权声明即可。详见 [LICENSE](LICENSE)。

---

## 9. 关联文档

- `技术方案.md` —— 完整技术方案
- `../../docs/03-作品计划.md` —— 作品计划
- `../../docs/05-合规与红线.md` —— 合规要求（**必读**）
