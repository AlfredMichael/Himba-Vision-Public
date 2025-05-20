// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.


// Header guards to prevent multiple inclusions of this header file
// If not defined, define the NDKCAMERA_H, the build manager is not smart enough to ignore multiple includes so using header safety prevents that
#ifndef NDKCAMERA_H
#define NDKCAMERA_H

// Include necessary Android and OpenCV headers for camera and image processing
#include <android/looper.h> //Manage event loops necessary for asynchronous events
#include <android/native_window.h> //Create native window interface
#include <android/sensor.h> // Provides access to the Android sensor framework so we can read data from sensors like gyroscopes and accelerometers
#include <camera/NdkCameraDevice.h> //Manages camera devices through the NDK Camera API
#include <camera/NdkCameraManager.h> //Provides access to the camera manager
#include <camera/NdkCameraMetadata.h> //Definitions for camera metadata
#include <media/NdkImageReader.h> // Provides access to the ImageReader API
#include <opencv2/core/core.hpp> // Provides access to OpenCV library for image processing


// Define NdkCamera class
class NdkCamera {
public:
    // Constructor
    NdkCamera();
    // Destructor
    virtual ~NdkCamera();

    // Open camera, with default facing direction (0=front, 1=back)
    int open(int camera_facing = 0);
    // Close camera
    void close();
    // Virtual function to handle image in cv::Mat(OpenCV) format
    virtual void on_image(const cv::Mat& rgb) const;
    // Virtual function to handle image in NV21(YUV - Android) format
    virtual void on_image(const unsigned char* nv21, int nv21_width, int nv21_height) const;

    //M:

    cv::Mat get_latest_frame() const;

    // Function to get focal length in pixels
    static float getFocalLengthPX(ACameraManager* cameraManager, const char* cameraId);



public:
    int camera_facing; // Camera facing direction (0=front, 1=back)
    int camera_orientation; // Camera orientation

private:
    ACameraManager* camera_manager; // Camera manager manager
    ACameraDevice* camera_device; // Camera device manager
    AImageReader* image_reader; // Image Reader (Frame by Frame) manager
    ANativeWindow* image_reader_surface; // Surface for image reader
    ACameraOutputTarget* image_reader_target; // Output target for camera
    ACaptureRequest* capture_request; // Capture request handle
    ACaptureSessionOutputContainer* capture_session_output_container; // Capture session output container handle
    ACaptureSessionOutput* capture_session_output; // Capture session output handle
    ACameraCaptureSession* capture_session; // Capture session handle

    mutable std::mutex frame_mutex;
    mutable cv::Mat latest_frame;
};

// Define NdkCameraWindow class that inherits from NdkCamera
class NdkCameraWindow : public NdkCamera {
public:
    // Constructor
    NdkCameraWindow();
    // Destructor
    virtual ~NdkCameraWindow();

    // Set native window for rendering
    void set_window(ANativeWindow* win);
    // Virtual function to handle image rendering in cv::Mat format
    virtual void on_image_render(cv::Mat& rgb) const;
    // Virtual function to handle image in NV21 format
    virtual void on_image(const unsigned char* nv21, int nv21_width, int nv21_height) const;

public:
    mutable int accelerometer_orientation; // Orientation from accelerometer sensor

private:
    ASensorManager* sensor_manager; // Sensor manager handle
    mutable ASensorEventQueue* sensor_event_queue; // Sensor event queue handle
    const ASensor* accelerometer_sensor; // Accelerometer sensor handle
    ANativeWindow* win; // Native window handle for rendering
};

// End of header guards
#endif // NDKCAMERA_H

