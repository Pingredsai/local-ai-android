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