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

#include "../header/ndkcamera.h"

#include <string>

#include <android/log.h>

#include <opencv2/core/core.hpp>

#include "mat.h"

// Global static variables for storing the latest camera frame and synchronizing access to it
static cv::Mat newlatest_frame; // Latest captured frame (global storage)
static std::mutex newframe_mutex; // Mutex to protect access to the frame

// Callback function invoked when the camera device gets disconnected
static void onDisconnected(void* context, ACameraDevice* device)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onDisconnected %p", device);
}

// Callback function invoked when an error occurs with the camera device
static void onError(void* context, ACameraDevice* device, int error)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onError %p %d", device, error);
}

// Callback for handling the frame acquisition and processing with AImageReader
static void onImageAvailable(void* context, AImageReader* reader) {
    //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "onImageAvailable() called");

    // Acquire the latest image from the AImageReader
    AImage* image = 0;
    media_status_t status = AImageReader_acquireLatestImage(reader, &image);

    if (status != AMEDIA_OK) {
        //__android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to acquire latest image");
        return;
    }

    // Retrieve the image format and log it
    int32_t format;
    AImage_getFormat(image, &format);
   // __android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Image format: %d", format);

    // Retrieve the dimensions of the image and log them
    int32_t width = 0;
    int32_t height = 0;
    AImage_getWidth(image, &width);
    AImage_getHeight(image, &height);
    //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Image dimensions: %d x %d", width, height);

    // Retrieve the pixel stride for each color channel (Y, U, V) and log them
    int32_t y_pixelStride = 0, u_pixelStride = 0, v_pixelStride = 0;
    AImage_getPlanePixelStride(image, 0, &y_pixelStride);
    AImage_getPlanePixelStride(image, 1, &u_pixelStride);
    AImage_getPlanePixelStride(image, 2, &v_pixelStride);
    //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Pixel strides: Y=%d, U=%d, V=%d", y_pixelStride, u_pixelStride, v_pixelStride);

    // Retrieve the row stride for each color channel and log them
    int32_t y_rowStride = 0, u_rowStride = 0, v_rowStride = 0;
    AImage_getPlaneRowStride(image, 0, &y_rowStride);
    AImage_getPlaneRowStride(image, 1, &u_rowStride);
    AImage_getPlaneRowStride(image, 2, &v_rowStride);
    //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Row strides: Y=%d, U=%d, V=%d", y_rowStride, u_rowStride, v_rowStride);

    // Retrieve the raw data for each color channel and their lengths
    uint8_t* y_data = 0, * u_data = 0, * v_data = 0;
    int y_len = 0, u_len = 0, v_len = 0;
    AImage_getPlaneData(image, 0, &y_data, &y_len);
    AImage_getPlaneData(image, 1, &u_data, &u_len);
    AImage_getPlaneData(image, 2, &v_data, &v_len);
    //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Plane data lengths: Y=%d, U=%d, V=%d", y_len, u_len, v_len);

    // Check if the image is already in NV21 format
    if (u_data == v_data + 1 && v_data == y_data + width * height &&
        y_pixelStride == 1 && u_pixelStride == 2 && v_pixelStride == 2 &&
        y_rowStride == width && u_rowStride == width && v_rowStride == width) {

        // Directly process NV21 format
        //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "NV21 format detected");
        ((NdkCamera*)context)->on_image((unsigned char*)y_data, (int)width, (int)height);

        // Update the global latest frame
        {
            std::lock_guard<std::mutex> lock(newframe_mutex);
            newlatest_frame = cv::Mat(height + height / 2, width, CV_8UC1, y_data).clone();
            // __android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Updated latest frame with NV21 data");
        }
    } else {
        // Construct NV21 format manually
        unsigned char* nv21 = new unsigned char[width * height + width * height / 2];

        // Fill Y-plane data
        unsigned char* yptr = nv21;
        for (int y = 0; y < height; y++) {
            const unsigned char* y_data_ptr = y_data + y_rowStride * y;
            for (int x = 0; x < width; x++) {
                yptr[0] = y_data_ptr[0];
                yptr++;
                y_data_ptr += y_pixelStride;
            }
        }

        // Fill UV-plane data
        unsigned char* uvptr = nv21 + width * height;
        for (int y = 0; y < height / 2; y++) {
            const unsigned char* v_data_ptr = v_data + v_rowStride * y;
            const unsigned char* u_data_ptr = u_data + u_rowStride * y;
            for (int x = 0; x < width / 2; x++) {
                uvptr[0] = v_data_ptr[0];
                uvptr[1] = u_data_ptr[0];
                uvptr += 2;
                v_data_ptr += v_pixelStride;
                u_data_ptr += u_pixelStride;
            }
        }

        //__android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Calling OnImage");
        ((NdkCamera*)context)->on_image((unsigned char*)nv21, (int)width, (int)height);

        // Update the global latest frame
        {
            std::lock_guard<std::mutex> lock(newframe_mutex);
            newlatest_frame = cv::Mat(height + height / 2, width, CV_8UC1, nv21).clone();
            //__android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Updated latest frame with constructed NV21 data");
        }

        // Clean up dynamically allocated memory
        delete[] nv21;
    }

    // Release the image to free resources
    AImage_delete(image);
}

