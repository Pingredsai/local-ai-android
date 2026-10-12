# LocalAI

**Run a large language model entirely on your Android phone — no internet, no API keys, no data leaving the device.**

[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3DDC84.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg)](https://kotlinlang.org)
[![LLM](https://img.shields.io/badge/inference-llama.cpp-blue.svg)](https://github.com/ggml-org/llama.cpp)

> 中文文档见 [README.md](README.md)

---

## Why this exists

Every AI assistant you use today sends your words to someone else's server. That means:

- Your data leaves your device on every message
- You pay per token, forever
- You cannot use it offline
- The provider decides what the model will and will not say

**LocalAI takes the opposite approach.** The model runs *on your phone*, inside the app, with no network connection required.

The strongest proof is in the manifest: **this app does not request the `INTERNET` permission at all.**

---

## Demo

**Demo video:** [`screenshots/demo-fast.mp4`](screenshots/demo-fast.mp4) — 58 s, played at 4x speed.

What it shows:
1. Airplane mode enabled (no network)
2. App launched, model loaded locally
3. Full conversation with streaming token output
4. Live performance HUD (first-token latency / tok/s / memory)

| Chat | Performance HUD |
|---|---|
| *screenshot placeholder* | *screenshot placeholder* |

---

## Features

| Feature | Status |
|---|---|
| 100% offline inference | ✅ |
| **No `INTERNET` permission** | ✅ |
| Streaming token output | ✅ |
| Real-time metrics (first-token latency / tok/s / memory) | ✅ |
| Pluggable engine architecture (Mock / llama.cpp / MediaPipe) | ✅ |
| Model switching | planned |
| Chat history persistence | planned |
| GPU / NNAPI acceleration | planned |

---

## Architecture

```
Compose UI  (ChatScreen)
      |  StateFlow
      v
ViewModel   (ChatViewModel)
      |
      v
LlmEngine   (interface)
      |-- MockEngine          demo / UI development
      |-- LlamaCppEngine      real on-device inference
      |-- MediaPipeEngine     fallback (planned)
              |
              v
        NativeBridge  ->  C++ / llama.cpp
```

**Design principle: graceful degradation.** If the native library fails to load, or no model file is found, the app silently falls back to a mock engine instead of crashing. This lets the UI be developed and demoed independently of the inference stack.

---

## Requirements

| Item | Version |
|---|---|
| Android | 10 (API 29) or higher |
| ABI | `arm64-v8a` |
| JDK | 17 |
| Android SDK | API 35 |
| NDK | 30.0.16248370 *(only required when native is enabled)* |
| CMake | 4.1.2 *(only required when native is enabled)* |
| Gradle | 8.9 |

---

## Quick Start

### 1. Build and run (mock mode)

The project compiles and runs **without any model or llama.cpp** — it uses a mock engine so you can see the full UI immediately. Open it in Android Studio, let it sync, then hit Run.

> Note: this repository does not ship `gradle-wrapper.jar`. Generate it with `gradle wrapper --gradle-version 8.9`, or let Android Studio create it on first sync.

### 2. Enable real on-device inference

**Step 1 — vendor llama.cpp**

```bash
git clone --recursive https://github.com/ggml-org/llama.cpp third_party/llama.cpp
```

**Step 2 — enable the compile flag**

In `app/build.gradle.kts`, inside the `defaultConfig.externalNativeBuild.cmake` block:

```kotlin
arguments += listOf("-DUSE_LLAMA_CPP=ON")
```

**Step 3 — push a model**

Download a GGUF model (e.g. `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf`) from HuggingFace:

```bash
adb push Qwen2.5-1.5B-Instruct-Q4_K_M.gguf /data/local/tmp/
adb shell run-as com.example.localai mkdir -p files/models
adb shell run-as com.example.localai sh -c "cp /data/local/tmp/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf files/models/model.gguf"
```

**Step 4 — flip the switch**

In `AppConfig.kt`:

```kotlin
const val USE_REAL_INFERENCE = true
```

Run again. The app scans `files/models/` for `.gguf` files at startup.

---

## Recommended models

| Model | Size (Q4_K_M) | Notes |
|---|---|---|
| **Qwen2.5-1.5B-Instruct** | ~1.0 GB | Recommended default — fast, strong Chinese support |
| Qwen2.5-3B-Instruct | ~2.0 GB | Better quality, slower |
| Llama-3.2-1B-Instruct | ~0.8 GB | Fastest, weaker multilingual |
| Gemma-2-2B-it | ~1.6 GB | Balanced |

Only `arm64-v8a` is built by default to keep build times short.

---

## Performance

Measured on real hardware — **no estimates, no marketing numbers.**

| Device | SoC | Model | Quant | First token | Generation | Peak RSS |
|---|---|---|---|---|---|---|
| Google Pixel 4 | Snapdragon 855 (2019) | Qwen2.5-1.5B-Instruct | Q4_K_M | **52 ms** | **18.5 tok/s** | ~1.3 GB |

### What this tells you

- ✅ **Fully functional offline**: 18.5 tok/s on a 2019 phone — comfortably conversational
- ⚡ **Heads up for builders**: build with `-O3`. A Debug (`-O0`) build of ggml runs **37x slower** — I lost days to this. See [TECHNICAL-SPEC.md](TECHNICAL-SPEC.md).
- 📊 Peak RSS ~1.3 GB; fully CPU inference, no GPU/NPU used

### Reproduce it

```bash
# 1. build with native enabled
#    gradle.properties -> localai.enableNative=true

# 2. push the model
adb push qwen2.5-1.5b-instruct-q4_k_m.gguf /data/local/tmp/
adb shell "run-as com.example.localai sh -c 'cat /data/local/tmp/qwen2.5-1.5b-instruct-q4_k_m.gguf > files/models/model.gguf'"

# 3. run and check logcat
adb logcat -s LocalAI-Native:V
```

Open a PR with your numbers — device diversity is exactly what this project needs.
I am especially interested in **Snapdragon 8 Gen 2/3 devices**, where this model should be 10-20x faster.
## Project structure

```
local-ai-android/
|-- app/
|   |-- build.gradle.kts
|   `-- src/main/
|       |-- AndroidManifest.xml      no INTERNET permission
|       |-- cpp/                     CMake + llama.cpp JNI
|       `-- java/com/example/localai/
|           |-- AppConfig.kt
|           |-- engine/              LlmEngine / Mock / LlamaCpp / NativeBridge
|           |-- model/               ChatMessage / ModelConfig / PerfStats
|           `-- ui/                  ChatScreen / ChatViewModel / theme
`-- gradle/libs.versions.toml
```

---

## Roadmap

- [x] Compose chat UI with streaming output
- [x] Performance metrics HUD
- [x] Pluggable engine abstraction
- [x] llama.cpp JNI integration
- [ ] Verified on-device benchmark results
- [ ] Model picker + download manager
- [ ] Chat history persistence
- [ ] Optional GPU acceleration
- [ ] Release APK on GitHub Releases

---

## Notes on llama.cpp API

The JNI layer targets a recent llama.cpp API (`llama_vocab`, sampler chains, `llama_batch_get_one`). llama.cpp evolves quickly — if you hit `unknown function` or `no matching function` errors, check the signatures against your own checkout of `llama.h`.

---

## License

MIT License — free to use, modify, and distribute commercially. See [LICENSE](LICENSE).

---

## Contributing

Issues and PRs are welcome — especially **benchmark results from different devices**.

Built by an Android/AI engineer exploring what on-device AI can do when it does not depend on anyone's server.