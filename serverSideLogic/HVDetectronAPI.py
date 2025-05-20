from flask import Flask, request, jsonify
import detectron2
from detectron2.utils.logger import setup_logger

setup_logger()
from detectron2.engine import DefaultPredictor
from detectron2.config import get_cfg
from detectron2 import model_zoo
from detectron2.data import MetadataCatalog
import torch
import numpy as np
import cv2

app = Flask(__name__)

# Initialize the model
cfg = get_cfg()
cfg.merge_from_file(model_zoo.get_config_file("COCO-PanopticSegmentation/panoptic_fpn_R_50_1x.yaml"))
cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = 0.5
cfg.MODEL.WEIGHTS = model_zoo.get_checkpoint_url("COCO-PanopticSegmentation/panoptic_fpn_R_50_1x.yaml")
cfg.MODEL.DEVICE = "cuda" if torch.cuda.is_available() else "cpu"  # Use GPU if available

predictor = DefaultPredictor(cfg)

# Known object heights in meters
OBJECT_HEIGHTS = {
    'person': 1.7, 'bicycle': 1.0, 'car': 1.5, 'motorcycle': 1.1, 'airplane': 19.4,
    'bus': 3.5, 'train': 4.5, 'truck': 4.0, 'boat': 2.5, 'traffic light': 4.5,
    'fire hydrant': 0.8, 'stop sign': 2.3, 'parking meter': 1.2, 'bench': 0.7,
    'bird': 0.3, 'cat': 0.25, 'dog': 0.5, 'horse': 1.6, 'sheep': 0.9, 'cow': 1.5,
    'elephant': 3.2, 'bear': 2.8, 'zebra': 1.65, 'giraffe': 5.5, 'backpack': 0.5,
    'umbrella': 0.9, 'handbag': 0.3, 'tie': 1.4, 'suitcase': 0.55, 'frisbee': 0.025,
    'skis': 1.7, 'snowboard': 1.6, 'sports ball': 0.24, 'kite': 1.0, 'baseball bat': 0.85,
    'baseball glove': 0.3, 'skateboard': 0.8, 'surfboard': 2.2, 'tennis racket': 0.68,
    'bottle': 0.3, 'wine glass': 0.2, 'cup': 0.1, 'fork': 0.2, 'knife': 0.23, 'spoon': 0.2,
    'bowl': 0.07, 'banana': 0.19, 'apple': 0.1, 'sandwich': 0.05, 'orange': 0.1, 'broccoli': 0.18,
    'carrot': 0.2, 'hot dog': 0.15, 'pizza': 0.4, 'donut': 0.1, 'cake': 0.18, 'chair': 0.9,
    'couch': 1.0, 'potted plant': 0.65, 'bed': 0.6, 'dining table': 0.78, 'toilet': 0.45,
    'tv': 0.75, 'laptop': 0.025, 'mouse': 0.04, 'remote': 0.2, 'keyboard': 0.017,
    'cell phone': 0.018, 'microwave': 0.3, 'oven': 0.9, 'toaster': 0.25, 'sink': 0.18,
    'refrigerator': 1.7, 'book': 0.03, 'clock': 0.4, 'vase': 0.4, 'scissors': 0.2,
    'teddy bear': 0.5, 'hair drier': 0.2, 'toothbrush': 0.18, 'banner': 1.0, 'blanket': 0.02,
    'bridge': 10.0, 'cardboard': 0.02, 'counter': 0.9, 'curtain': 2.0, 'door-stuff': 2.1,
    'flower': 0.3, 'house': 5.0, 'mirror-stuff': 1.5, 'pillow': 0.2, 'platform': 1.0,
    'shelf': 1.8, 'stairs': 0.2, 'tent': 2.5, 'tree': 10.0, 'fence': 1.2,
    'cabinet': 1.5, 'table': 0.75, 'mountain': 1000.0,
    'building': 10.0, 'wall': 3.0
}

# Classes to exclude from results
EXCLUDED_CLASSES = ['sky', 'ceiling']

