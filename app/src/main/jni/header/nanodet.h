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


// Define header guards
#ifndef NANODET_H
#define NANODET_H

// Include necessary OpenCV and NCNN headers
#include <opencv2/core/core.hpp> //Provides access to core functionalities from the open cv library
#include <net.h> // Provides access to the core functionality from the ncnn library
#include <string>
#include <map>
#include <vector>

// Define a struct to represent detected objects
struct Object{
    cv::Rect_<float> rect; //Templated class that stores the rectangle position (x,y) and size (width, height) in floating points
    int label; //Class label of the object
    float prob; //Probability of the detection
};

//Define the NanoDet class
class NanoDet {
public:
    NanoDet(); //Constructor
    //* mean pointer for example const char* modeltype means modeltype stores the memory address to const char
    //Load model from file
    //--> model type: type of path of the model to be loaded
    //--> target size: the desired size of input images (Pixels)
    //--> mean vals: array of mean values for normalization
    //--> norm vals: array of normalized values
    //--> use gpu: boolean flag indicating whether gpu should be used for inference
    int load(const char* modeltype, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);

    //Load model from Android asset manager
    int load(AAssetManager* mgr, const char* modeltype, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);

    //Detect objects in an image
    int detect(const cv::Mat& rgb, std::vector<Object>& objects, float prob_threshold = 0.4f, float nms_threshold = 0.5f);

    //Draw detected object on an image
    int draw(cv::Mat& rgb, const std::vector<Object>& objects);

    // Public flag to toggle tracking behavior
    bool trackLastKnownPosition = false;




private:
    ncnn::Net nanodet; //NCNN neural network object
    int target_size; //Target size for the input image
    float mean_vals[3]; //Mean RGB values for normalization
    float norm_vals[3]; // RGB Normalization values
    // Blob memory allocator for storing chunks of data like feature maps
    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    // Memory allocator for storing temporary data in blocks during computations
    ncnn::PoolAllocator workspace_pool_allocator;

    // Map to track last known positions of each detected object type
    std::map<std::string, std::string> lastKnownPositions;

    // Focal length in pixels
    float focalLengthPx;

    // Flag to check if focal length has been initialized
    bool focalLengthInitialized;

    // Mutex to ensure thread-safe initialization
    std::mutex focalLengthMutex;
};

// Declare the global focal length variable
extern float globalFocalLengthPx;


#endif //NANODET_H