package com.example.atmos.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaPlayer
import android.util.Base64
import com.example.atmos.camera.CameraLogger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * FastVLM 服务器上传器
 * 每 5 秒发送一次图像，接收识别结果和音频
 * JSON 格式: {"result": "芝士雪豹","audio": "base64mp3"}
 */
class FastVLMUploader(
    private val context: Context,
    private val serverUrl: String = "http://10.121.207.89:8080/detect"
) {
    companion object {
        private const val TARGET_MIN_SIZE = 640
        private const val JPEG_QUALITY = 85
    }
    
    private var compressedWidth = 0
    private var compressedHeight = 0
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
    
    var onVLMResult: ((String, String?) -> Unit)? = null
    var onConnectionStatusChange: ((ConnectionStatus) -> Unit)? = null
    
    @Volatile
    private var isUploading = false
    
    private var lastToastTime = 0L
    private val TOAST_INTERVAL = 5000L  // 5秒内只显示一次Toast
    
    private var mediaPlayer: MediaPlayer? = null
    
    enum class ConnectionStatus {
        IDLE, CONNECTING, CONNECTED, UPLOADING, ERROR
    }
    
    private var currentStatus = ConnectionStatus.IDLE
        set(value) {
            field = value
            onConnectionStatusChange?.invoke(value)
        }
    
    fun handleNewImage(image: Image, scope: CoroutineScope) {
        if (isUploading) {
            image.close()
            return
        }
        
        currentStatus = ConnectionStatus.UPLOADING
        scope.launch(Dispatchers.IO) {
            try {
                image.use {
                    uploadImage(it)
                }
            } catch (e: Exception) {
                CameraLogger.logError(e, "FastVLM上传")
                currentStatus = ConnectionStatus.ERROR
                isUploading = false
            }
        }
    }
    
    private suspend fun uploadImage(image: Image) {
        isUploading = true
        var tempFile: File? = null
        try {
            currentStatus = ConnectionStatus.CONNECTING
            
            val originalBytes = yuvToJpeg(image)
            val compressedBytes = compressImage(originalBytes)
            
            CameraLogger.logInfo("[FastVLM] 图片: ${compressedBytes.size / 1024}KB, 尺寸: ${compressedWidth}x${compressedHeight}")
            
            val timestamp = dateFormat.format(Date())
            val filename = "VLM_${timestamp}.jpg"
            
            tempFile = File.createTempFile("vlm_", ".jpg")
            tempFile.writeBytes(compressedBytes)
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    tempFile.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            
            val httpRequest = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()
            
            val startTime = System.currentTimeMillis()
            val response = client.newCall(httpRequest).execute()
            val elapsed = System.currentTimeMillis() - startTime
            
            if (response.isSuccessful) {
                currentStatus = ConnectionStatus.CONNECTED
                val responseBody = response.body?.string()
                CameraLogger.logInfo("[FastVLM] 成功 (${elapsed}ms): $responseBody")
                
                responseBody?.let { json ->
                    parseVLMResponse(json)?.let { (result, audioBase64) ->
                        withContext(Dispatchers.Main) {
                            onVLMResult?.invoke(result, audioBase64)
                            audioBase64?.let { playAudio(it) }
                        }
                    }
                }
            } else {
                currentStatus = ConnectionStatus.ERROR
                CameraLogger.logWarning("[FastVLM] 错误: ${response.code}")
                showToast("FastVLM服务器错误: ${response.code}")
            }
            response.close()
            
        } catch (e: Exception) {
            currentStatus = ConnectionStatus.ERROR
            CameraLogger.logError(e, "FastVLM上传")
            showToast("FastVLM连接失败: ${e.message}")
        } finally {
            tempFile?.delete()
            isUploading = false
        }
    }
    
    private fun parseVLMResponse(json: String): Pair<String, String?>? {
        return try {
            val jsonObject = JSONObject(json)
            val result = jsonObject.getString("result")
            val audio = if (jsonObject.has("audio")) jsonObject.getString("audio") else null
            Pair(result, audio)
        } catch (e: Exception) {
            CameraLogger.logError(e, "解析FastVLM响应")
            null
        }
    }
    
    private fun playAudio(base64Audio: String) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempAudioFile = File.createTempFile("audio_", ".mp3", context.cacheDir)
            tempAudioFile.writeBytes(audioBytes)
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempAudioFile.absolutePath)
                setOnCompletionListener {
                    tempAudioFile.delete()
                    release()
                }
                setOnErrorListener { _, _, _ ->
                    tempAudioFile.delete()
                    release()
                    true
                }
                prepare()
                setVolume(1.0f, 1.0f)  // 设置音量为最大 (左右声道都是1.0)
                start()
            }
            
            CameraLogger.logInfo("[FastVLM] 播放音频: ${audioBytes.size / 1024}KB (音量: 最大)")
        } catch (e: Exception) {
            CameraLogger.logError(e, "播放音频")
        }
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    private fun compressImage(originalBytes: ByteArray): ByteArray {
        var bitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            val minDimension = minOf(originalWidth, originalHeight)
            val scale = if (minDimension > TARGET_MIN_SIZE) {
                TARGET_MIN_SIZE.toFloat() / minDimension.toFloat()
            } else {
                1f
            }
            
            val targetWidth = (originalWidth * scale).toInt()
            val targetHeight = (originalHeight * scale).toInt()
            
            compressedWidth = targetWidth
            compressedHeight = targetHeight
            
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
            options.inPreferredConfig = Bitmap.Config.RGB_565
            
            bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
            
            val bitmapNotNull = bitmap ?: throw IllegalStateException("Failed to decode bitmap")
            
            scaledBitmap = if (bitmapNotNull.width != targetWidth || bitmapNotNull.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmapNotNull, targetWidth, targetHeight, false).also {
                    if (it != bitmapNotNull) {
                        bitmapNotNull.recycle()
                        bitmap = null
                    }
                }
            } else {
                bitmapNotNull
            }
            
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            
            return outputStream.toByteArray()
        } finally {
            bitmap?.recycle()
            scaledBitmap?.recycle()
        }
    }
    
    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun yuvToJpeg(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        
        val nv21 = ByteArray(width * height * 3 / 2)
        
        var pos = 0
        yBuffer.rewind()
        for (row in 0 until height) {
            val rowOffset = row * yRowStride
            yBuffer.position(rowOffset)
            for (col in 0 until width) {
                nv21[pos++] = yBuffer.get(rowOffset + col * yPixelStride)
            }
        }
        
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        uBuffer.rewind()
        vBuffer.rewind()
        
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uvOffset = row * uvRowStride + col * uvPixelStride
                nv21[pos++] = vBuffer.get(uvOffset)
                nv21[pos++] = uBuffer.get(uvOffset)
            }
        }
        
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val jpegBytes = out.toByteArray()
        
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        
        val matrix = android.graphics.Matrix()
        matrix.postRotate(270f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        
        val rotatedOut = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, rotatedOut)
        rotatedBitmap.recycle()
        
        return rotatedOut.toByteArray()
    }
    
    private fun showToast(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > TOAST_INTERVAL) {
            lastToastTime = currentTime
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
