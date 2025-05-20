// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
// https://github.com/nihui/ncnn-android-nanodet
// https://github.com/RangiLyu/nanodet
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

/*
  @misc
    {
        nanodet,
        title={NanoDet-Plus: Super fast and high accuracy lightweight anchor-free object detection model},
        author={RangiLyu},
        howpublished={\url{https://github.com/RangiLyu/nanodet}},
        year={2021}
    }

 */

#include "../header/nanodet.h" //Header files, contains class declarations
#include "../header/ndkcamera.h"
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp> //OpenCV libraries for image processing

#include "cpu.h" //handles CPU configurations for performance optimization
#include <map>
#include <string>

//Calculate the area of intersection between two bounding boxes a and b
static inline float intersection_area(const Object& a, const Object& b)
{
    cv::Rect_<float> inter = a.rect & b.rect;
    //returns the area of intersection between a and b
    return inter.area();
}

/*
 * Face object is a vector(array) of object instances (prob) (x,y) (width, height)
 * vector<object> faceobjects = {
 * Object(0.9, 100, 100, 50, 50),
 * Object(0.9, 100, 100, 50, 50),
 * Object(0.9, 100, 100, 50, 50),
 * }
 */
// quicksort the objects in descending order based on their probability scores
static void qsort_descent_inplace(std::vector<Object>& faceobjects, int left, int right)
{
    int i = left;
    int j = right;
    float p = faceobjects[(left + right) / 2].prob;

    while (i <= j)
    {
        while (faceobjects[i].prob > p)
            i++;

        while (faceobjects[j].prob < p)
            j--;

        if (i <= j)
        {
            // swap
            std::swap(faceobjects[i], faceobjects[j]);

            i++;
            j--;
        }
    }

    //     #pragma omp parallel sections
    {
        //         #pragma omp section
        {
            if (left < j) qsort_descent_inplace(faceobjects, left, j);
        }
        //         #pragma omp section
        {
            if (i < right) qsort_descent_inplace(faceobjects, i, right);
        }
    }
}

static void qsort_descent_inplace(std::vector<Object>& faceobjects)
{
    if (faceobjects.empty())
        return;

    qsort_descent_inplace(faceobjects, 0, faceobjects.size() - 1);
}

//Perform non-maximum suppression to filter our overlapping bounding boxes, keeping only the most probable ones
static void nms_sorted_bboxes(const std::vector<Object>& faceobjects, std::vector<int>& picked, float nms_threshold)
{
    picked.clear();

    const int n = faceobjects.size();

    std::vector<float> areas(n);
    for (int i = 0; i < n; i++)
    {
        areas[i] = faceobjects[i].rect.width * faceobjects[i].rect.height;
    }

    for (int i = 0; i < n; i++)
    {
        const Object& a = faceobjects[i];

        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++)
        {
            const Object& b = faceobjects[picked[j]];

            // intersection over union
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            // float IoU = inter_area / union_area
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }

        if (keep)
            picked.push_back(i);
    }
}

