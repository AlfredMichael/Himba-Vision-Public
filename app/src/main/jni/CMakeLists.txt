# Minimum CMake version required
cmake_minimum_required(VERSION 3.10)

# Project Name
project(himbavision)

# Set path to OpenCV Directory
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.10.0-android/sdk/native/jni)

# Search for the core and imgproc module in the OpenCV library
find_package(OpenCV REQUIRED core imgproc)

# Set path to the NCNN library
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20240820-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)

# Search for the NCNN library
find_package(ncnn REQUIRED)

# Include directories
include_directories(
        ${CMAKE_SOURCE_DIR}/header  # Include directory to headers
        ${CMAKE_SOURCE_DIR}/header/ndkcamera # Include directory to ndkcamera
        ${CMAKE_SOURCE_DIR}/header/libjpeg-turbo  # Path to turbojpeg.h
)

# Add the libturbojpeg library
add_library(turbojpeg SHARED IMPORTED)
set_target_properties(turbojpeg PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libturbojpeg.so)

# Add a shared library called 'himbavision' from the specified source files
add_library(himbavision SHARED
        source/nanodetncnn.cpp
        source/nanodet.cpp
        source/ndkcamera.cpp
        source/framesdirect.cpp
)

# Link the 'himbavision' library with the required libraries:
# - ncnn
# - OpenCV libraries
# - turbojpeg
# - camera2ndk
# - mediandk
# - log
target_link_libraries(himbavision
        ncnn
        ${OpenCV_LIBS}
        turbojpeg
        camera2ndk
        mediandk
        log
)