// Session lifecycle callbacks for the camera
static void onSessionActive(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionActive %p", session);
}

static void onSessionReady(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionReady %p", session);
}

static void onSessionClosed(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionClosed %p", session);
}

// Capture lifecycle callbacks
void onCaptureFailed(void* context, ACameraCaptureSession* session, ACaptureRequest* request, ACameraCaptureFailure* failure)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureFailed %p %p %p", session, request, failure);
}

void onCaptureSequenceCompleted(void* context, ACameraCaptureSession* session, int sequenceId, int64_t frameNumber)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureSequenceCompleted %p %d %ld", session, sequenceId, frameNumber);
}

void onCaptureSequenceAborted(void* context, ACameraCaptureSession* session, int sequenceId)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureSequenceAborted %p %d", session, sequenceId);
}

void onCaptureCompleted(void* context, ACameraCaptureSession* session, ACaptureRequest* request, const ACameraMetadata* result)
{
    // __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureCompleted %p %p %p", session, request, result);
}

NdkCamera::NdkCamera()
{
    camera_facing = 1;
    camera_orientation = 0;

    camera_manager = 0;
    camera_device = 0;
    image_reader = 0;
    image_reader_surface = 0;
    image_reader_target = 0;
    capture_request = 0;
    capture_session_output_container = 0;
    capture_session_output = 0;
    capture_session = 0;


    // setup imagereader and its surface
    {
        AImageReader_new(640, 480, AIMAGE_FORMAT_YUV_420_888, /*maxImages*/2, &image_reader);

        AImageReader_ImageListener listener;
        listener.context = this;
        listener.onImageAvailable = onImageAvailable;

        if(image_reader){
            AImageReader_setImageListener(image_reader, &listener);
        }

        AImageReader_getWindow(image_reader, &image_reader_surface);

        ANativeWindow_acquire(image_reader_surface);
    }
}

NdkCamera::~NdkCamera()
{
    close();

    if (image_reader)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Closing image reader");
        AImageReader_delete(image_reader);
        image_reader = 0;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Image reader closed");
    }else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Image reader was already null");
    }

    if (image_reader_surface)
    {
        ANativeWindow_release(image_reader_surface);
        image_reader_surface = 0;
    }
}

