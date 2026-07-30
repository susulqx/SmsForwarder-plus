cmake_minimum_required(VERSION 3.10.2)

project("leoric")

# 设置 C 标准
set(CMAKE_C_STANDARD 99)

# 查找 Android log 库（必须在 target_link_libraries 之前）
find_library(log-lib log)

# Leoric 保活 JNI 库
add_library(leoric SHARED
        leoric.c
)

# 链接 Android log 库
target_link_libraries(leoric
        ${log-lib}
)
