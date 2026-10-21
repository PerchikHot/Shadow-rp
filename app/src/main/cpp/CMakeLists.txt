cmake_minimum_required(VERSION 3.22.1)
project(shadowrp_native LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
include(FetchContent)
FetchContent_Declare(llama
  GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
  GIT_TAG b5400
  GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama)
add_library(shadowrp_llm SHARED native_llm.cpp)
target_include_directories(shadowrp_llm PRIVATE ${llama_SOURCE_DIR}/include)
target_link_libraries(shadowrp_llm PRIVATE llama log)