// Sets up the camera manager, finds the appropriate camera based on the facing direction, opens the camera, and sets up the capture request and session.
int NdkCamera::open(int _camera_facing)
{
    __android_log_print(ANDROID_LOG_WARN, "ncnn", "open() called");

    // Check if the camera is already open
    if (camera_device)
    {
        __android_log_print(ANDROID_LOG_WARN, "ncnn", "Camera is already open, skipping reopen.");
        return 0; // Camera is already open, return success
    }

    camera_facing = _camera_facing;

    // Step 1: Create camera manager
    camera_manager = ACameraManager_create();
    if (!camera_manager) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to create camera manager");
        return -1;
    }
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera manager created");

    // Step 2: Find back camera first, then front
    std::string camera_id;
    {
        ACameraIdList* camera_id_list = nullptr;
        if (ACameraManager_getCameraIdList(camera_manager, &camera_id_list) != ACAMERA_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to get camera ID list");
            return -1;
        }
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Number of cameras: %d", camera_id_list->numCameras);

        for (int i = 0; i < camera_id_list->numCameras; ++i)
        {
            const char* id = camera_id_list->cameraIds[i];
            ACameraMetadata* camera_metadata = nullptr;
            if (ACameraManager_getCameraCharacteristics(camera_manager, id, &camera_metadata) != ACAMERA_OK) {
                __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Failed to get camera metadata for ID: %s", id);
                continue;
            }

            // Query facing direction
            acamera_metadata_enum_android_lens_facing_t facing = ACAMERA_LENS_FACING_BACK;
            {
                ACameraMetadata_const_entry e = { 0 };
                if (ACameraMetadata_getConstEntry(camera_metadata, ACAMERA_LENS_FACING, &e) != ACAMERA_OK) {
                    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Failed to get lens facing for ID: %s", id);
                    ACameraMetadata_free(camera_metadata);
                    continue;
                }
                facing = (acamera_metadata_enum_android_lens_facing_t)e.data.u8[0];
            }

            // Prioritize back camera
            if (camera_facing == 1 && facing == ACAMERA_LENS_FACING_BACK) {
                camera_id = id;
            } else if (camera_facing == 0 && facing == ACAMERA_LENS_FACING_FRONT && camera_id.empty()) {
                camera_id = id; // Select front if back not found
            }

            // Query orientation
            int orientation = 0;
            {
                ACameraMetadata_const_entry e = { 0 };
                if (ACameraMetadata_getConstEntry(camera_metadata, ACAMERA_SENSOR_ORIENTATION, &e) == ACAMERA_OK) {
                    orientation = (int)e.data.i32[0];
                }
            }
            camera_orientation = orientation;

            __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera ID: %s, Facing: %d, Orientation: %d",
                                id, facing, camera_orientation);

            ACameraMetadata_free(camera_metadata);

            if (!camera_id.empty()) break; // Exit loop once desired camera is found
        }

        ACameraManager_deleteCameraIdList(camera_id_list);
    }

    if (camera_id.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to find a suitable camera");
        return -1;
    }
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Selected Camera ID: %s", camera_id.c_str());

    // Step 3: Open camera
    {
        ACameraDevice_StateCallbacks camera_device_state_callbacks = {
                .context = this,
                .onDisconnected = onDisconnected,
                .onError = onError
        };

        if (ACameraManager_openCamera(camera_manager, camera_id.c_str(), &camera_device_state_callbacks, &camera_device) != ACAMERA_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to open camera ID: %s", camera_id.c_str());
            return -1;
        }
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera opened successfully");
    }

    // Step 4: Capture request setup
    {
        if (ACameraDevice_createCaptureRequest(camera_device, TEMPLATE_PREVIEW, &capture_request) != ACAMERA_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to create capture request");
            return -1;
        }
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture request created");

        if (image_reader_surface == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "image_reader_surface is null");
            return -1;
        }

        ACameraOutputTarget_create(image_reader_surface, &image_reader_target);
        ACaptureRequest_addTarget(capture_request, image_reader_target);
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Image reader target added to capture request");
    }

    // Step 5: Capture session setup
    {
        ACameraCaptureSession_stateCallbacks camera_capture_session_state_callbacks = {
                .context = this,
                .onActive = onSessionActive,
                .onReady = onSessionReady,
                .onClosed = onSessionClosed
        };

        ACaptureSessionOutputContainer_create(&capture_session_output_container);
        if (image_reader_surface == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "image_reader_surface is null");
            return -1;
        }

        ACaptureSessionOutput_create(image_reader_surface, &capture_session_output);
        if (capture_session_output == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to create capture session output");
            return -1;
        }

        ACaptureSessionOutputContainer_add(capture_session_output_container, capture_session_output);
        if (capture_session_output_container == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to add capture session output to container");
            return -1;
        }

        if (ACameraDevice_createCaptureSession(camera_device, capture_session_output_container, &camera_capture_session_state_callbacks, &capture_session) != ACAMERA_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to create capture session");
            return -1;
        }
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session created");

        ACameraCaptureSession_captureCallbacks camera_capture_session_capture_callbacks = {
                .context = this,
                .onCaptureStarted = nullptr,
                .onCaptureProgressed = nullptr,
                .onCaptureCompleted = onCaptureCompleted,
                .onCaptureFailed = onCaptureFailed,
                .onCaptureSequenceCompleted = onCaptureSequenceCompleted,
                .onCaptureSequenceAborted = onCaptureSequenceAborted,
                .onCaptureBufferLost = nullptr
        };

        if (ACameraCaptureSession_setRepeatingRequest(capture_session, &camera_capture_session_capture_callbacks, 1, &capture_request, nullptr) != ACAMERA_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Failed to start repeating capture request");
            return -1;
        }
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Repeating capture request started");
    }

    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "open() completed successfully");
    return 0;
}

