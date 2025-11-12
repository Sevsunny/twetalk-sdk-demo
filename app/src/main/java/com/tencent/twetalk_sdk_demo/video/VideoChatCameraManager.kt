package com.tencent.twetalk_sdk_demo.video

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tencent.twetalk.protocol.ImageMessage
import java.io.ByteArrayOutputStream

class VideoChatCameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onImageCaptured: (ImageMessage) -> Unit
) {
    companion object {
        private const val TAG = "VideoChatCameraManager"

        // JPEG 压缩质量（0-100）
        private const val JPEG_QUALITY = 85

        // 目标分辨率（作为参考，实际会根据设备能力调整）
        private val TARGET_RESOLUTION = Size(1280, 720)
    }

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(previewView.context))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val resolutionSelector = createResolutionSelector()

        // 预览配置
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // 图片捕获配置
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setJpegQuality(JPEG_QUALITY)
            .setTargetRotation(previewView.display.rotation)
            .build()
        
        // 摄像头选择器
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera.", e)
        }
    }
    
    fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(previewView.context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // 转换为 JPEG ByteArray
                    val imgMsg = imageProxyToImageMessage(image)
                    onImageCaptured(imgMsg)
                    image.close()
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Failed to capture.", exception)
                }
            }
        )
    }
    
    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        bindCameraUseCases()
    }

    /**
     * 创建智能分辨率选择器
     */
    private fun createResolutionSelector(): ResolutionSelector {
        val context = previewView.context

        // 获取屏幕尺寸作为参考
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 计算合适的目标分辨率（不超过屏幕尺寸，保持16:9或4:3比例）
        val targetSize = calculateOptimalResolution(screenWidth, screenHeight)

        Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}, target size: ${targetSize.width}x${targetSize.height}")

        return ResolutionSelector.Builder()
            // 设置分辨率策略：优先选择最接近目标分辨率的
            .setResolutionStrategy(
                ResolutionStrategy(
                    targetSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            // 设置宽高比策略：优先 16:9，降级到 4:3
            .setAspectRatioStrategy(
                AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            )
            .build()
    }

    /**
     * 计算最佳分辨率
     * 考虑屏幕尺寸和常用分辨率
     */
    private fun calculateOptimalResolution(screenWidth: Int, screenHeight: Int): Size {
        // 常用的分辨率
        val commonResolutions = listOf(
            Size(1920, 1080), // Full HD
            Size(1280, 720),  // HD
            Size(960, 540),   // qHD
            Size(640, 480)    // VGA
        )

        // 选择不超过屏幕尺寸的最大分辨率
        val maxDimension = maxOf(screenWidth, screenHeight)

        return commonResolutions.firstOrNull {
            it.width <= maxDimension || it.height <= maxDimension
        } ?: TARGET_RESOLUTION
    }

    private fun imageProxyToImageMessage(image: ImageProxy): ImageMessage {
        var bitmap = image.toBitmap()

        // 获取旋转角度
        val rotationDegrees = image.imageInfo.rotationDegrees

        // 判断是否需要镜像
//        val needMirror = lensFacing == CameraSelector.LENS_FACING_FRONT

        if (rotationDegrees != 0) {
            bitmap = rotateBitmap(bitmap, rotationDegrees)
        } 
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)

        val byteArray = stream.toByteArray()
        val finalWidth = bitmap.width
        val finalHeight = bitmap.height

        if (bitmap != image.toBitmap()) {
            bitmap.recycle()
        }

        return ImageMessage(
            byteArray,
            finalWidth,
            finalHeight,
            ImageMessage.ImageFormat.JPEG
        )
    }

    private var rotationMatrix: Matrix? = null

    private fun rotateBitmap(
        source: Bitmap,
        degrees: Int,
        mirror: Boolean = false
    ): Bitmap {
        val matrix = rotationMatrix ?: Matrix().also { rotationMatrix = it }
        matrix.reset()

        // 镜像
        if (mirror) {
            matrix.preScale(-1f, 1f)
        }

        // 旋转
        if (degrees != 0) {
            matrix.postRotate(degrees.toFloat())
        }

        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
    }

    fun release() {
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
    }
}
