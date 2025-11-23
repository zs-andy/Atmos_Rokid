package com.example.atmos.detection

data class Detection(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val className: String,
    val confidence: Float
)

data class DetectionResponse(
    val detections: List<Detection>,
    val processingTime: Float,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)