// Stops the capture session, closes the camera device, and releases all associated resources.
void NdkCamera::close()
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "close() called");

    // Stop and close the capture session
    if (capture_session)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Stopping and closing capture session");
        ACameraCaptureSession_stopRepeating(capture_session);
        ACameraCaptureSession_close(capture_session);
        capture_session = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session closed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session was already null");
    }

    // Close the camera device
    if (camera_device)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Closing camera device");
        ACameraDevice_close(camera_device);
        camera_device = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera device closed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera device was already null");
    }

    // Free the capture session output container
    if (capture_session_output_container)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Freeing capture session output container");
        ACaptureSessionOutputContainer_free(capture_session_output_container);
        capture_session_output_container = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session output container freed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session output container was already null");
    }

    // Free the capture session output
    if (capture_session_output)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Freeing capture session output");
        ACaptureSessionOutput_free(capture_session_output);
        capture_session_output = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session output freed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture session output was already null");
    }

    // Free the capture request
    if (capture_request)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Freeing capture request");
        ACaptureRequest_free(capture_request);
        capture_request = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture request freed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Capture request was already null");
    }

    // Free the image reader target
    if (image_reader_target)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Freeing image reader target");
        ACameraOutputTarget_free(image_reader_target);
        image_reader_target = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Image reader target freed");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Image reader target was already null");
    }


    // Delete the camera manager
    if (camera_manager)
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Deleting camera manager");
        ACameraManager_delete(camera_manager);
        camera_manager = nullptr;
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera manager deleted");
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera manager was already null");
    }

    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "All resources released");
}

void NdkCamera::on_image(const cv::Mat& rgb) const
{
}

void NdkCamera::on_image(const unsigned char* nv21, int nv21_width, int nv21_height) const {
    // Rotate nv21
    int w = 0;
    int h = 0;
    int rotate_type = 0;
    {
        if (camera_orientation == 0) {
            w = nv21_width;
            h = nv21_height;
            rotate_type = camera_facing == 0 ? 2 : 1;
        }
        if (camera_orientation == 90) {
            w = nv21_height;
            h = nv21_width;
            rotate_type = camera_facing == 0 ? 5 : 6;
        }
        if (camera_orientation == 180) {
            w = nv21_width;
            h = nv21_height;
            rotate_type = camera_facing == 0 ? 4 : 3;
        }
        if (camera_orientation == 270) {
            w = nv21_height;
            h = nv21_width;
            rotate_type = camera_facing == 0 ? 7 : 8;
        }
    }

    cv::Mat nv21_rotated(h + h / 2, w, CV_8UC1);
    ncnn::kanna_rotate_yuv420sp(nv21, nv21_width, nv21_height, nv21_rotated.data, w, h, rotate_type);

    // Convert nv21_rotated to rgb
    cv::Mat rgb(h, w, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21_rotated.data, w, h, rgb.data);

    // Store the latest frame
    {
        std::lock_guard<std::mutex> lock(frame_mutex);
        latest_frame = rgb.clone();
    }

    on_image(rgb);
}

// Get the latest frame and protect by mutex for thread safety
cv::Mat NdkCamera::get_latest_frame() const {
    if (camera_device) {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera is already open");
    } else {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "Camera is not open");
    }
    __android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "get_latest_frame() called");

    // Attempt to lock the mutex
    std::lock_guard<std::mutex> lock(newframe_mutex);

    // Check if the latest frame is empty
    if (newlatest_frame.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCamera", "Latest frame is empty");
        return cv::Mat();
    }

    __android_log_print(ANDROID_LOG_DEBUG, "NdkCamera", "Returning latest frame");
    return newlatest_frame.clone();
}


