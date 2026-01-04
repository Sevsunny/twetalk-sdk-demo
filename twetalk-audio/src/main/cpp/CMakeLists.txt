
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("twetalk-audio")
set(CMAKE_CXX_STANDARD 17)

# Set C++ compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(opus_jni SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
        opus_jni.cpp)

# 头文件（Opus 的 include）
target_include_directories(opus_jni PRIVATE
        ${CMAKE_SOURCE_DIR}/include
)

# 引入预编译的共享库（位于 jniLibs）
add_library(opus SHARED IMPORTED GLOBAL)
set(OPUS_JNILIBS_DIR ${CMAKE_SOURCE_DIR}/../../main/jniLibs)

# Enable RTTI and exceptions
target_compile_options(opus_jni PRIVATE
    -fexceptions
    -frtti
)

# 指定不同 ABI 的 so 路径
set_target_properties(opus PROPERTIES
        IMPORTED_LOCATION ${OPUS_JNILIBS_DIR}/${ANDROID_ABI}/libopus.so
        INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_SOURCE_DIR}/include
)
# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(opus_jni
    opus
    android
    log)
