package ie.tus.himbavision.dataclasses


data class DetectionResult(
    val object_results: List<String>? = null,
    val all_results: List<String>? = null
)