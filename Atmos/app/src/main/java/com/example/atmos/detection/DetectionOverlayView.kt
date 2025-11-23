package com.example.atmos.detection

import android.content.Context
import android.graphics.*
import android.view.View

class DetectionOverlayView(context: Context) : View(context) {
    
    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)
    }
    
    private var detections: List<Detection> = emptyList()
    private var imageWidth = 640f
    private var imageHeight = 640f
    
    var userScaleX = 3.5f
    var userScaleY = 3.5f
    var userOffsetX = 180f
    var userOffsetY = 100f

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        textSize = 14f
        color = Color.WHITE
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    fun updateDetections(detections: List<Detection>, imgWidth: Float, imgHeight: Float) {
        this.detections = detections
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        invalidate()
    }
    
    fun clearDetections() {
        detections = emptyList()
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty() || width == 0 || height == 0) return
        
        val vw = width.toFloat()
        val vh = height.toFloat()
        val centerX = vw / 2f
        val centerY = vh / 2f
        
        // 计算 letterbox 参数
        val modelSize = imageWidth
        val scale = modelSize / maxOf(vw, vh)
        val scaledWidth = vh * scale
        val scaledHeight = vw * scale
        val padX = (modelSize - scaledWidth) / 2
        val padY = (modelSize - scaledHeight) / 2
        
        detections.forEach { detection ->
            boxPaint.color = Color.rgb(0, 255, 0)
            textBackgroundPaint.color = Color.argb(180, 0, 255, 0)
            
            // 去除 letterbox padding
            val xInScaled = detection.x - padX
            val yInScaled = detection.y - padY

            
            // 转换为归一化坐标 (0-1)
            val normX = xInScaled / scaledWidth
            val normY = yInScaled / scaledHeight
            val normW = detection.w / scaledWidth
            val normH = detection.h / scaledHeight
            
            // 归一化坐标 → View 坐标
            val vcx = normX * vw
            val vcy = normY * vh
            val vbw = normW * vw
            val vbh = normH * vh
            
            // 计算边界框
            val baseLeft = vcx - vbw / 2
            val baseTop = vcy - vbh / 2
            val baseRight = vcx + vbw / 2
            val baseBottom = vcy + vbh / 2
            
            // 应用用户缩放和位移
            val screenLeft = (baseLeft - centerX) * userScaleX + centerX + userOffsetX
            val screenTop = (baseTop - centerY) * userScaleY + centerY + userOffsetY
            val screenRight = (baseRight - centerX) * userScaleX + centerX + userOffsetX
            val screenBottom = (baseBottom - centerY) * userScaleY + centerY + userOffsetY
            
            // 检查可见性并绘制
            if (screenRight > 0 && screenLeft < vw && screenBottom > 0 && screenTop < vh) {
                // 绘制边框
                if (screenTop in 0f..vh) {
                    canvas.drawLine(
                        screenLeft.coerceIn(0f, vw), screenTop,
                        screenRight.coerceIn(0f, vw), screenTop, boxPaint
                    )
                }
                if (screenBottom in 0f..vh) {
                    canvas.drawLine(
                        screenLeft.coerceIn(0f, vw), screenBottom,
                        screenRight.coerceIn(0f, vw), screenBottom, boxPaint
                    )
                }
                if (screenLeft in 0f..vw) {
                    canvas.drawLine(
                        screenLeft, screenTop.coerceIn(0f, vh),
                        screenLeft, screenBottom.coerceIn(0f, vh), boxPaint
                    )
                }
                if (screenRight in 0f..vw) {
                    canvas.drawLine(
                        screenRight, screenTop.coerceIn(0f, vh),
                        screenRight, screenBottom.coerceIn(0f, vh), boxPaint
                    )
                }
            
                // 绘制标签
                val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
                val textBounds = Rect()
                textPaint.getTextBounds(label, 0, label.length, textBounds)
                
                val clippedLeft = screenLeft.coerceIn(0f, vw)
                val clippedTop = screenTop.coerceIn(0f, vh)
                
                val textX = (clippedLeft + 4).coerceIn(0f, vw - textBounds.width() - 8)
                val textY = if (clippedTop - 4 < textBounds.height() + 4) {
                    clippedTop + textBounds.height() + 8
                } else {
                    clippedTop - 4
                }
                
                canvas.drawRect(
                    textX - 2, textY - textBounds.height() - 2,
                    textX + textBounds.width() + 2, textY + 2,
                    textBackgroundPaint
                )
                canvas.drawText(label, textX, textY, textPaint)
            }
        }
    }
}
