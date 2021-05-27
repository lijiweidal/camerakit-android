package com.camerakit.api.camera2

import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.util.Log
import androidx.annotation.RequiresApi
import android.view.Surface
import com.camerakit.api.*
import com.camerakit.api.camera2.ext.*
import com.camerakit.type.CameraFacing
import com.camerakit.type.CameraFlash
import com.camerakit.type.CameraSize
import com.camerakit.util.ImageUtil


@RequiresApi(21)
@SuppressWarnings("MissingPermission")
class Camera2(eventsDelegate: CameraEvents, context: Context) :
        CameraApi, CameraEvents by eventsDelegate {

    override val cameraHandler: CameraHandler = CameraHandler.get()

    private val cameraManager: CameraManager =
            context.getSystemService(CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var cameraAttributes: CameraAttributes? = null

    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null
    private var photoCallback: ((jpeg: ByteArray) -> Unit)? = null

    private var flash: CameraFlash = CameraFlash.OFF
    private var previewStarted = false
    private var openPreview = false  //预览关闭后不处理onCaptureCompleted回调
    private var cameraFacing: CameraFacing = CameraFacing.BACK
    private var waitingFrames: Int = 0

    private var cameraCharacteristics: CameraCharacteristics? = null

    private var previewSize: CameraSize? = null

    @Synchronized
    override fun open(facing: CameraFacing) {
        cameraFacing = facing
        val cameraId = cameraManager.getCameraId(facing) ?: throw RuntimeException()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        this.cameraCharacteristics = cameraCharacteristics
        //cameraManager.whenDeviceAvailable(cameraId, cameraHandler) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                val cameraAttributes = Attributes(cameraCharacteristics, facing)
                this@Camera2.cameraDevice = cameraDevice
                this@Camera2.cameraAttributes = cameraAttributes
                onCameraOpened(cameraAttributes)
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
                cameraDevice.close()
                this@Camera2.cameraDevice = null
                this@Camera2.captureSession = null
                onCameraClosed()
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                cameraDevice.close()
                this@Camera2.cameraDevice = null
                this@Camera2.captureSession = null
            }
        }, cameraHandler)
        //}
    }

    @Synchronized
    override fun release() {
        cameraDevice?.close()
        cameraDevice = null
        captureSession?.close()
        captureSession = null
        cameraAttributes = null
        imageReader?.close()
        imageReader = null
        previewStarted = false
        onCameraClosed()
    }

    override fun destroy() {
        cameraHandler.quit()
    }

    @Synchronized
    override fun setPreviewOrientation(degrees: Int) {
    }

    @Synchronized
    override fun setPreviewSize(size: CameraSize) {
        Log.d("Camera2", "cameraSize=" + size.width + "*" + size.height)
        previewSize = size
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        openPreview = true
        val cameraDevice = cameraDevice
        val imageReader = imageReader
        if (cameraDevice != null && imageReader != null) {
            val surface = Surface(surfaceTexture)
            cameraDevice.getCaptureSession(surface, imageReader, cameraHandler) { captureSession ->
                try {
                    if (captureSession != null) {
                        this.captureSession = captureSession
                        val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        previewRequestBuilder.addTarget(surface)
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, cameraHandler)
                        this.previewRequestBuilder = previewRequestBuilder
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Synchronized
    override fun stopPreview() {
        openPreview = false
        val captureSession = captureSession
        this.captureSession = null
        if (captureSession != null) {
            try {
                captureSession.stopRepeating()
                captureSession.abortCaptures()
                captureSession.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onPreviewStopped()
            }
        }
        previewStarted = false
    }

    @Synchronized
    override fun setFlash(flash: CameraFlash) {
        this.flash = flash
    }

    @Synchronized
    override fun setPhotoSize(size: CameraSize) {
        this.imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
    }

    @Synchronized
    override fun capturePhoto(callback: (jpeg: ByteArray) -> Unit) {
        this.photoCallback = callback

        if (cameraFacing == CameraFacing.BACK) {
            //lockFocus()
            captureStillPicture()
        } else {
            captureStillPicture()
        }
    }

    //+lijiwei.youdao add
    override fun startImageReader(callback: FrameCallback) {

        //此处写死YUV格式尺寸大小为1280*720，使用1920及以上尺寸或者JPEG格式会出现卡顿问题。只有开启ImageReader才会重新配置
        this.imageReader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 3)

        val previewRequestBuilder = previewRequestBuilder
        val captureSession = captureSession
        captureSession!!.stopRepeating()
        if (previewRequestBuilder != null && captureSession != null) {
            try {
                previewRequestBuilder.addTarget(imageReader!!.surface)
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
                this.previewRequestBuilder = previewRequestBuilder
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        imageReader?.setOnImageAvailableListener({ reader ->
            if (reader != null) {
                try {
                    val image: Image? = reader.acquireLatestImage()
                    if (image != null) {
                        callback.onFrame(ImageUtil.imageToByteArray(image))
                        image.close()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }, null)
    }

    override fun stopImageReader() {
        imageReader?.setOnImageAvailableListener(null, null)
        val previewRequestBuilder = previewRequestBuilder
        val captureSession = captureSession
        captureSession?.stopRepeating()
        if (previewRequestBuilder != null && captureSession != null) {
            try {
                if (imageReader != null) {
                    previewRequestBuilder.removeTarget(imageReader!!.surface)
                }
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, cameraHandler)
                this.previewRequestBuilder = previewRequestBuilder
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun tapFocus(x: Int, y: Int) {

        val activeRect = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val right = activeRect!!.right
        val bottom = activeRect.bottom
        Log.d("Camera2", "rect=$activeRect")

        if (previewSize == null) return

        val previewWidth = previewSize!!.width
        val previewHeight = previewSize!!.height

        val leftPos = (x * right) / previewWidth
        val topPos = (y * bottom) / previewHeight

        val areaSize = (50 * right) / previewWidth

        val focusLeft = clamp(leftPos - areaSize, 0, right)
        val focusTop = clamp(topPos - areaSize, 0, bottom)
        val focusRight = clamp(leftPos + areaSize, leftPos, right)
        val focusBottom = clamp(topPos + areaSize, topPos, bottom)

        val newActiveRect = Rect(focusLeft, focusTop, focusRight, focusBottom)

        Log.d("Camera2", "newRect=$newActiveRect")

        previewRequestBuilder?.apply {
            set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(newActiveRect, 1000)))
            set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(newActiveRect, 1000)))
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        }

        try {
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), tapFocusCallback, cameraHandler)
        } catch (e: Exception) {
        }

    }

    private fun clamp(x: Int, min: Int, max: Int): Int {
        return if (x < min) {
            min
        } else if (x > max) {
            max
        } else {
            x
        }
    }
    //-lijiwei.youdao add

    //向CameraCaptureSession发送对焦请求
    private fun lockFocus() {
        val previewRequestBuilder = previewRequestBuilder
        val captureSession = captureSession
        if (previewRequestBuilder != null && captureSession != null) {
            try {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                captureState = STATE_WAITING_LOCK
                waitingFrames = 0
                captureSession.capture(previewRequestBuilder.build(), captureCallback, cameraHandler)
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runPreCaptureSequence() {
        val previewRequestBuilder = previewRequestBuilder
        val captureSession = captureSession
        if (previewRequestBuilder != null && captureSession != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            captureState = STATE_WAITING_PRECAPTURE
            captureSession.capture(previewRequestBuilder.build(), captureCallback, cameraHandler)
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, null)
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, when (this.flash) {
                CameraFlash.ON -> CaptureRequest.FLASH_MODE_TORCH
                else -> CaptureRequest.FLASH_MODE_OFF
            })
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, cameraHandler)

        }
    }

    private fun captureStillPicture() {
        val captureSession = captureSession
        val cameraDevice = cameraDevice
        val imageReader = imageReader
        if (captureSession != null && cameraDevice != null && imageReader != null) {
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder.set(CaptureRequest.FLASH_MODE, when (flash) {
                CameraFlash.ON -> CaptureRequest.FLASH_MODE_SINGLE
                else -> CaptureRequest.FLASH_MODE_OFF
            })

            val delay = when (flash) {
                CameraFlash.ON -> 75L
                else -> 0L
            }

            captureState = STATE_PREVIEW
            cameraHandler.postDelayed({
                captureSession.capture(captureBuilder.build(), captureCallback, cameraHandler)
            }, delay)
        }
    }

    private fun unlockFocus() {
        val previewRequestBuilder = previewRequestBuilder
        val captureSession = captureSession
        if (previewRequestBuilder != null && captureSession != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            captureSession.capture(previewRequestBuilder.build(), captureCallback, cameraHandler)
            captureState = STATE_PREVIEW
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, cameraHandler)
        }
    }

    private var captureState: Int = STATE_PREVIEW

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {

            when (captureState) {
                STATE_PREVIEW -> {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        photoCallback?.invoke(ImageUtil.imageToByteArray(image))
                        photoCallback = null
                        image.close()
                    }
                }
                STATE_WAITING_LOCK -> {
                    //val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    captureStillPicture()
                    /* if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                         runPreCaptureSequence()
                     } else if (null == afState || CaptureResult.CONTROL_AF_STATE_INACTIVE == afState) {
                         captureStillPicture()
                     } else if (waitingFrames >= 5) {
                         waitingFrames = 0
                         captureStillPicture()
                     } else {
                         waitingFrames++
                     }*/
                }
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        captureState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (!previewStarted && openPreview) {
                onPreviewStarted()
                previewStarted = true
            }
            process(result)
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            process(partialResult)
        }

    }

    private val tapFocusCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                Log.d("Camera2", "tap focus failed")
            }
            //Log.d("Camera2", "afState = $afState")
            if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                previewRequestBuilder?.apply {
                    set(CaptureRequest.CONTROL_AF_TRIGGER, null)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, null)
                }
                onTapFocusFinish()
                Log.d("Camera2", "tap focus finish")
                captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), captureCallback, cameraHandler)
            }
        }
    }

    companion object {
        private const val STATE_PREVIEW = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_PRECAPTURE = 2
        private const val STATE_WAITING_NON_PRECAPTURE = 3
        private const val STATE_PICTURE_TAKEN = 4
    }

    private class Attributes(cameraCharacteristics: CameraCharacteristics,
                             cameraFacing: CameraFacing) : CameraAttributes {

        override val facing: CameraFacing = cameraFacing

        override val sensorOrientation: Int = cameraCharacteristics.getSensorOrientation()

        override val previewSizes: Array<CameraSize> = cameraCharacteristics.getPreviewSizes()

        override val photoSizes: Array<CameraSize> = cameraCharacteristics.getPhotoSizes()

        override val flashes: Array<CameraFlash> = cameraCharacteristics.getFlashes()

    }

}