static const int NDKCAMERAWINDOW_ID = 233;

NdkCameraWindow::NdkCameraWindow() : NdkCamera()
{
    sensor_manager = 0;
    sensor_event_queue = 0;
    accelerometer_sensor = 0;
    win = 0;

    accelerometer_orientation = 0;

    // sensor
    sensor_manager = ASensorManager_getInstance();

    accelerometer_sensor = ASensorManager_getDefaultSensor(sensor_manager, ASENSOR_TYPE_ACCELEROMETER);
}

NdkCameraWindow::~NdkCameraWindow()
{
    if (accelerometer_sensor)
    {
        ASensorEventQueue_disableSensor(sensor_event_queue, accelerometer_sensor);
        accelerometer_sensor = 0;
    }

    if (sensor_event_queue)
    {
        ASensorManager_destroyEventQueue(sensor_manager, sensor_event_queue);
        sensor_event_queue = 0;
    }

    if (win)
    {
        ANativeWindow_release(win);
    }
}

void NdkCameraWindow::set_window(ANativeWindow* _win)
{
    if (win)
    {
        __android_log_print(ANDROID_LOG_DEBUG, "NdkCameraWindow", "Releasing previous window %p", win);
        ANativeWindow_release(win);
    }

    win = _win;

    if (win == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCameraWindow", "set_window: ANativeWindow is null after assignment");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "NdkCameraWindow", "Acquiring new window %p", win);
        ANativeWindow_acquire(win);
    }
}

void NdkCameraWindow::on_image_render(cv::Mat& rgb) const
{
}