// Generate bounding box proposal with the class labels and prediction , filtering the the proposals by a probability threshold
static void generate_proposals(const ncnn::Mat& cls_pred, const ncnn::Mat& dis_pred, int stride, const ncnn::Mat& in_pad, float prob_threshold, std::vector<Object>& objects)
{
    const int num_grid = cls_pred.h;

    int num_grid_x;
    int num_grid_y;
    if (in_pad.w > in_pad.h)
    {
        num_grid_x = in_pad.w / stride;
        num_grid_y = num_grid / num_grid_x;
    }
    else
    {
        num_grid_y = in_pad.h / stride;
        num_grid_x = num_grid / num_grid_y;
    }

    const int num_class = cls_pred.w;
    const int reg_max_1 = dis_pred.w / 4;

    for (int i = 0; i < num_grid_y; i++)
    {
        for (int j = 0; j < num_grid_x; j++)
        {
            const int idx = i * num_grid_x + j;

            const float* scores = cls_pred.row(idx);

            // find label with max score
            int label = -1;
            float score = -FLT_MAX;
            for (int k = 0; k < num_class; k++)
            {
                if (scores[k] > score)
                {
                    label = k;
                    score = scores[k];
                }
            }

            if (score >= prob_threshold)
            {
                ncnn::Mat bbox_pred(reg_max_1, 4, (void*)dis_pred.row(idx));
                {
                    ncnn::Layer* softmax = ncnn::create_layer("Softmax");

                    ncnn::ParamDict pd;
                    pd.set(0, 1); // axis
                    pd.set(1, 1);
                    softmax->load_param(pd);

                    ncnn::Option opt;
                    opt.num_threads = 1;
                    opt.use_packing_layout = false;

                    softmax->create_pipeline(opt);

                    softmax->forward_inplace(bbox_pred, opt);

                    softmax->destroy_pipeline(opt);

                    delete softmax;
                }

                float pred_ltrb[4];
                for (int k = 0; k < 4; k++)
                {
                    float dis = 0.f;
                    const float* dis_after_sm = bbox_pred.row(k);
                    for (int l = 0; l < reg_max_1; l++)
                    {
                        dis += l * dis_after_sm[l];
                    }

                    pred_ltrb[k] = dis * stride;
                }

                float pb_cx = (j + 0.5f) * stride;
                float pb_cy = (i + 0.5f) * stride;

                float x0 = pb_cx - pred_ltrb[0];
                float y0 = pb_cy - pred_ltrb[1];
                float x1 = pb_cx + pred_ltrb[2];
                float y1 = pb_cy + pred_ltrb[3];

                Object obj;
                obj.rect.x = x0;
                obj.rect.y = y0;
                obj.rect.width = x1 - x0;
                obj.rect.height = y1 - y0;
                obj.label = label;
                obj.prob = score;

                objects.push_back(obj);
            }
        }
    }
}

NanoDet::NanoDet()
        : focalLengthPx(0.0f),
          focalLengthInitialized(false)
{
    blob_pool_allocator.set_size_compare_ratio(0.f);
    workspace_pool_allocator.set_size_compare_ratio(0.f);
}


int NanoDet::load(const char* modeltype, int _target_size, const float* _mean_vals, const float* _norm_vals, bool use_gpu)
{
    nanodet.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    nanodet.opt = ncnn::Option();

#if NCNN_VULKAN
    nanodet.opt.use_vulkan_compute = use_gpu;
#endif

    nanodet.opt.num_threads = ncnn::get_big_cpu_count();
    nanodet.opt.blob_allocator = &blob_pool_allocator;
    nanodet.opt.workspace_allocator = &workspace_pool_allocator;

    char parampath[256];
    char modelpath[256];
    sprintf(parampath, "nanodet-%s.param", modeltype);
    sprintf(modelpath, "nanodet-%s.bin", modeltype);

    nanodet.load_param(parampath);
    nanodet.load_model(modelpath);

    target_size = _target_size;
    mean_vals[0] = _mean_vals[0];
    mean_vals[1] = _mean_vals[1];
    mean_vals[2] = _mean_vals[2];
    norm_vals[0] = _norm_vals[0];
    norm_vals[1] = _norm_vals[1];
    norm_vals[2] = _norm_vals[2];

    return 0;
}

int NanoDet::load(AAssetManager* mgr, const char* modeltype, int _target_size, const float* _mean_vals, const float* _norm_vals, bool use_gpu)
{
    nanodet.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    nanodet.opt = ncnn::Option();

#if NCNN_VULKAN
    nanodet.opt.use_vulkan_compute = use_gpu;
#endif

    nanodet.opt.num_threads = ncnn::get_big_cpu_count();
    nanodet.opt.blob_allocator = &blob_pool_allocator;
    nanodet.opt.workspace_allocator = &workspace_pool_allocator;

    char parampath[256];
    char modelpath[256];
    sprintf(parampath, "nanodet-%s.param", modeltype);
    sprintf(modelpath, "nanodet-%s.bin", modeltype);

    nanodet.load_param(mgr, parampath);
    nanodet.load_model(mgr, modelpath);

    target_size = _target_size;
    mean_vals[0] = _mean_vals[0];
    mean_vals[1] = _mean_vals[1];
    mean_vals[2] = _mean_vals[2];
    norm_vals[0] = _norm_vals[0];
    norm_vals[1] = _norm_vals[1];
    norm_vals[2] = _norm_vals[2];

    return 0;
}

