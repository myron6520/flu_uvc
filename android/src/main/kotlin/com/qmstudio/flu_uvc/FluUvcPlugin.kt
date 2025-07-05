package com.qmstudio.flu_uvc
import android.Manifest
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import android.graphics.BitmapFactory
import android.graphics.YuvImage
import android.graphics.Rect
import android.graphics.ImageFormat
import java.io.ByteArrayOutputStream
import android.content.Context
import android.hardware.camera2.*
import android.hardware.usb.*
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresPermission
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.EnumMap
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
  private var mainHandler: Handler? = null
  private val cameraOpenCloseLock = Semaphore(1)
  private val captureLock = Object()
  @Volatile private var imageData: ByteArray? = null
  @Volatile private var isCapturing = false
  @Volatile private var isImageReaderReleased = false
  
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flu_uvc")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    mainHandler = Handler(Looper.getMainLooper())
  }

  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("CameraBackground").apply {
      start()
      backgroundHandler = Handler(looper)
    }
  }

  private fun stopBackgroundThread() {
    try {
      backgroundThread?.quitSafely()
      backgroundThread?.join()
    } catch (e: InterruptedException) {
      Log.e("FluUvcPlugin", "Error stopping background thread: ${e.message}")
    } finally {
      backgroundThread = null
      backgroundHandler = null
    }
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "canScan" -> {
        result.success(findUsbCameraId()!=null)
      }
      "startScan" -> {
        startCapture()
        result.success(true)
      }
      "getImage" -> {
        getImage(result)
      }
      "stopScan" -> {
        stopCapture()
        releaseCamera(result)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  // 添加条形码解析方法
  private fun decodeBarcode(imageBytes: ByteArray): String? {
    try {
      // 将 JPEG 数据转换为 Bitmap
      val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

      // 创建 ZXing 的 RGBLuminanceSource
      val width = bitmap.width
      val height = bitmap.height
      val pixels = IntArray(width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

      val source = RGBLuminanceSource(width, height, pixels)
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

      // 尝试多种格式
      val formats = arrayOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E
      )

      val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
      hints[DecodeHintType.TRY_HARDER] = true
      hints[DecodeHintType.POSSIBLE_FORMATS] = formats.toList()

      val reader = MultiFormatReader()
      val result = reader.decode(binaryBitmap, hints)

      return result.text
    } catch (e: Exception) {
      Log.d("FluUvcPlugin", "Barcode decode failed: ${e.message}")
      return null
    }
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  private fun initCamera() {
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        return
      }

      // 确保 cameraManager 存在
      if (cameraManager == null) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      }

      // 获取USB摄像头ID
      val cameraId = findUsbCameraId()
      Log.e("FluUvcPlugin", "cameraId=" + cameraId);
      if (cameraId == null) {
        return
      }

      startBackgroundThread()

      // 创建ImageReader
      isImageReaderReleased = false

      imageReader = ImageReader.newInstance(
          640, 480, ImageFormat.YUV_420_888, 2
      ).apply {
          setOnImageAvailableListener({ reader ->
              if (!isCapturing || isImageReaderReleased) return@setOnImageAvailableListener

              val startTime = System.currentTimeMillis()
              val image = try {
                  reader.acquireLatestImage()
              } catch (e: Exception) {
                  Log.e("FluUvcPlugin", "Image acquire failed: ${e.message}")
                  null
              }

              image?.let {
                try {
                  val yBuffer = it.planes[0].buffer
                  val uBuffer = it.planes[1].buffer
                  val vBuffer = it.planes[2].buffer

                  val ySize = yBuffer.remaining()
                  val uSize = uBuffer.remaining()
                  val vSize = vBuffer.remaining()

                  val nv21 = ByteArray(ySize + uSize + vSize)
                  yBuffer.get(nv21, 0, ySize)

                  val pixelStride = it.planes[1].pixelStride
                  var uvIndex = ySize
                  for (i in 0 until uSize step pixelStride) {
                    nv21[uvIndex++] = vBuffer.get(i)
                    nv21[uvIndex++] = uBuffer.get(i)
                  }

                  val yuvImage = YuvImage(nv21, ImageFormat.NV21, it.width, it.height, null)
                  val out = ByteArrayOutputStream()
                  yuvImage.compressToJpeg(Rect(0, 0, it.width, it.height), 90, out)
                  val jpegData = out.toByteArray()

                  synchronized(captureLock) {
                    imageData = jpegData
                    decodeBarcode(jpegData)?.let { code ->
                      val endTime = System.currentTimeMillis()
                      val duration = endTime - startTime
                      Log.d("FluUvcPlugin", "Barcode decode took --------------------------> ${duration}ms")

                      mainHandler?.post {
                        channel.invokeMethod("onBarcodeDetected", code)
                      }
                    }
                  }
                } catch (e: Exception) {
                  Log.e("FluUvcPlugin", "Image processing error: ${e.message}")
                } finally {
                  try { 
                    it.close() 
                  } catch (_: Exception) {}
                }
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
          Log.e("CAMERA_ERROR", "onError: $error", )
        }
      }, backgroundHandler)

    } catch (e: Exception) {
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  private fun findUsbCameraId(): String? {
    Log.e("TAG", "开始查找USB摄像头...")
    try {
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
    } catch (e: Exception) {
      Log.e("FluUvcPlugin", "error: cameraManager.cameraIdList: ${e.message}")
      return null
    }
  }

  private var surfaceTexture: SurfaceTexture? = null
  private var surface: Surface? = null
  
  private fun createCameraPreviewSession() {
    try {
      // 确保之前的资源被释放
      surfaceTexture?.release()
      surface?.release()
      
      // 创建新的 SurfaceTexture
      surfaceTexture = SurfaceTexture(0)
      surface = Surface(surfaceTexture)
      val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      previewRequestBuilder?.addTarget(surface!!)
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

  private fun startCapture() {
    synchronized(captureLock) {
      if (isCapturing) {
        return
      }
      initCamera()
      isCapturing = true
    }
  }

  private fun stopCapture() {
    synchronized(captureLock) {
      isCapturing = false
    }
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
                        "data" to rgbBytes,
                        "jpeg" to imageData
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
        isImageReaderReleased = true
      }
      // 3. 关闭图像读取器
      try {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
      } catch (e: Exception) {
        Log.e("FluUvcPlugin", "Error closing image reader: ${e.message}")
      } finally {
        imageReader = null
      }

      // 2. 关闭捕获会话
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
        if (cameraDevice != null) {
          val device = cameraDevice
          cameraDevice = null
          device?.close()
        }
      } catch (e: Exception) {
        Log.e("FluUvcPlugin", "Error closing camera device: ${e.message}")
      }

      // 5. 停止后台线程
      stopBackgroundThread()

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