void NdkCameraWindow::on_image(const unsigned char* nv21, int nv21_width, int nv21_height) const
{
    if (win == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, "NdkCameraWindow", "Native window (win) is null");
        return;
    }
    // resolve orientation from camera_orientation and accelerometer_sensor
    {
        if (!sensor_event_queue)
        {
            sensor_event_queue = ASensorManager_createEventQueue(sensor_manager, ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS), NDKCAMERAWINDOW_ID, 0, 0);

            ASensorEventQueue_enableSensor(sensor_event_queue, accelerometer_sensor);
        }

        int id = ALooper_pollAll(0, 0, 0, 0);
        if (id == NDKCAMERAWINDOW_ID)
        {
            ASensorEvent e[8];
            ssize_t num_event = 0;
            while (ASensorEventQueue_hasEvents(sensor_event_queue) == 1)
            {
                num_event = ASensorEventQueue_getEvents(sensor_event_queue, e, 8);
                if (num_event < 0)
                    break;
            }

            if (num_event > 0)
            {
                float acceleration_x = e[num_event - 1].acceleration.x;
                float acceleration_y = e[num_event - 1].acceleration.y;
                float acceleration_z = e[num_event - 1].acceleration.z;
//                 __android_log_print(ANDROID_LOG_WARN, "NdkCameraWindow", "x = %f, y = %f, z = %f", x, y, z);

                if (acceleration_y > 7)
                {
                    accelerometer_orientation = 0;
                }
                if (acceleration_x < -7)
                {
                    accelerometer_orientation = 90;
                }
                if (acceleration_y < -7)
                {
                    accelerometer_orientation = 180;
                }
                if (acceleration_x > 7)
                {
                    accelerometer_orientation = 270;
                }
            }
        }
    }

    // roi crop and rotate nv21
    int nv21_roi_x = 0;
    int nv21_roi_y = 0;
    int nv21_roi_w = 0;
    int nv21_roi_h = 0;
    int roi_x = 0;
    int roi_y = 0;
    int roi_w = 0;
    int roi_h = 0;
    int rotate_type = 0;
    int render_w = 0;
    int render_h = 0;
    int render_rotate_type = 0;
    {
        int win_w = ANativeWindow_getWidth(win);
        int win_h = ANativeWindow_getHeight(win);

        if (accelerometer_orientation == 90 || accelerometer_orientation == 270)
        {
            std::swap(win_w, win_h);
        }

        const int final_orientation = (camera_orientation + accelerometer_orientation) % 360;

        if (final_orientation == 0 || final_orientation == 180)
        {
            if (win_w * nv21_height > win_h * nv21_width)
            {
                roi_w = nv21_width;
                roi_h = (nv21_width * win_h / win_w) / 2 * 2;
                roi_x = 0;
                roi_y = ((nv21_height - roi_h) / 2) / 2 * 2;
            }
            else
            {
                roi_h = nv21_height;
                roi_w = (nv21_height * win_w / win_h) / 2 * 2;
                roi_x = ((nv21_width - roi_w) / 2) / 2 * 2;
                roi_y = 0;
            }

            nv21_roi_x = roi_x;
            nv21_roi_y = roi_y;
            nv21_roi_w = roi_w;
            nv21_roi_h = roi_h;
        }
        if (final_orientation == 90 || final_orientation == 270)
        {
            if (win_w * nv21_width > win_h * nv21_height)
            {
                roi_w = nv21_height;
                roi_h = (nv21_height * win_h / win_w) / 2 * 2;
                roi_x = 0;
                roi_y = ((nv21_width - roi_h) / 2) / 2 * 2;
            }
            else
            {
                roi_h = nv21_width;
                roi_w = (nv21_width * win_w / win_h) / 2 * 2;
                roi_x = ((nv21_height - roi_w) / 2) / 2 * 2;
                roi_y = 0;
            }

            nv21_roi_x = roi_y;
            nv21_roi_y = roi_x;
            nv21_roi_w = roi_h;
            nv21_roi_h = roi_w;
        }

        if (camera_facing == 0)
        {
            if (camera_orientation == 0 && accelerometer_orientation == 0)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 90)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 180)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 270)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 0)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 90)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 180)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 270)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 0)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 90)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 180)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 270)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 0)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 90)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 180)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 270)
            {
                rotate_type = 2;
            }
        }
        else
        {
            if (final_orientation == 0)
            {
                rotate_type = 1;
            }
            if (final_orientation == 90)
            {
                rotate_type = 6;
            }
            if (final_orientation == 180)
            {
                rotate_type = 3;
            }
            if (final_orientation == 270)
            {
                rotate_type = 8;
            }
        }

        if (accelerometer_orientation == 0)
        {
            render_w = roi_w;
            render_h = roi_h;
            render_rotate_type = 1;
        }
        if (accelerometer_orientation == 90)
        {
            render_w = roi_h;
            render_h = roi_w;
            render_rotate_type = 8;
        }
        if (accelerometer_orientation == 180)
        {
            render_w = roi_w;
            render_h = roi_h;
            render_rotate_type = 3;
        }
        if (accelerometer_orientation == 270)
        {
            render_w = roi_h;
            render_h = roi_w;
            render_rotate_type = 6;
        }
    }

    // crop and rotate nv21
    cv::Mat nv21_croprotated(roi_h + roi_h / 2, roi_w, CV_8UC1);
    {
        const unsigned char* srcY = nv21 + nv21_roi_y * nv21_width + nv21_roi_x;
        unsigned char* dstY = nv21_croprotated.data;
        ncnn::kanna_rotate_c1(srcY, nv21_roi_w, nv21_roi_h, nv21_width, dstY, roi_w, roi_h, roi_w, rotate_type);

        const unsigned char* srcUV = nv21 + nv21_width * nv21_height + nv21_roi_y * nv21_width / 2 + nv21_roi_x;
        unsigned char* dstUV = nv21_croprotated.data + roi_w * roi_h;
        ncnn::kanna_rotate_c2(srcUV, nv21_roi_w / 2, nv21_roi_h / 2, nv21_width, dstUV, roi_w / 2, roi_h / 2, roi_w, rotate_type);
    }

    // nv21_croprotated to rgb
    cv::Mat rgb(roi_h, roi_w, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21_croprotated.data, roi_w, roi_h, rgb.data);

    on_image_render(rgb);

    // rotate to native window orientation
    cv::Mat rgb_render(render_h, render_w, CV_8UC3);
    ncnn::kanna_rotate_c3(rgb.data, roi_w, roi_h, rgb_render.data, render_w, render_h, render_rotate_type);

    ANativeWindow_setBuffersGeometry(win, render_w, render_h, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);

    ANativeWindow_Buffer buf;
    ANativeWindow_lock(win, &buf, NULL);

    // scale to target size
    if (buf.format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM || buf.format == AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM)
    {
        for (int y = 0; y < render_h; y++)
        {
            const unsigned char* ptr = rgb_render.ptr<const unsigned char>(y);
            unsigned char* outptr = (unsigned char*)buf.bits + buf.stride * 4 * y;

            int x = 0;
#if __ARM_NEON
            for (; x + 7 < render_w; x += 8)
            {
                uint8x8x3_t _rgb = vld3_u8(ptr);
                uint8x8x4_t _rgba;
                _rgba.val[0] = _rgb.val[0];
                _rgba.val[1] = _rgb.val[1];
                _rgba.val[2] = _rgb.val[2];
                _rgba.val[3] = vdup_n_u8(255);
                vst4_u8(outptr, _rgba);

                ptr += 24;
                outptr += 32;
            }
#endif // __ARM_NEON
            for (; x < render_w; x++)
            {
                outptr[0] = ptr[0];
                outptr[1] = ptr[1];
                outptr[2] = ptr[2];
                outptr[3] = 255;

                ptr += 3;
                outptr += 4;
            }
        }
    }

    ANativeWindow_unlockAndPost(win);
}


