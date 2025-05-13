package com.qmstudio.flu_uvc

import android.content.Context
import android.hardware.camera2.*
import android.hardware.usb.*
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** FluUvcPlugin */
class FluUvcPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private var cameraManager: CameraManager? = null
  private var cameraDevice: CameraDevice? = null
  private var captureSession: CameraCaptureSession? = null
  private var imageReader: ImageReader? = null
  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private val cameraOpenCloseLock = Semaphore(1)
  private var isCapturing = false
  private var imageData: ByteArray? = null
  private val captureLock = Object()

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flu_uvc")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  }

  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("CameraBackground").apply {
      start()
      backgroundHandler = Handler(looper)
    }
  }

  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Log.e("FluUvcPlugin", "Error stopping background thread: ${e.message}")
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "initCamera" -> {
        initCamera(result)
      }
      "startCapture" -> {
        startCapture(result)
      }
      "stopCapture" -> {
        stopCapture(result)
      }
      "getImage" -> {
        getImage(result)
      }
      "releaseCamera" -> {
        releaseCamera(result)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun initCamera(result: Result) {
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        result.error("CAMERA_BUSY", "Failed to acquire camera lock", null)
        return
      }

      // 确保 cameraManager 存在
      if (cameraManager == null) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      }

      // 获取USB摄像头ID
      val cameraId = findUsbCameraId()
      if (cameraId == null) {
        result.error("NO_CAMERA", "No USB camera found", null)
        return
      }

      startBackgroundThread()

      // 创建ImageReader
      imageReader = ImageReader.newInstance(
        640, 480, ImageFormat.JPEG, 2
      ).apply {
        setOnImageAvailableListener({ reader ->
          val image = reader.acquireLatestImage()
          try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            synchronized(captureLock) {
              imageData = bytes
            }
          } finally {
            image.close()
          }
        }, backgroundHandler)
      }

      // 打开相机
      cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
          cameraDevice = camera
          createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
          camera.close()
          cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
          camera.close()
          cameraDevice = null
          result.error("CAMERA_ERROR", "Camera error: $error", null)
        }
      }, backgroundHandler)

      result.success(true)
    } catch (e: Exception) {
      result.error("INIT_ERROR", "Failed to initialize camera: ${e.message}", null)
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  private fun findUsbCameraId(): String? {
    Log.e("TAG", "开始查找USB摄像头...")
    cameraManager?.cameraIdList?.forEach { id ->
      val characteristics = cameraManager?.getCameraCharacteristics(id)
      val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
      val facingStr = when(facing) {
        CameraCharacteristics.LENS_FACING_BACK -> "后置摄像头"
        CameraCharacteristics.LENS_FACING_FRONT -> "前置摄像头"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置摄像头"
        else -> "未知类型"
      }
      
      // 获取相机支持的分辨率
      val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      val sizes = map?.getOutputSizes(ImageFormat.JPEG)
      
      Log.e("TAG", """
        相机ID: $id
        类型: $facingStr
        支持的分辨率: ${sizes?.joinToString { "${it.width}x${it.height}" }}
        硬件级别: ${characteristics?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}
        是否支持自动对焦: ${characteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.isNotEmpty()}
      """.trimIndent())
    }
    
    return cameraManager?.cameraIdList?.find { id ->
      val characteristics = cameraManager?.getCameraCharacteristics(id)
      val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
      facing == CameraCharacteristics.LENS_FACING_EXTERNAL
    }
  }

  private fun createCameraPreviewSession() {
    try {
      val texture = SurfaceTexture(0)
      val surface = Surface(texture)
      val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      previewRequestBuilder?.addTarget(surface)
      previewRequestBuilder?.addTarget(imageReader?.surface!!)

      cameraDevice?.createCaptureSession(
        listOf(surface, imageReader?.surface!!),
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
              previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
              captureSession?.setRepeatingRequest(
                previewRequestBuilder?.build()!!,
                null,
                backgroundHandler
              )
            } catch (e: Exception) {
              Log.e("FluUvcPlugin", "Error starting camera preview: ${e.message}")
            }
          }

          override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e("FluUvcPlugin", "Failed to configure camera session")
          }
        },
        null
      )
    } catch (e: Exception) {
      Log.e("FluUvcPlugin", "Error creating camera preview session: ${e.message}")
    }
  }

  private fun startCapture(result: Result) {
    synchronized(captureLock) {
      if (isCapturing) {
        result.error("ALREADY_CAPTURING", "Camera is already capturing", null)
        return
      }
      isCapturing = true
    }
    result.success(true)
  }

  private fun stopCapture(result: Result?) {
    synchronized(captureLock) {
      isCapturing = false
    }
    result?.success(true)
  }

  private fun getImage(res: Result) {
    try {
        val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder?.addTarget(imageReader?.surface!!)
        
        captureSession?.capture(captureRequestBuilder?.build()!!, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                synchronized(captureLock) {
                    if (imageData == null) {
                        res.error("NO_IMAGE", "No image data available", null)
                        return
                    }
                    
                    // 将 JPEG 转换为 RGB
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData!!.size)
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    
                    // 创建 RGB 字节数组
                    val rgbBytes = ByteArray(width * height * 3)
                    var index = 0
                    for (pixel in pixels) {
                        rgbBytes[index++] = (pixel shr 16 and 0xFF).toByte() // R
                        rgbBytes[index++] = (pixel shr 8 and 0xFF).toByte()  // G
                        rgbBytes[index++] = (pixel and 0xFF).toByte()        // B
                    }
                    
                    Log.e("FluUvcPlugin", "rgbBytes: $width $height")
                    // 返回宽度、高度和 RGB 数据
                    val result = mapOf(
                        "width" to width,
                        "height" to height,
                        "data" to rgbBytes
                    )
                    res.success(result)
                }
            }
        }, backgroundHandler)
    } catch (e: Exception) {
        res.error("CAPTURE_ERROR", "Failed to capture image: ${e.message}", null)
    }
  }

  private fun releaseCamera(result: Result?) {
    try {
      // 1. 停止捕获
      synchronized(captureLock) {
        isCapturing = false
        imageData = null
      }

      // 2. 停止后台线程
      stopBackgroundThread()

      // 3. 关闭捕获会话
      try {
        captureSession?.stopRepeating()
        captureSession?.close()
      } catch (e: Exception) {
        Log.e("FluUvcPlugin", "Error closing capture session: ${e.message}")
      } finally {
        captureSession = null
      }

      // 4. 关闭相机设备
      try {
        cameraDevice?.close()
      } catch (e: Exception) {
        Log.e("FluUvcPlugin", "Error closing camera device: ${e.message}")
      } finally {
        cameraDevice = null
      }

      // 5. 关闭图像读取器
      try {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
      } catch (e: Exception) {
        Log.e("FluUvcPlugin", "Error closing image reader: ${e.message}")
      } finally {
        imageReader = null
      }

      // 6. 释放相机锁
      if (cameraOpenCloseLock.availablePermits() == 0) {
        cameraOpenCloseLock.release()
      }

      Log.d("FluUvcPlugin", "Camera resources released successfully")
      result?.success(true)
    } catch (e: Exception) {
      Log.e("FluUvcPlugin", "Error releasing camera resources: ${e.message}")
      result?.error("RELEASE_ERROR", "Failed to release camera: ${e.message}", null)
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    releaseCamera(null)
  }

  companion object {
    private const val ACTION_USB_PERMISSION = "com.qmstudio.flu_uvc.USB_PERMISSION"
  }
}
