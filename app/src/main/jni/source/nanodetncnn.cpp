// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
// https://github.com/nihui/ncnn-android-nanodet
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "../header/nanodet.h"

#include "../header/ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include "../header/ndkcamera.h"
#include <turbojpeg.h>
#include <chrono>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

//Draws a white rectangle as a background for the the unsupported text if not model is available to run detections
static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "Loading..";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    //Calculate the center of the image for text placement
    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

// Global variable to store FPS
float global_fps = 0.f;

//Calculate the frames per second of the camera feed and displays the fps at the top right corner of the image
static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    // Update the global FPS variable
    global_fps = avg_fps;

    /*
    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));
                */

    return 0;
}


static NanoDet* g_nanodet = 0;
static ncnn::Mutex lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

//Manage camera frame rendering
void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // nanodet
    {
        ncnn::MutexLockGuard g(lock);
        //Check is a nanodet object is available
        if (g_nanodet)
        {
            std::vector<Object> objects;
            g_nanodet->detect(rgb, objects);

            g_nanodet->draw(rgb, objects);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }


    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;

extern "C" {
    //initialize resources when the library loads
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    return JNI_VERSION_1_4;
}

//Cleanup resources when the library unloads
JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);


        delete g_nanodet;
        g_nanodet = 0;
    }

    delete g_camera;
    g_camera = 0;
}

