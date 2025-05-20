//
// Created by Michael Alfred on 22/12/2024.
//

#include <jni.h>
#include "../header/ndkcamera.h"
#include <opencv2/core.hpp> // Include core for cv::Mat
#include <android/log.h>    // Include Android log
#include <opencv2/imgproc.hpp>
#include <turbojpeg.h>      // Include libjpeg-turbo

// Declare an instance of NdkCamera
NdkCamera ndkCamera;

extern "C" JNIEXPORT jint JNICALL
Java_ie_tus_himbavision_jnibridge_FramesDirect_openCamera(JNIEnv* env, jclass /* this */, jint cameraFacing) {
    return ndkCamera.open(cameraFacing);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ie_tus_himbavision_jnibridge_FramesDirect_getLatestFrame(JNIEnv* env, jclass /* this */) {
    // Get the latest frame
    cv::Mat frame = ndkCamera.get_latest_frame();

    // Check if the frame is empty
    if (frame.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Frame is empty");
        return nullptr;
    }

    // Convert the frame to the format expected by libjpeg-turbo
    cv::Mat rgbFrame;
    int pixelFormat;
    if (frame.channels() == 1) {
        // Grayscale image
        rgbFrame = frame;
        pixelFormat = TJPF_GRAY;
    } else if (frame.channels() == 3) {
        // Convert BGR to RGB
        cv::cvtColor(frame, rgbFrame, cv::COLOR_BGR2RGB);
        pixelFormat = TJPF_RGB;
    } else if (frame.channels() == 4) {
        // Convert BGRA to RGB
        cv::cvtColor(frame, rgbFrame, cv::COLOR_BGRA2RGB);
        pixelFormat = TJPF_RGB;
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Unsupported frame format with %d channels", frame.channels());
        return nullptr;
    }

    // Compress the image using libjpeg-turbo
    tjhandle tjInstance = tjInitCompress();
    if (tjInstance == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to initialize turbojpeg compressor");
        return nullptr;
    }

    unsigned char* jpegBuf = nullptr;
    unsigned long jpegSize = 0;
    int width = rgbFrame.cols;
    int height = rgbFrame.rows;
    int pitch = 0;          // If pitch == 0, then bytesPerLine = width * bytesPerPixel
    int subsamp = TJSAMP_444; // No chroma subsampling
    int quality = 90;       // JPEG quality
    int flags = TJFLAG_FASTDCT; // Compression flags

    int ret = tjCompress2(
            tjInstance,
            rgbFrame.data,
            width,
            pitch,
            height,
            pixelFormat,
            &jpegBuf,
            &jpegSize,
            subsamp,
            quality,
            flags
    );

    if (ret != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "tjCompress2() failed: %s", tjGetErrorStr());
        tjDestroy(tjInstance);
        return nullptr;
    }

    // Create jbyteArray to return jpeg data to Java
    jbyteArray byteArray = env->NewByteArray(jpegSize);
    if (byteArray == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to allocate jbyteArray");
        tjFree(jpegBuf);
        tjDestroy(tjInstance);
        return nullptr;
    }

    env->SetByteArrayRegion(byteArray, 0, jpegSize, reinterpret_cast<jbyte*>(jpegBuf));

    // Free the JPEG buffer and destroy the compressor instance
    tjFree(jpegBuf);
    tjDestroy(tjInstance);

    // Log the size of the compressed JPEG
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Compressed JPEG size: %lu bytes", jpegSize);

    return byteArray;
}