int NanoDet::detect(const cv::Mat& rgb, std::vector<Object>& objects, float prob_threshold, float nms_threshold)
{
    int width = rgb.cols;
    int height = rgb.rows;

    // pad to multiple of 32
    int w = width;
    int h = height;
    float scale = 1.f;
    if (w > h)
    {
        scale = (float)target_size / w;
        w = target_size;
        h = h * scale;
    }
    else
    {
        scale = (float)target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgb.data, ncnn::Mat::PIXEL_RGB2BGR, width, height, w, h);

    // pad to target_size rectangle
    int wpad = (w + 31) / 32 * 32 - w;
    int hpad = (h + 31) / 32 * 32 - h;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 0.f);

    in_pad.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = nanodet.create_extractor();

    ex.input("input.1", in_pad);

    std::vector<Object> proposals;

    // stride 8
    {
        ncnn::Mat cls_pred;
        ncnn::Mat dis_pred;
        ex.extract("cls_pred_stride_8", cls_pred);
        ex.extract("dis_pred_stride_8", dis_pred);

        std::vector<Object> objects8;
        generate_proposals(cls_pred, dis_pred, 8, in_pad, prob_threshold, objects8);

        proposals.insert(proposals.end(), objects8.begin(), objects8.end());
    }

    // stride 16
    {
        ncnn::Mat cls_pred;
        ncnn::Mat dis_pred;
        ex.extract("cls_pred_stride_16", cls_pred);
        ex.extract("dis_pred_stride_16", dis_pred);

        std::vector<Object> objects16;
        generate_proposals(cls_pred, dis_pred, 16, in_pad, prob_threshold, objects16);

        proposals.insert(proposals.end(), objects16.begin(), objects16.end());
    }

    // stride 32
    {
        ncnn::Mat cls_pred;
        ncnn::Mat dis_pred;
        ex.extract("cls_pred_stride_32", cls_pred);
        ex.extract("dis_pred_stride_32", dis_pred);

        std::vector<Object> objects32;
        generate_proposals(cls_pred, dis_pred, 32, in_pad, prob_threshold, objects32);

        proposals.insert(proposals.end(), objects32.begin(), objects32.end());
    }

    // sort all proposals by score from highest to lowest
    qsort_descent_inplace(proposals);

    // apply nms with nms_threshold
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    int count = picked.size();

    objects.resize(count);
    for (int i = 0; i < count; i++)
    {
        objects[i] = proposals[picked[i]];

        // adjust offset to original unpadded
        float x0 = (objects[i].rect.x - (wpad / 2)) / scale;
        float y0 = (objects[i].rect.y - (hpad / 2)) / scale;
        float x1 = (objects[i].rect.x + objects[i].rect.width - (wpad / 2)) / scale;
        float y1 = (objects[i].rect.y + objects[i].rect.height - (hpad / 2)) / scale;

        // clip
        x0 = std::max(std::min(x0, (float)(width - 1)), 0.f);
        y0 = std::max(std::min(y0, (float)(height - 1)), 0.f);
        x1 = std::max(std::min(x1, (float)(width - 1)), 0.f);
        y1 = std::max(std::min(y1, (float)(height - 1)), 0.f);

        objects[i].rect.x = x0;
        objects[i].rect.y = y0;
        objects[i].rect.width = x1 - x0;
        objects[i].rect.height = y1 - y0;
    }

    // sort objects by area
    struct
    {
        bool operator()(const Object& a, const Object& b) const
        {
            return a.rect.area() > b.rect.area();
        }
    } objects_area_greater;
    std::sort(objects.begin(), objects.end(), objects_area_greater);

    return 0;
}

/**
 * @brief Calculates the distance from the camera to an object using the pinhole camera model formula.
 *
 * This function computes the distance to an object based on the focal length of the camera (in pixels),
 * the actual height of the object (in real-world units), and the height of the object as measured in pixels
 * in the captured image. It validates the inputs to ensure they are positive and returns -1.0f for invalid inputs.
 *
 * @param focalLengthPx The focal length of the camera in pixels (must be > 0).
 * @param actualObjectHeight The actual height of the object in real-world units (must be > 0).
 * @param objectHeightInPixels The height of the object in the image in pixels (must be > 0).
 * @return The calculated distance to the object (same unit as actualObjectHeight), or -1.0f if inputs are invalid.
 */