//Load the Elite 416 Nanodet model
JNIEXPORT jboolean JNICALL Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint cpugpu)
{
    // Validate cpugpu parameter
    if (cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    // Get the asset manager
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    // Define the model type, target size, mean values, and normalization values for "ELite1_416"
    const char* modeltype = "ELite1_416";
    const int target_size = 416;
    const float mean_vals[3] = {127.f, 127.f, 127.f};
    const float norm_vals[3] = {1.f / 128.f, 1.f / 128.f, 1.f / 128.f};
    bool use_gpu = (int)cpugpu == 1;

    // Reload the model
    {
        ncnn::MutexLockGuard g(lock);

        // Check if GPU is requested but not available
        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // No GPU available
            delete g_nanodet;
            g_nanodet = 0;
        }
        else
        {
            // Load the model
            if (!g_nanodet)
                g_nanodet = new NanoDet;
            g_nanodet->load(mgr, modeltype, target_size, mean_vals, norm_vals, use_gpu);
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getFps(JNIEnv* env, jobject thiz) {
    return global_fps;
}

//Manages camera opening and closing
// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    // Check the camera facing parameter
    if (facing < 0 || facing > 1) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Invalid camera facing: %d", facing);
        return JNI_FALSE; // Return false if the facing parameter is invalid
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    // Check if g_camera is initialized properly
    if (g_camera == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "g_camera is NULL");
        return JNI_FALSE; // Ensure g_camera is initialized
    }

    // Try to open the camera
    int result = g_camera->open((int)facing);

    // Log the result of the camera open operation
    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to open camera with facing %d, error code: %d", facing, result);
        return JNI_FALSE; // Handle error from camera open operation
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Camera opened successfully");

    return JNI_TRUE; // Return true if the camera opened successfully
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    if (win == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "setOutputWindow: ANativeWindow_fromSurface returned null");
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}



// Function to filter repetitive directions
std::vector<std::string> filterDirections(const std::vector<std::string>& directions, int timeframeSeconds) {
    std::vector<std::string> filteredDirections;
    std::chrono::steady_clock::time_point lastLoggedTime = std::chrono::steady_clock::now() - std::chrono::seconds(timeframeSeconds);

    for (const auto& direction : directions) {
        if (filteredDirections.empty() || direction != filteredDirections.back()) {
            filteredDirections.push_back(direction);
            lastLoggedTime = std::chrono::steady_clock::now();
        } else {
            auto now = std::chrono::steady_clock::now();
            if (std::chrono::duration_cast<std::chrono::seconds>(now - lastLoggedTime).count() >= timeframeSeconds) {
                filteredDirections.push_back(direction);
                lastLoggedTime = now;
            }
        }
    }

    return filteredDirections;
}

std::vector<std::string> g_allDetections;
std::vector<std::string> g_centerDetections;
std::vector<std::string> g_MinNavDirections;
std::vector<std::string> g_MaxNavDirections;

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getAllDetections(JNIEnv* env, jobject thiz)
{
    jobjectArray result = env->NewObjectArray(g_allDetections.size(), env->FindClass("java/lang/String"), env->NewStringUTF(""));

    for (size_t i = 0; i < g_allDetections.size(); ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(g_allDetections[i].c_str()));
    }

    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getCenterDetections(JNIEnv* env, jobject thiz)
{
    jobjectArray result = env->NewObjectArray(g_centerDetections.size(), env->FindClass("java/lang/String"), env->NewStringUTF(""));

    for (size_t i = 0; i < g_centerDetections.size(); ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(g_centerDetections[i].c_str()));
    }

    return result;
}

}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getMinNavDirections(JNIEnv* env, jobject thiz)
{
    // Filter repetitive directions
    std::vector<std::string> filteredDirections = filterDirections(g_MinNavDirections, 6); // 6 seconds timeframe

    // Explicitly cast the size to jsize
    jsize arraySize = static_cast<jsize>(filteredDirections.size());

    jobjectArray result = env->NewObjectArray(arraySize, env->FindClass("java/lang/String"), env->NewStringUTF(""));

    for (jsize i = 0; i < arraySize; ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(filteredDirections[i].c_str()));
    }

    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getMaxNavDirections(JNIEnv* env, jobject thiz)
{
    // Filter repetitive directions
    std::vector<std::string> filteredDirections = filterDirections(g_MaxNavDirections, 10); // 10 seconds timeframe

    // Explicitly cast the size to jsize
    jsize arraySize = static_cast<jsize>(filteredDirections.size());

    jobjectArray result = env->NewObjectArray(arraySize, env->FindClass("java/lang/String"), env->NewStringUTF(""));

    for (jsize i = 0; i < arraySize; ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(filteredDirections[i].c_str()));
    }

    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getAllFindDetections(JNIEnv* env, jobject thiz, jstring objectName)
{
    const char* object_name_cstr = env->GetStringUTFChars(objectName, nullptr);
    std::string object_name(object_name_cstr);
    env->ReleaseStringUTFChars(objectName, object_name_cstr);

    // Filter detections based on the object name
    std::vector<std::string> filtered_detections;
    for (const auto& detection : g_allDetections) {
        if (detection.find(object_name) != std::string::npos) {
            filtered_detections.push_back(detection);
        }
    }

    // Create a new Java string array to return the filtered detections
    jobjectArray result = env->NewObjectArray(filtered_detections.size(), env->FindClass("java/lang/String"), env->NewStringUTF(""));

    for (size_t i = 0; i < filtered_detections.size(); ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(filtered_detections[i].c_str()));
    }

    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getLatestFrame(JNIEnv* env, jobject thiz) {
    // Get the latest frame
    cv::Mat frame = g_camera->get_latest_frame();

    // Check if the frame is empty
    if (frame.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Frame is empty");
        return nullptr;
    }

    // Log the frame dimensions
    __android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Frame dimensions: %d x %d", frame.cols, frame.rows);

    // Convert the NV21 frame to RGB format
    cv::Mat rgbFrame;
    cv::cvtColor(frame, rgbFrame, cv::COLOR_YUV2RGB_NV21);

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
            TJPF_RGB,
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

extern "C"
JNIEXPORT jfloat JNICALL
Java_ie_tus_himbavision_jnibridge_HimbaJNIBridge_getFocalLengthPx(JNIEnv* env, jobject thiz) {
    return globalFocalLengthPx;
}
