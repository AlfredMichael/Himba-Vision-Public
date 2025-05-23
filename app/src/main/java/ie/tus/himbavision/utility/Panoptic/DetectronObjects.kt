package ie.tus.himbavision.utility.Panoptic


//132 in total excluding sky and ceiling ("sky", "ceiling")

val detectronObjectNames = listOf(
    // First List (Class IDs 0 to 79)
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
    "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
    "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
    "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
    "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
    "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",

    // Second List (Class IDs 0 to 51)
    "things", "banner", "blanket", "bridge", "cardboard", "counter", "curtain", "door-stuff", "floor-wood", "flower",
    "fruit", "gravel", "house", "light", "mirror-stuff", "net", "pillow", "platform", "playingfield", "railroad",
    "river", "road", "roof", "sand", "sea", "shelf", "snow", "stairs", "tent", "towel",
    "wall-brick", "wall-stone", "wall-tile", "wall-wood", "water", "window-blind", "window", "tree", "fence",
    "cabinet", "table", "floor", "pavement", "mountain", "grass", "dirt", "paper", "food",
    "building", "rock", "wall", "rug"
)