float calculateDistance(float focalLengthPx, float actualObjectHeight, float objectHeightInPixels) {
    // Validate inputs
    if (focalLengthPx <= 0.0f) {
        // Invalid focal length
        return -1.0f;
    }

    if (actualObjectHeight <= 0.0f) {
        // Invalid actual object height
        return -1.0f;
    }

    if (objectHeightInPixels <= 0.0f) {
        // Invalid object height in pixels
        return -1.0f;
    }

    // Calculate distance using the pinhole camera model formula
    float distance = (actualObjectHeight * focalLengthPx) / objectHeightInPixels;

    return distance;
}

/**
 * @brief Analyzes navigation distances and determines optimal movement directions.
 *
 * This function evaluates a map of closest distances corresponding to various zones around the phone's camera
 * (e.g., "Near-Center", "Mid-Left") to determine the safest and most optimal navigation directions. It
 * clears any previous directions and updates the global minimal navigation directions vector (`g_MinNavDirections`)
 * based on the analysis.
 *
 * The function identifies potential paths by comparing distances in various zones to avoid obstacles
 * and maintain safe navigation. If no safe paths are found, it provides warnings or fallback directions.
 *
 * @param tempDistances A map containing zone names as keys and their respective distances as values.
 * @param g_MinNavDirections A reference to a vector that will be updated with the optimal navigation directions.
 */

void analyzeNavigation(const std::map<std::string, float>& tempDistances, std::vector<std::string>& g_MinNavDirections) {
    // Clear previous directions
    g_MinNavDirections.clear();


    bool nearCenterFound = false;
    bool midCenterFound = false;
    float nearCenterDistance = std::numeric_limits<float>::max();
    float midCenterDistance = std::numeric_limits<float>::max();
    float nearLeftDistance = std::numeric_limits<float>::max();
    float midLeftDistance = std::numeric_limits<float>::max();
    float nearRightDistance = std::numeric_limits<float>::max();
    float midRightDistance = std::numeric_limits<float>::max();


    for (const auto& entry : tempDistances) {
        const std::string& grid_zone = entry.first;
        float distance = entry.second;
       // __android_log_print(ANDROID_LOG_DEBUG, "TempDistances", "Grid Zone: %s, Distance: %f", grid_zone.c_str(), distance);

        if (grid_zone == "Near-Center") {
            nearCenterFound = true;
            nearCenterDistance = std::min(nearCenterDistance, distance);
        } else if (grid_zone == "Mid-Center") {
            midCenterFound = true;
            midCenterDistance = std::min(midCenterDistance, distance);
        } else if (grid_zone == "Near-Left") {
            nearLeftDistance = std::min(nearLeftDistance, distance);
        } else if (grid_zone == "Mid-Left") {
            midLeftDistance = std::min(midLeftDistance, distance);
        } else if (grid_zone == "Near-Right") {
            nearRightDistance = std::min(nearRightDistance, distance);
        } else if (grid_zone == "Mid-Right") {
            midRightDistance = std::min(midRightDistance, distance);
        }
    }

    // Log the distances being compared
    //__android_log_print(ANDROID_LOG_DEBUG, "Distances", "Near-Center Distance: %f, Mid-Center Distance: %f", nearCenterDistance, midCenterDistance);
    //__android_log_print(ANDROID_LOG_DEBUG, "Distances", "Near-Left Distance: %f, Mid-Left Distance: %f", nearLeftDistance, midLeftDistance);
    //__android_log_print(ANDROID_LOG_DEBUG, "Distances", "Near-Right Distance: %f, Mid-Right Distance: %f", nearRightDistance, midRightDistance);

    if (!nearCenterFound && !midCenterFound) {
        g_MinNavDirections.emplace_back("Continue ahead");
    } else {
        float centerDistance = nearCenterFound ? nearCenterDistance : midCenterDistance;
        if ((nearLeftDistance > centerDistance && midLeftDistance > centerDistance) || (nearRightDistance > centerDistance && midRightDistance > centerDistance)) {
            if (nearLeftDistance > centerDistance && midLeftDistance > centerDistance) {
                g_MinNavDirections.emplace_back("Move left");
            } else if (nearRightDistance > centerDistance && midRightDistance > centerDistance) {
                g_MinNavDirections.emplace_back("Move right");
            }
        } else if (nearLeftDistance <= centerDistance || midLeftDistance <= centerDistance) {
            if (nearRightDistance <= centerDistance || midRightDistance <= centerDistance) {
                g_MinNavDirections.emplace_back("Slow down, no safe path found");
            } else {
                g_MinNavDirections.emplace_back("Move right");
            }
        } else {
            g_MinNavDirections.emplace_back("Cannot find path, be careful");
        }
    }

    // Log all the minimal navigation directions
    for (const auto& direction : g_MinNavDirections) {
        //__android_log_print(ANDROID_LOG_DEBUG, "NavDirections", "Navigation Direction: %s", direction.c_str());
    }
}


