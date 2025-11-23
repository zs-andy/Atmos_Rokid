package com.example.atmos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.atmos.camera.CameraLogger
import com.example.atmos.detection.DetectionOverlayView
import com.example.atmos.network.YoloUploader
import com.example.atmos.network.FastVLMUploader
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TARGET_WIDTH = 1920
        private const val TARGET_HEIGHT = 1440
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewImageReader: ImageReader? = null
    private var captureImageReader: ImageReader? = null
    private var surfaceView: SurfaceView? = null
    private var blackMaskView: android.view.View? = null
    private var detectionOverlayView: DetectionOverlayView? = null
    private var statusText: TextView? = null
    private var connectionStatusText: TextView? = null
    private var frameCount = 0
    private var startTime = 0L
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private lateinit var yoloUploader: YoloUploader
    private lateinit var fastVLMUploader: FastVLMUploader
    private var lastYoloTime = 0L
    private var lastVLMTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        CameraLogger.logInfo("应用启动")
        
        // 初始化双服务器上传器和保存器
        yoloUploader = YoloUploader(this)
        fastVLMUploader = FastVLMUploader(this)
        
        // 设置 Yolo 检测结果回调
        yoloUploader.onDetectionResult = { detections, _, imgWidth, imgHeight ->
            runOnUiThread {
                detectionOverlayView?.updateDetections(
                    detections,
                    imgWidth.toFloat(),
                    imgHeight.toFloat()
                )
                updateStatus("FPS: %.1f | Yolo: ${detections.size} 个目标"
                    .format(if (frameCount > 0) frameCount * 1000.0 / (System.currentTimeMillis() - startTime) else 0.0))
            }
        }
        
        // 设置 FastVLM 结果回调
        fastVLMUploader.onVLMResult = { result, audio ->
            runOnUiThread {
                CameraLogger.logInfo("[FastVLM] 识别结果: $result, 音频: ${if (audio != null) "有" else "无"}")
                // 可以在这里显示识别结果到UI
            }
        }
        
        // 设置 Yolo 连接状态回调
        yoloUploader.onConnectionStatusChange = { status ->
            runOnUiThread {
                val (statusText, statusColor) = when (status) {
                    YoloUploader.ConnectionStatus.IDLE -> "Yolo: 空闲" to android.graphics.Color.GRAY
                    YoloUploader.ConnectionStatus.CONNECTING -> "Yolo: 连接中..." to android.graphics.Color.YELLOW
                    YoloUploader.ConnectionStatus.CONNECTED -> "Yolo: 已连接 ✓" to android.graphics.Color.GREEN
                    YoloUploader.ConnectionStatus.UPLOADING -> "Yolo: 上传中..." to android.graphics.Color.CYAN
                    YoloUploader.ConnectionStatus.ERROR -> "Yolo: 连接错误 ✗" to android.graphics.Color.RED
                }
                connectionStatusText?.text = statusText
                connectionStatusText?.setTextColor(statusColor)
            }
        }
        
        // 设置 FastVLM 连接状态回调
        fastVLMUploader.onConnectionStatusChange = { _ ->
            // 可以添加第二个状态指示器显示 VLM 状态
        }
        
        // 创建界面
        val layout = FrameLayout(this)
        
        //========== 画面调整参数 ==========
        val viewScaleX = 3.5f    // X轴缩放系数 (>1.0 放大裁剪中间部分)
        val viewScaleY = 3.5f    // Y轴缩放系数
        val viewOffsetX = 180f     // X轴位移（像素）
        val viewOffsetY = 100f     // Y轴位移（像素）
        //==================================

        // val viewScaleX = 1f    // X轴缩放系数 (>1.0 放大裁剪中间部分)
        // val viewScaleY = 1f    // Y轴缩放系数
        // val viewOffsetX = 0f     // X轴位移（像素）
        // val viewOffsetY = 0f     // Y轴位移（像素）
        
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
        }
        layout.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 添加黑色遮罩层（用于隐藏相机预览）
        blackMaskView = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        layout.addView(blackMaskView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 添加检测覆盖视图（使用相同的缩放参数）
        detectionOverlayView = DetectionOverlayView(this).apply {
            userScaleX = viewScaleX
            userScaleY = viewScaleY
            userOffsetX = viewOffsetX
            userOffsetY = viewOffsetY
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        layout.addView(detectionOverlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 在布局完成后应用缩放和位移到 SurfaceView
        surfaceView?.post {
            surfaceView?.apply {
                scaleX = viewScaleX
                scaleY = viewScaleY
                translationX = viewOffsetX
                translationY = viewOffsetY
            }
        }
        
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.argb(128, 0, 0, 0))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        })
        
        // 添加连接状态指示器
        connectionStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.argb(180, 0, 0, 0))
            setPadding(12, 8, 12, 8)
            text = "服务器: 未连接"
        }
        layout.addView(connectionStatusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            setMargins(16, 16, 16, 16)
        })
        
        setContentView(layout)
        
        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CameraLogger.logInfo("权限已授予")
            } else {
                updateStatus("错误：摄像头权限被拒绝")
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        CameraLogger.logInfo("Surface 已创建")
        openCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        CameraLogger.logInfo("Surface 尺寸: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        CameraLogger.logInfo("Surface 已销毁")
        releaseCamera()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            // 列出所有相机
            CameraLogger.logInfo("可用相机列表:")
            manager.cameraIdList.forEach { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                    CameraCharacteristics.LENS_FACING_BACK -> "后置"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
                    else -> "未知"
                }
                CameraLogger.logInfo("  相机 $id: $facing")
            }
            
            // 查找后置摄像头
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: return
            
            val characteristics = manager.getCameraCharacteristics(cameraId)
            
            // 选择最接近目标分辨率的预览尺寸
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            val previewSize = map.getOutputSizes(SurfaceHolder::class.java)
                .minByOrNull { Math.abs(it.width * it.height - TARGET_WIDTH * TARGET_HEIGHT) }
                ?: Size(TARGET_WIDTH, TARGET_HEIGHT)
            
            CameraLogger.logInfo("打开摄像头 $cameraId, 预览尺寸: ${previewSize.width}x${previewSize.height}")
            
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(previewSize)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    CameraLogger.logError(Exception("摄像头错误: $error"), "打开摄像头")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
            
        } catch (e: Exception) {
            CameraLogger.logError(e, "打开摄像头")
            updateStatus("错误: ${e.message}")
        }
    }

    private fun createCaptureSession(previewSize: Size) {
        try {
            val surface = surfaceView?.holder?.surface ?: return
            
            // 创建预览用的 ImageReader (YUV 格式用于显示和上传)
            previewImageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                5  // 增加缓冲区，避免处理慢时崩溃
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        frameCount++
                        
                        // 计算 FPS
                        if (frameCount == 1) {
                            startTime = System.currentTimeMillis()
                        } else if (frameCount % 30 == 0) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val fps = if (elapsed > 0) frameCount * 1000.0 / elapsed else 0.0
                            runOnUiThread {
                                updateStatus("FPS: %.1f | Yolo: 0.25s | VLM: 5s".format(fps))
                            }
                        }
                        
                        val currentTime = System.currentTimeMillis()
                        var shouldUploadYolo = false
                        var shouldUploadVLM = false
                        
                        // Yolo: 每 0.25 秒上传一次
                        if (currentTime - lastYoloTime >= 250) {
                            lastYoloTime = currentTime
                            shouldUploadYolo = true
                        }
                        
                        // FastVLM: 每 5 秒上传一次
                        if (currentTime - lastVLMTime >= 5000) {
                            lastVLMTime = currentTime
                            shouldUploadVLM = true
                        }
                        
                        // 根据需要上传到不同的服务器
                        if (shouldUploadYolo) {
                            yoloUploader.handleNewImage(image, lifecycleScope)
                        } else if (shouldUploadVLM) {
                            fastVLMUploader.handleNewImage(image, lifecycleScope)
                        } else {
                            image.close()
                        }
                    }
                }, backgroundHandler)
            }
            
            // 不再需要单独的 JPEG ImageReader，直接使用 YUV 预览流
            
            // 创建捕获会话
            val outputConfigs = listOf(
                OutputConfiguration(surface),
                OutputConfiguration(previewImageReader?.surface!!)
            )
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                Executor { it.run() },
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview(surface)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        CameraLogger.logError(Exception("配置失败"), "创建捕获会话")
                    }
                }
            )
            cameraDevice?.createCaptureSession(sessionConfig)
            
        } catch (e: Exception) {
            CameraLogger.logError(e, "创建捕获会话")
        }
    }

    private fun startPreview(surface: Surface) {
        try {
            val request = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
                addTarget(previewImageReader?.surface!!)
                // 自动对焦
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // 自动曝光
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }?.build() ?: return
            
            captureSession?.setRepeatingRequest(request, null, backgroundHandler)
            
            CameraLogger.logInfo("预览已启动")
            updateStatus("摄像头已激活 | Yolo: 0.2s | VLM: 5s")
            
        } catch (e: Exception) {
            CameraLogger.logError(e, "启动预览")
        }
    }
    


    private fun releaseCamera() {
        fastVLMUploader.release()
        detectionOverlayView?.clearDetections()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        previewImageReader?.close()
        previewImageReader = null
        frameCount = 0
        startTime = 0L
        lastYoloTime = 0L
        lastVLMTime = 0L
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun updateStatus(text: String) {
        runOnUiThread {
            statusText?.text = text
        }
    }

    override fun onPause() {
        super.onPause()
        CameraLogger.logInfo("Activity onPause - 保持相机运行")
    }

    override fun onResume() {
        super.onResume()
        CameraLogger.logInfo("Activity onResume")
        
        // 如果后台线程未启动，启动它
        if (backgroundThread == null) {
            startBackgroundThread()
        }
        
        // 如果相机未打开且 Surface 有效，打开相机
        if (cameraDevice == null && surfaceView?.holder?.surface?.isValid == true) {
            openCamera()
        }
    }
    
    override fun onStop() {
        super.onStop()
        CameraLogger.logInfo("Activity onStop - 释放相机")
        releaseCamera()
    }
    
    override fun onStart() {
        super.onStart()
        CameraLogger.logInfo("Activity onStart")
        
        // 确保后台线程启动
        if (backgroundThread == null) {
            startBackgroundThread()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        releaseCamera()
        stopBackgroundThread()
    }
}
