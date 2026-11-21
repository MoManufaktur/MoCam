cmake_minimum_required(VERSION 3.18)
project(mocamwhisper C CXX)

# whisper.cpp (Whisper-Spracherkennung, offline) wird beim Bauen aus dem offiziellen Release geholt
include(FetchContent)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
# Kirin 980 (Cortex-A76) und alle neueren Handys: ARMv8.2 mit fp16 und dotprod -> deutlich schneller
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

FetchContent_Declare(whisper
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz)
FetchContent_MakeAvailable(whisper)

add_library(mocamwhisper SHARED whisper_jni.cpp)
target_compile_options(mocamwhisper PRIVATE -O3)
target_link_libraries(mocamwhisper PRIVATE whisper log)