float NdkCamera::getFocalLengthPX(ACameraManager* cameraManager, const char* cameraId) {
    float focalLengthMM = 0.0f;
    float sensorWidthMM = 0.0f;
    int32_t imageWidthPX = 0;

    ACameraMetadata* cameraMetadata = nullptr;
    ACameraManager_getCameraCharacteristics(cameraManager, cameraId, &cameraMetadata);

    // Get focal length in mm
    ACameraMetadata_const_entry focalLengthEntry;
    if (ACameraMetadata_getConstEntry(cameraMetadata, ACAMERA_LENS_INFO_AVAILABLE_FOCAL_LENGTHS, &focalLengthEntry) == ACAMERA_OK) {
        focalLengthMM = focalLengthEntry.data.f[0];
        __android_log_print(ANDROID_LOG_DEBUG, "CameraInfo", "Focal Length (mm): %f", focalLengthMM);
    }

    // Get sensor width in mm
    ACameraMetadata_const_entry physicalSizeEntry;
    if (ACameraMetadata_getConstEntry(cameraMetadata, ACAMERA_SENSOR_INFO_PHYSICAL_SIZE, &physicalSizeEntry) == ACAMERA_OK) {
        sensorWidthMM = physicalSizeEntry.data.f[0];
        __android_log_print(ANDROID_LOG_DEBUG, "CameraInfo", "Sensor Width (mm): %f", sensorWidthMM);
    }

    // Get image width in pixels
    ACameraMetadata_const_entry pixelArraySizeEntry;
    if (ACameraMetadata_getConstEntry(cameraMetadata, ACAMERA_SENSOR_INFO_PIXEL_ARRAY_SIZE, &pixelArraySizeEntry) == ACAMERA_OK) {
        imageWidthPX = pixelArraySizeEntry.data.i32[0];
        __android_log_print(ANDROID_LOG_DEBUG, "CameraInfo", "Image Width (px): %d", imageWidthPX);
    }

    ACameraMetadata_free(cameraMetadata);

    // Calculate focal length in pixels
    float focalLengthPX = (focalLengthMM / sensorWidthMM) * imageWidthPX;
    __android_log_print(ANDROID_LOG_DEBUG, "CameraInfo", "Focal Length (px): %f", focalLengthPX);

    return focalLengthPX;
}



/*
 * When a new image is available, the onImageAvailable function is called.
 * This function acquires the image data and calls on_image to process it.
 * The on_image function rotates and converts the image to RGB format, then stores it in latest_frame.
 * The get_latest_frame function can be called to retrieve the most recent frame.

By using std::lock_guard<std::mutex> lock(frame_mutex);, we ensure that access to latest_frame is thread-safe,
 preventing data corruption or inconsistent states.
 */