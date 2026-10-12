# ============================================================
#  LocalAI native 层
#
#  两种模式：
#    USE_LLAMA_CPP=OFF（默认）→ 编译 stub，保证开箱可编译
#    USE_LLAMA_CPP=ON         → 链接 llama.cpp，启用真实推理
#
#  启用方式：
#    1) 将 llama.cpp 放到 <项目根>/third_party/llama.cpp
#    2) 在 app/build.gradle.kts 的 externalNativeBuild.cmake.arguments
#       中加入 "-DUSE_LLAMA_CPP=ON"
# ============================================================

cmake_minimum_required(VERSION 3.22.1)
project("localai" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================
#  ⚠️ 关键：强制优化等级
#
#  Android Gradle Plugin 在 debug variant 下会向 CMake 传
#  CMAKE_BUILD_TYPE=Debug，而 CMake 的 Debug 配置默认**不带任何
#  -O 优化标志**（等同 -O0）。ggml 在无优化下比 Release 慢 20-50 倍。
#
#  实测（Pixel 4 / Qwen2.5-1.5B Q4_K_M）：
#    -O0  → 0.5 tok/s
#    -O3  → 见 README 的 benchmark
#
#  这个设置必须在 add_subdirectory(llama.cpp) 之前，否则不会生效。
#  （诊断思路来自 DEV 读者：编译优化等级比 -march 更容易被忽略）
# ============================================================
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_C_FLAGS_DEBUG   "-O3 -g" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -g" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELWITHDEBINFO   "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)

message(STATUS "LocalAI: CMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}, 强制 -O3 优化")

option(USE_LLAMA_CPP "Link against llama.cpp for real inference" OFF)

add_library(localai SHARED
        localai_jni.cpp)

find_library(log-lib log)

target_link_libraries(localai
        ${log-lib})

if(USE_LLAMA_CPP)
    message(STATUS "LocalAI: 启用 llama.cpp 真实推理")

    set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")

    if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
        message(FATAL_ERROR
                "找不到 llama.cpp: ${LLAMA_DIR}\n"
                "请先 clone: git clone --recursive https://github.com/ggml-org/llama.cpp third_party/llama.cpp")
    endif()

    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

    add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

    target_compile_definitions(localai PRIVATE USE_LLAMA_CPP=1)
    target_link_libraries(localai llama)
endif()