# Surfaces that require caution
SURFACE_CLASSES = [
    "bridge", "blanket", "floor-wood", "gravel", "road", "snow", "sand",
    "stairs", "pavement", "floor", "rug", "grass", "dirt"
]

# Grid labels
GRID_LABELS = [
    "Far-Left", "Far-Center", "Far-Right",
    "Mid-Left", "Mid-Center", "Mid-Right",
    "Near-Left", "Near-Center", "Near-Right"
]


def himba_find(image, outputs, focal_length_px, object_name=None):
    """
    Categorize objects based on a 3x3 grid and calculate distance and steps.
    Returns aggregated results as a dictionary.
    """
    height, width = image.shape[:2]
    grid_height = height // 3
    grid_width = width // 3
    panoptic_seg, segments_info = outputs["panoptic_seg"]
    panoptic_seg_np = panoptic_seg.cpu().numpy()
    metadata = MetadataCatalog.get(cfg.DATASETS.TRAIN[0])
    thing_classes = metadata.thing_classes
    stuff_classes = metadata.stuff_classes

    # Dictionary to hold aggregated results
    aggregated_results = {}

    for seg_info in segments_info:
        category_id = seg_info['category_id']
        is_thing = seg_info['isthing']
        class_name = thing_classes[category_id] if is_thing else stuff_classes[category_id]

        # Exclude specified classes
        if class_name in EXCLUDED_CLASSES:
            continue

        # If object_name is specified, filter by it
        if object_name and class_name != object_name:
            continue

        mask = (panoptic_seg_np == seg_info["id"])
        contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if contours:
            contour = max(contours, key=cv2.contourArea)
            x, y, w, h = cv2.boundingRect(contour)
            x_center = (x + x + w) / 2
            y_base = y + h
            row = min(int(y_base // grid_height), 2)
            col = min(int(x_center // grid_width), 2)
            grid_position = GRID_LABELS[row * 3 + col]

            # Check if the object's height is known
            if class_name in OBJECT_HEIGHTS:
                actual_height = OBJECT_HEIGHTS[class_name]  # In meters
                image_height = h  # In pixels
                distance = (actual_height * focal_length_px) / image_height  # In meters
                distance = round(distance, 2)  # Round to two decimal places
                # Convert distance to steps
                step_length = 0.75  # Average step length in meters
                steps = round(distance / step_length)

                detection_info = {
                    "position": grid_position,
                    "distance_m": distance,
                    "steps": steps
                }
            else:
                # If height is not known, only include position
                detection_info = {
                    "position": grid_position
                }

            # Initialize the class in the dictionary if not present
            if class_name not in aggregated_results:
                aggregated_results[class_name] = {
                    "count": 0,
                    "detections": []
                }
            aggregated_results[class_name]["count"] += 1
            aggregated_results[class_name]["detections"].append(detection_info)

    return aggregated_results


def himba_nav(aggregated_results):
    """
    Generate navigation instructions based on aggregated object detections.
    Returns a dictionary with minimal and maximal navigation instructions.
    """
    navigation_commands = []
    caution_messages = []

    # Initialize grid_positions with empty lists
    grid_positions = {
        "Far-Left": [],
        "Far-Center": [],
        "Far-Right": [],
        "Mid-Left": [],
        "Mid-Center": [],
        "Mid-Right": [],
        "Near-Left": [],
        "Near-Center": [],
        "Near-Right": []
    }

    # Populate grid positions with objects
    for class_name, data in aggregated_results.items():
        for det in data["detections"]:
            grid = det["position"]
            grid_positions[grid].append({
                "class_name": class_name,
                "distance_m": det.get("distance_m", None),
                "steps": det.get("steps", None)
            })

    # Check for surfaces in critical grid regions
    critical_surfaces_near = [
        obj for obj in grid_positions["Near-Center"] if obj["class_name"] in SURFACE_CLASSES
    ]
    critical_surfaces_mid = [
        obj for obj in grid_positions["Mid-Center"] if obj["class_name"] in SURFACE_CLASSES
    ]

    if critical_surfaces_near:
        surfaces = ", ".join([obj["class_name"] for obj in critical_surfaces_near])
        caution_messages.append(f"Caution: Currently walking on {surfaces} at Near-Center.")
    if critical_surfaces_mid:
        surfaces = ", ".join([obj["class_name"] for obj in critical_surfaces_mid])
        caution_messages.append(f"Caution: Currently close to {surfaces} at Mid-Center.")

    # Collect minimum distances per grid zone
    temp_distances = {}  # Dictionary to store minimum distances
    for grid_zone, objects in grid_positions.items():
        if objects:
            # Extract distances, ignoring None values and surface class objects
            distances = [obj["distance_m"] for obj in objects if obj["distance_m"] is not None and obj["class_name"] not in SURFACE_CLASSES]
            if distances:
                temp_distances[grid_zone] = min(distances)

    # Initialize variables to store minimum distances
    near_center_found = False
    mid_center_found = False
    near_center_distance = float('inf')
    mid_center_distance = float('inf')
    near_left_distance = float('inf')
    mid_left_distance = float('inf')
    near_right_distance = float('inf')
    mid_right_distance = float('inf')

    # Assign distances based on grid zones
    for grid_zone, distance in temp_distances.items():
        if grid_zone == "Near-Center":
            near_center_found = True
            near_center_distance = min(near_center_distance, distance)
        elif grid_zone == "Mid-Center":
            mid_center_found = True
            mid_center_distance = min(mid_center_distance, distance)
        elif grid_zone == "Near-Left":
            near_left_distance = min(near_left_distance, distance)
        elif grid_zone == "Mid-Left":
            mid_left_distance = min(mid_left_distance, distance)
        elif grid_zone == "Near-Right":
            near_right_distance = min(near_right_distance, distance)
        elif grid_zone == "Mid-Right":
            mid_right_distance = min(mid_right_distance, distance)


    print(f"Near-Center Distance: {near_center_distance}, Mid-Center Distance: {mid_center_distance}")
    print(f"Near-Left Distance: {near_left_distance}, Mid-Left Distance: {mid_left_distance}")
    print(f"Near-Right Distance: {near_right_distance}, Mid-Right Distance: {mid_right_distance}")

    # Navigation Decision Logic
    # if not near_center_found and not mid_center_found:
    if not near_center_found:
        navigation_commands.append("Continue ahead.")
    else:
        # Determine the primary center distance
        center_distance = near_center_distance if near_center_found else mid_center_distance

        # Conditions based on the distances
        can_move_left = near_left_distance > center_distance and mid_left_distance > center_distance
        can_move_right = near_right_distance > center_distance and mid_right_distance > center_distance

        if can_move_left or can_move_right:
            if can_move_left and can_move_right:
                # Choose the side with the greater distance
                if near_left_distance > near_right_distance:
                    navigation_commands.append("Move left.")
                else:
                    navigation_commands.append("Move right.")
            elif can_move_left:
                navigation_commands.append("Move left.")
            elif can_move_right:
                navigation_commands.append("Move right.")
        elif near_left_distance <= center_distance or mid_left_distance <= center_distance:
            if near_right_distance <= center_distance or mid_right_distance <= center_distance:
                navigation_commands.append("Slow down, no safe path found.")
            else:
                navigation_commands.append("Move right.")
        else:
            navigation_commands.append("Cannot find path, be careful.")

    # Maximal Navigation: Detailed detections
    maximal_navigation = []
    for class_name, data in aggregated_results.items():
        detections_info = []
        for det in data["detections"]:
            pos = det["position"]
            steps = det.get("steps", None)
            if steps is not None:
                detections_info.append(f"{pos} in {steps} steps")
            else:
                detections_info.append(f"{pos}")
        positions_steps = ", ".join(detections_info)
        if any("distance_m" in det for det in data["detections"]):
            distances = ", ".join([f"{det['distance_m']}m" for det in data["detections"] if "distance_m" in det])
            steps = ", ".join([f"{det['steps']} steps" for det in data["detections"] if "steps" in det])
            maximal_navigation.append(
                f"{data['count']} {class_name}(s) detected at {positions_steps} with distances: {distances} and steps: {steps}."
            )
        else:
            maximal_navigation.append(f"{data['count']} {class_name}(s) detected at {positions_steps}.")

    # Combine navigation commands and caution messages
    final_navigation = caution_messages + navigation_commands

    return {
        "minimal_navigation": final_navigation if final_navigation else ["No navigation commands generated."],
        "maximal_navigation": maximal_navigation if maximal_navigation else ["No objects detected."]
    }

def prepare_object_results(aggregated_results, object_name=None):
    """
    Formats the aggregated object detections into readable strings.
    """
    if not aggregated_results:
        return [f"No {object_name} detected in the image."] if object_name else ["No objects detected in the image."]

    formatted_results = []
    for class_name, data in aggregated_results.items():
        count = data["count"]
        detections = data["detections"]
        positions = ", ".join([f"{det['position']}" for det in detections])
        if class_name in OBJECT_HEIGHTS:
            distances = ", ".join([f"{det['distance_m']}m" for det in detections])
            steps = ", ".join([f"{det['steps']} steps" for det in detections])
            formatted_result = f"{count} {class_name}(s) detected at {positions} with distances: {distances} and steps: {steps}."
        else:
            formatted_result = f"{count} {class_name}(s) detected at {positions}."
        formatted_results.append(formatted_result)
    return formatted_results


@app.route('/detect', methods=['POST'])
def detect():
    """
    Endpoint to detect objects in an image and return their details.
    """
    try:
        file = request.files['image']
        focal_length_px = float(request.form['focal_length_px'])
        object_name = request.form.get('object_name', None)
        image = cv2.imdecode(np.frombuffer(file.read(), np.uint8), cv2.IMREAD_COLOR)

        # Save the received image (optional)
        # cv2.imwrite('png/phoneimage.png', image)

        print("Image received and decoded successfully.")
        print(f"Focal Length: {focal_length_px}, Object Name: {object_name}")

        # Perform inference
        outputs = predictor(image)
        print("Inference completed.")

        # Aggregate detected objects
        aggregated_results = himba_find(image, outputs, focal_length_px, object_name)

        # Prepare the response
        if object_name:
            object_results = prepare_object_results(aggregated_results, object_name)
            print(f"Object Results: {object_results}")
            return jsonify({
                "object_results": object_results
            })
        else:
            object_results = prepare_object_results(aggregated_results)
            print(f"All Results: {object_results}")
            return jsonify({
                "all_results": object_results
            })
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"error": str(e)})


@app.route('/navigate', methods=['POST'])
def navigate():
    """
    Endpoint to process image frames and return navigation instructions.
    """
    try:
        file = request.files['image']
        focal_length_px = float(request.form['focal_length_px'])
        image = cv2.imdecode(np.frombuffer(file.read(), np.uint8), cv2.IMREAD_COLOR)

        # Save the received image (optional)
        # cv2.imwrite('png/navigate_image.png', image)

        print("Image received and decoded successfully for navigation.")
        print(f"Focal Length: {focal_length_px}")

        # Perform inference
        outputs = predictor(image)
        print("Inference completed for navigation.")

        # Aggregate detected objects without filtering by object_name
        aggregated_results = himba_find(image, outputs, focal_length_px)

        # Generate navigation instructions
        navigation_instructions = himba_nav(aggregated_results)

        # Prepare object results for response
        object_results = prepare_object_results(aggregated_results)

        print(f"Navigation Instructions: {navigation_instructions}")
        print(f"Object Results: {object_results}")

        return jsonify({
            "navigation": navigation_instructions,
            "object_results": object_results
        })
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"error": str(e)})


if __name__ == '__main__':
    app.run(debug=True)