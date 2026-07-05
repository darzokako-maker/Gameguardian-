cmake_minimum_required(VERSION 3.22.1)
project("stealth")

add_library(stealth SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(stealth ${log-lib})

