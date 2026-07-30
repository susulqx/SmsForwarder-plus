cmake_minimum_required(VERSION 3.10.2)

project("leoric")

# 设置 C 标准
set(CMAKE_C_STANDARD 99)

# Leoric 保活 JNI 库
add_library(leoric SHARED
        leoric.c
)

# 链接 Android log 库
target_link_libraries(leoric
        ${log-lib}
)

# 查找 log 库
find_library(log-lib log)
