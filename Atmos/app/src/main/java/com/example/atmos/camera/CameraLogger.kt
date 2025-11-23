package com.example.atmos.camera

import java.text.SimpleDateFormat
import java.util.*

object CameraLogger {
    private const val TAG = "CameraCapture"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun logInfo(message: String) {
        val timestamp = timeFormat.format(Date())
        println("[$timestamp] [INFO] $TAG: $message")
    }
    
    fun logWarning(message: String) {
        val timestamp = timeFormat.format(Date())
        println("[$timestamp] [WARN] $TAG: $message")
    }
    
    fun logError(error: Throwable, context: String) {
        val timestamp = timeFormat.format(Date())
        println("[$timestamp] [ERROR] $TAG: $context: ${error.message}")
        error.printStackTrace()
    }
}