// Store all the detection results for the current frame
extern std::vector<std::string> g_allDetections;
//Store all the center detection results for the current frame
extern std::vector<std::string> g_centerDetections;
//Store all the minimal detection results for the current frame
extern std::vector<std::string> g_MinNavDirections;
//Store all the maximal/most comprehensive detection results for the current frame
extern std::vector<std::string> g_MaxNavDirections;

float globalFocalLengthPx;

int NanoDet::draw(cv::Mat& rgb, const std::vector<Object>& objects)
{
    // Initialize the focal length if not already intialized
    {
        std::lock_guard<std::mutex> lock(focalLengthMutex);
        if (!focalLengthInitialized) {
            ACameraManager* cameraManager = ACameraManager_create();
            if (!cameraManager) {
                __android_log_print(ANDROID_LOG_ERROR, "NanoDet", "Failed to create ACameraManager");
                return -1;
            }

            ACameraIdList* cameraIdList = nullptr;
            if (ACameraManager_getCameraIdList(cameraManager, &cameraIdList) != ACAMERA_OK || cameraIdList->numCameras < 1) {
                __android_log_print(ANDROID_LOG_ERROR, "NanoDet", "No cameras found");
                if (cameraIdList) ACameraManager_deleteCameraIdList(cameraIdList);
                ACameraManager_delete(cameraManager);
                return -1;
            }

            // Select the first desired camera ID
            const char* cameraId = cameraIdList->cameraIds[0];

            // Get focal length in pixels
            focalLengthPx = NdkCamera::getFocalLengthPX(cameraManager, cameraId);
            globalFocalLengthPx = focalLengthPx;
            __android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "Focal Length (px): %f", focalLengthPx);

            // Cleanup
            ACameraManager_deleteCameraIdList(cameraIdList);
            ACameraManager_delete(cameraManager);

            focalLengthInitialized = true;
        }
    }

    // Clear detections for the current frame
    g_allDetections.clear();
    g_centerDetections.clear();
    g_MaxNavDirections.clear();

    static const char* class_names[] = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
    };

    static const float object_heights[] = {
            1.7, 1.0, 1.5, 1.1, 19.4, 3.25, 4.5, 4.0, 2.5, 4.5, 0.825, 2.25, 1.25, 0.675, 0.3, 0.25, 0.5, 1.55, 0.9, 1.5,
            3.25, 2.8, 1.65, 5.5, 0.5, 0.9, 0.3, 1.4, 0.55, 0.025, 1.75, 1.55, 0.24, 1.0, 0.85, 0.3, 0.8, 2.25, 0.68, 0.3,
            0.2, 0.1, 0.2, 0.23, 0.2, 0.07, 0.19, 0.1, 0.05, 0.1, 0.18, 0.2, 0.15, 0.4, 0.1, 0.18, 0.9, 1.05, 0.65, 0.6,
            0.775, 0.45, 0.75, 0.025, 0.04, 0.2, 0.018, 0.018, 0.3, 0.9, 0.25, 0.18, 1.75, 0.035, 0.4, 0.4, 0.2, 0.5, 0.2,
            0.175
    };


    static const unsigned char colors[19][3] = {
            {54, 67, 244}, {99, 30, 233}, {176, 39, 156}, {183, 58, 103}, {181, 81, 63},
            {243, 150, 33}, {244, 169, 3}, {212, 188, 0}, {136, 150, 0}, {80, 175, 76},
            {74, 195, 139}, {57, 220, 205}, {59, 235, 255}, {7, 193, 255}, {0, 152, 255},
            {34, 87, 255}, {72, 85, 121}, {158, 158, 158}, {139, 125, 96}
    };

    int color_index = 0;

    // Define grid zones
    int grid_rows = 3;
    int grid_cols = 3;
    int cell_width = rgb.cols / grid_cols;
    int cell_height = rgb.rows / grid_rows;

    // Lambda to get grid zone name based on object position
    auto get_grid_zone = [&](int x_pos, int y_pos) -> std::string {
        int row = y_pos / cell_height;
        int col = x_pos / cell_width;

        if (row < 0 || row >= grid_rows || col < 0 || col >= grid_cols)
            return "Out of bounds";

        const char* row_names[] = {"Far", "Mid", "Near"};
        const char* col_names[] = {"Left", "Center", "Right"};

        return std::string(row_names[row]) + "-" + col_names[col];
    };

    // **Aggregation Structures Start**
    // Map to hold class-wise detections with grid zones and step counts
    std::map<std::string, std::vector<std::pair<std::string, int>>> aggregatedDetections;
    // **Aggregation Structures End**

    // Temporary structure to store distances
    std::map<std::string, float> tempDistances;

    // Log the contents of tempDistances
    for (const auto& entry : tempDistances) {
        const std::string& grid_zone = entry.first;
        float distance = entry.second;
        //__android_log_print(ANDROID_LOG_DEBUG, "flowexecutiondraw", "1. Grid Zone: %s, Distance: %f", grid_zone.c_str(), distance);
    }


    // Process each detected object
    for (const auto& obj : objects) {
        const unsigned char* color = colors[color_index % 19];
        color_index++;

        cv::Scalar cc(color[0], color[1], color[2]);

        // Draw bounding box
        cv::rectangle(rgb, obj.rect, cc, 2);

        // Draw label
        char text[256];
        sprintf(text, "%s %.1f%%", class_names[obj.label], obj.prob * 100);

        int baseLine = 0;
        cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);
        int x = obj.rect.x;
        int y = obj.rect.y - label_size.height - baseLine;
        y = (y < 0) ? 0 : y;
        x = (x + label_size.width > rgb.cols) ? rgb.cols - label_size.width : x;

        cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)), cc, -1);
        cv::Scalar textcc = (color[0] + color[1] + color[2] >= 381) ? cv::Scalar(0, 0, 0) : cv::Scalar(255, 255, 255);
        cv::putText(rgb, text, cv::Point(x, y + label_size.height), cv::FONT_HERSHEY_SIMPLEX, 0.5, textcc, 1);

        // Determine primary bottom-center position
        int x_pos = obj.rect.x + obj.rect.width / 2;
        int y_pos = obj.rect.y + obj.rect.height;
        std::string grid_zone = get_grid_zone(x_pos, y_pos);

        // Center fallback if out of bounds
        if (grid_zone == "Out of bounds") {
            x_pos = obj.rect.x + obj.rect.width / 2;
            y_pos = obj.rect.y + obj.rect.height / 2;
            grid_zone = get_grid_zone(x_pos, y_pos);
        }

        // Top fallback if center is out of bounds
        if (grid_zone == "Out of bounds") {
            x_pos = obj.rect.x + obj.rect.width / 2;
            y_pos = obj.rect.y;
            grid_zone = get_grid_zone(x_pos, y_pos);
        }

        // Output object height in pixels
        float object_height_px = obj.rect.height;
        //__android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "Object height (px): %f", object_height_px);

        //__android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "Object Label (0-79): %d", obj.label);

        // Ensure we have a valid label index for object_heights
        if (obj.label >= 0 && obj.label < sizeof(object_heights) / sizeof(object_heights[0])) {
            float actual_height = object_heights[obj.label]; // Get the actual height of the object in meters
            // __android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "Actual Height (meters): %f", actual_height);

            // Calculate the distance from the camera for this object
            float distance = calculateDistance(focalLengthPx, actual_height, object_height_px);

            // Log the actual distance
            //__android_log_print(ANDROID_LOG_DEBUG, "flowexecutiondraw", "Actual Distance: %f", distance);

            // Initialize the grid zone distance with a max distance to get the closest object in a grid zone
            if (tempDistances.find(grid_zone) == tempDistances.end()) {
                tempDistances[grid_zone] = std::numeric_limits<float>::max();
            }

            // Store the distance in the temporary structure
            tempDistances[grid_zone] = std::min(tempDistances[grid_zone], distance);

            // Log the contents of tempDistances
            //__android_log_print(ANDROID_LOG_DEBUG, "flowexecutiondraw", "2. Grid Zone: %s, Distance: %f", grid_zone.c_str(), tempDistances[grid_zone]);

            // Convert the distance to steps (assuming average step length is 0.75 meters)
            float step_length = 0.75; // Average step length in meters
            int steps = distance / step_length;

            // Log the calculated distance and steps
            if (distance > 0.0f) {
                char distance_log[256];
                if (steps < 1) {
                    sprintf(distance_log, "Distance to %s: %.2f meters, Steps: %d. Stretch out your hand!", class_names[obj.label], distance, steps);
                } else {
                    sprintf(distance_log, "Distance to %s: %.2f meters, Steps: %d", class_names[obj.label], distance, steps);
                }
                //__android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "%s", distance_log);
            } else {
                //__android_log_print(ANDROID_LOG_ERROR, "NanoDet", "Invalid distance calculation for %s", class_names[obj.label]);
            }

            // **Aggregation Logic Start**
            // Aggregate detections by class
            std::string detected_class = std::string(class_names[obj.label]);
            aggregatedDetections[detected_class].emplace_back(grid_zone, steps);
            // **Aggregation Logic End**
        } else {
            //__android_log_print(ANDROID_LOG_ERROR, "NanoDet", "Invalid label index for %s", class_names[obj.label]);
        }

        //std::string detection_str = "Detected " + std::string(class_names[obj.label]) + " at " + grid_zone ;
        //std::string objectID = std::string(class_names[obj.label]);

    }

    // **After Processing All Objects: Aggregated Logging Start**
    for (const auto& entry : aggregatedDetections) {
        const std::string& detected_class = entry.first;
        const std::vector<std::pair<std::string, int>>& detections = entry.second;
        int count = detections.size();

        // Collect grid zones and step counts
        std::vector<std::string> grid_zones;
        std::vector<std::string> step_messages;
        std::vector<std::string> nav_step_messages;
        for (const auto& det : detections) {
            grid_zones.push_back(det.first);
            if (det.second < 1) {
                step_messages.push_back("Stretch out your hand!");
                nav_step_messages.emplace_back("Less than a step away");
            } else {
                step_messages.push_back("Take " + std::to_string(det.second) + " steps.");
                nav_step_messages.emplace_back("In " + std::to_string(det.second) + " steps.");

            }
        }

        // Concatenate grid zones
        std::string grid_zone_str;
        for (size_t i = 0; i < grid_zones.size(); ++i) {
            grid_zone_str += grid_zones[i];
            if (i != grid_zones.size() - 1) {
                grid_zone_str += ", ";
            }
        }

        // Concatenate step messages
        std::string step_str;
        for (size_t i = 0; i < step_messages.size(); ++i) {
            step_str += step_messages[i];
            if (i != step_messages.size() - 1) {
                step_str += ", ";
            }
        }

        // Concatenate nav step messages
        std::string nav_step_str;
        for (size_t i = 0; i < nav_step_messages.size(); ++i) {
            nav_step_str += nav_step_messages[i];
            if (i != nav_step_messages.size() - 1) {
                nav_step_str += ", ";
            }
        }

        // Create aggregated detection string
        std::string aggregated_str = std::to_string(count) + " " + detected_class + " detected at " + grid_zone_str + ". " + step_str;

        std::string aggregated_nav_str = std::to_string(count) + " " + detected_class + " detected at " + grid_zone_str + ". "+ nav_step_str ;

        // Log the aggregated detection
       // __android_log_print(ANDROID_LOG_DEBUG, "NanoDet", "%s", aggregated_str.c_str());

        // Log the aggregated nav detection
        //__android_log_print(ANDROID_LOG_DEBUG, "NanoDetNavigation", "%s", aggregated_nav_str.c_str());

        // Add to g_allDetections
        g_allDetections.push_back(aggregated_str);

        // Add to g_MaxNavDirections
        g_MaxNavDirections.push_back(aggregated_nav_str);

        // Add to g_centerDetections based on specific grid zones
        for (const auto& det : detections) {
            if (det.first == "Mid-Center" || det.first == "Far-Center" || det.first == "Near-Center") {
                // Find the corresponding step message
                std::string step_msg = (det.second < 1) ? "Stretch out your hand!" :
                                       "Take " + std::to_string(det.second) + " steps.";
                std::string center_detection_str = "Detected " + detected_class + " at " + det.first + ". " + step_msg;
                g_centerDetections.push_back(center_detection_str);

                // Log the center detection
                //__android_log_print(ANDROID_LOG_DEBUG, "NCenterDetection", "Center Detection: %s", center_detection_str.c_str());
            }
        }
    }
    // **After Processing All Objects: Aggregated Logging End**

    // Log the contents of tempDistances before calling analyzeNavigation
    for (const auto& entry : tempDistances) {
        const std::string& grid_zone = entry.first;
        float distance = entry.second;
        //__android_log_print(ANDROID_LOG_DEBUG, "flowexecutiondraw", "3. Grid Zone: %s, Distance: %f", grid_zone.c_str(), distance);
    }

    // Call the analyzeNavigation function with the temporary structure
    analyzeNavigation(tempDistances, g_MinNavDirections);

    return 0;
}

/*
 *
 * static const float object_heights[] = {
    1.7,       // person
    1.0,       // bicycle
    1.5,       // car
    1.1,       // motorcycle (average of 1-1.2 m)
    19.4,      // airplane
    3.25,      // bus (average of 3-3.5 m)
    4.5,       // train
    4.0,       // truck
    2.5,       // boat (average of 2-3 m)
    4.5,       // traffic light (average of 3-6 m)
    0.825,     // fire hydrant (average of 0.75-0.9 m)
    2.25,      // stop sign (average of 2-2.5 m)
    1.25,      // parking meter (average of 1-1.5 m)
    0.675,     // bench (average of 0.45-0.9 m)
    0.3,       // bird
    0.25,      // cat
    0.5,       // dog
    1.55,      // horse (average of 1.4-1.7 m)
    0.9,       // sheep (average of 0.6-1.2 m)
    1.5,       // cow
    3.25,      // elephant (average of 2.5-4 m)
    2.8,       // bear
    1.65,      // zebra
    5.5,       // giraffe
    0.5,       // backpack
    0.9,       // umbrella (average of 0.8-1 m)
    0.3,       // handbag
    1.4,       // tie
    0.55,      // suitcase
    0.025,     // frisbee
    1.75,      // skis (average of 1.5-2 m)
    1.55,      // snowboard (average of 1.4-1.7 m)
    0.24,      // sports ball
    1.0,       // kite
    0.85,      // baseball bat
    0.3,       // baseball glove
    0.8,       // skateboard
    2.25,      // surfboard (average of 1.8-2.7 m)
    0.68,      // tennis racket
    0.3,       // bottle
    0.2,       // wine glass
    0.1,       // cup
    0.2,       // fork
    0.23,      // knife
    0.2,       // spoon
    0.07,      // bowl
    0.19,      // banana (average of 0.18-0.2 m)
    0.1,       // apple
    0.05,      // sandwich
    0.1,       // orange
    0.18,      // broccoli (average of 0.15-0.2 m)
    0.2,       // carrot (average of 0.15-0.25 m)
    0.15,      // hot dog
    0.4,       // pizza (average of 0.3-0.5 m)
    0.1,       // donut
    0.18,      // cake (average of 0.15-0.2 m)
    0.9,       // chair (average of 0.8-1 m)
    1.05,      // couch (average of 0.9-1.2 m)
    0.65,      // potted plant (average of 0.3-1 m)
    0.6,       // bed (mattress height)
    0.775,     // dining table (average of 0.75-0.8 m)
    0.45,      // toilet (average of 0.4-0.5 m)
    0.75,      // tv (average of 0.5-1 m)
    0.025,     // laptop (closed thickness)
    0.04,      // mouse
    0.2,       // remote (average of 0.15-0.25 m)
    0.018,     // keyboard (thickness)
    0.018,     // cell phone (thickness)
    0.3,       // microwave
    0.9,       // oven
    0.25,      // toaster
    0.18,      // sink (average of 0.15-0.2 m)
    1.75,      // refrigerator (average of 1.5-2 m)
    0.035,     // book (average of 0.02-0.05 m)
    0.4,       // clock (average of 0.3-0.5 m)
    0.4,       // vase (average of 0.3-0.5 m)
    0.2,       // scissors (average of 0.15-0.25 m)
    0.5,       // teddy bear (average of 0.4-0.6 m)
    0.2,       // hair drier
    0.175      // toothbrush (average of 0.15-0.2 m)
};
 */