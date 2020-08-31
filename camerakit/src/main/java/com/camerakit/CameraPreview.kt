package com.camerakit

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import com.camerakit.api.*
import com.camerakit.api.camera1.Camera1
import com.camerakit.api.camera2.Camera2
import com.camerakit.preview.CameraSurfaceTexture
import com.camerakit.preview.CameraSurfaceTextureListener
import com.camerakit.preview.CameraSurfaceView
import com.camerakit.type.CameraFacing
import com.camerakit.type.CameraFlash
import com.camerakit.type.CameraSize
import com.camerakit.util.CameraSizeCalculator
import jpegkit.Jpeg
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraPreview : FrameLayout, CameraEvents {

    companion object {
        private const val FORCE_DEPRECATED_API = false
    }

    var lifecycleState: LifecycleState = LifecycleState.STOPPED
    var surfaceState: SurfaceState = SurfaceState.SURFACE_WAITING
    var cameraState: CameraState = CameraState.CAMERA_CLOSED
        set(state) {
            field = state
            when (state) {
                CameraState.CAMERA_OPENED -> {
                    listener?.onCameraOpened()
                }
                CameraState.PREVIEW_STARTED -> {
                    listener?.onPreviewStarted()
                }
                CameraState.PREVIEW_STOPPED -> {
                    listener?.onPreviewStopped()
                }
                CameraState.CAMERA_CLOSING -> {
                    listener?.onCameraClosed()
                }
                else -> {
                    // ignore
                }
            }
        }

    var listener: Listener? = null

    var displayOrientation: Int = 0
    var previewOrientation: Int = 0
    var captureOrientation: Int = 0
    var previewSize: CameraSize = CameraSize(0, 0)
    var surfaceSize: CameraSize = CameraSize(0, 0)
        get() {
            return surfaceTexture?.size ?: field
        }

    var photoSize: CameraSize = CameraSize(0, 0)
    var flash: CameraFlash = CameraFlash.OFF
    var imageMegaPixels: Float = 2f

    private var cameraFacing: CameraFacing = CameraFacing.BACK
    private var surfaceTexture: CameraSurfaceTexture? = null
    private var attributes: CameraAttributes? = null

    private val cameraSurfaceView: CameraSurfaceView = CameraSurfaceView(context)

    private val cameraDispatcher: ExecutorCoroutineDispatcher = newSingleThreadContext("CAMERA")
    private var cameraOpenContinuation: CancellableContinuation<Unit>? = null
    private var previewStartContinuation: CancellableContinuation<Unit>? = null

    //标记Camera是否已准备释放
    private var done = false

    @SuppressWarnings("NewApi")
    private val cameraApi: CameraApi = ManagedCameraApi(
            when (Build.VERSION.SDK_INT < 21 || FORCE_DEPRECATED_API) {
                true -> Camera1(this)
                false -> Camera2(this, context)
            })

    constructor(context: Context) :
            super(context)

    constructor(context: Context, attributeSet: AttributeSet) :
            super(context, attributeSet)

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayOrientation = if (windowManager.defaultDisplay.rotation == 0) {
            windowManager.defaultDisplay.rotation * 90 + 90
        } else {
            windowManager.defaultDisplay.rotation * 90
        }
        cameraSurfaceView.cameraSurfaceTextureListener = object : CameraSurfaceTextureListener {
            override fun onSurfaceReady(cameraSurfaceTexture: CameraSurfaceTexture) {
                surfaceTexture = cameraSurfaceTexture
                surfaceState = SurfaceState.SURFACE_AVAILABLE
                if (lifecycleState == LifecycleState.STARTED || lifecycleState == LifecycleState.RESUMED) {
                    //Log.d("CameraPreview", "onSurfaceReady resume")
                    resume()
                }
            }
        }

        addView(cameraSurfaceView)
    }

    fun start(facing: CameraFacing) {
        //Log.d("CameraPreview", "start")
        GlobalScope.launch(cameraDispatcher) {
            //Log.d("CameraPreview", "start launch")
            runBlocking {
                //Log.d("CameraPreview", "start runBlocking")
                lifecycleState = LifecycleState.STARTED
                cameraFacing = facing
                openCamera()
                //Log.d("CameraPreview", "start runBlocking end")
            }
        }
    }

    fun resume() {
        done = false
        //Log.d("CameraPreview", "resume")
        GlobalScope.launch(cameraDispatcher) {
            //Log.d("CameraPreview", "resume launch")
            runBlocking {
                //Log.d("CameraPreview", "resume runBlocking")
                lifecycleState = LifecycleState.RESUMED
                try {
                    startPreview()
                } catch (e: Exception) {
                    //Log.d("CameraPreview", e.toString())
                }
                //Log.d("CameraPreview", "resume runBlocking end")
            }
        }
    }

    fun pause() {
        done = true
        //Log.d("CameraPreview", "pause")
        lifecycleState = LifecycleState.PAUSED
        stopPreview()
    }

    fun stop() {
        //Log.d("CameraPreview", "stop")
        lifecycleState = LifecycleState.STOPPED
        closeCamera()
    }

    fun destroy() {
        //Log.d("CameraPreview", "destroy")
        cameraOpenContinuation?.cancel()
        previewStartContinuation?.cancel()
        cameraDispatcher.cancelChildren()
        cameraDispatcher.cancel()
        cameraDispatcher.close()
        cameraApi.destroy()
    }

    //+lijiwei.youdao add
    override fun startCamera2PreView(callBack: FrameCallBack) {
        cameraApi.startCamera2PreView(callBack)
    }

    override fun stopCamera2PreView() {
        cameraApi.stopCamera2PreView()
    }
    //-lijiwei.youdao add

    fun capturePhoto(callback: PhotoCallback) {
        cameraApi.setFlash(flash)
        cameraApi.capturePhoto {
            cameraApi.cameraHandler.post {
                val jpeg = Jpeg(it)
                jpeg.rotate(captureOrientation)
                val transformedBytes = jpeg.jpegBytes
                jpeg.release()
                callback.onCapture(transformedBytes)
            }
        }
    }

    fun hasFlash(): Boolean {
        if (attributes?.flashes != null) {
            return true
        }
        return false
    }

    fun getSupportedFlashTypes(): Array<CameraFlash>? {
        return attributes?.flashes
    }

    interface PhotoCallback {
        fun onCapture(jpeg: ByteArray)
    }

    // CameraEvents:

    override fun onCameraOpened(cameraAttributes: CameraAttributes) {
        cameraState = CameraState.CAMERA_OPENED
        attributes = cameraAttributes
        //Log.d("CameraPreview", "onCameraOpened cameraOpenContinuation resume and null")
        cameraOpenContinuation?.resume(Unit)
        cameraOpenContinuation = null
    }

    override fun onCameraClosed() {
        cameraState = CameraState.CAMERA_CLOSED
    }

    override fun onCameraError() {
    }

    override fun onPreviewStarted() {
        cameraState = CameraState.PREVIEW_STARTED
        //Log.d("CameraPreview", "onPreviewStarted previewStartContinuation resume and null")
        previewStartContinuation?.resume(Unit)
        previewStartContinuation = null
    }

    override fun onPreviewStopped() {
        cameraState = CameraState.PREVIEW_STOPPED
    }

    override fun onPreviewError() {
    }

    //+lijiwei add
    override fun onTapFocusFinish() {
        Log.d("CameraPreview", "CameraPreview tap focus finish")
        listener?.onTapFocusFinish()
    }
    //-lijiwei add

    // State enums:

    enum class LifecycleState {
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED;
    }

    enum class SurfaceState {
        SURFACE_AVAILABLE,
        SURFACE_WAITING;
    }

    enum class CameraState {
        CAMERA_OPENING,
        CAMERA_OPENED,
        PREVIEW_STARTING,
        PREVIEW_STARTED,
        PREVIEW_STOPPING,
        PREVIEW_STOPPED,
        CAMERA_CLOSING,
        CAMERA_CLOSED;
    }

    // Camera control:

    private suspend fun openCamera(): Unit = suspendCancellableCoroutine {
        //Log.d("CameraPreview", "openCamera start")
        //Log.d("CameraPreview", "openCamera cameraOpenContinuation = it")
        cameraOpenContinuation = it
        cameraState = CameraState.CAMERA_OPENING
        cameraApi.open(cameraFacing)
        //Log.d("CameraPreview", "openCamera end")
    }

    private suspend fun startPreview(): Unit = suspendCancellableCoroutine {
        //Log.d("CameraPreview", "startPreview start")
        //Log.d("CameraPreview", "startPreview previewStartContinuation = it")
        if (done || cameraState == CameraState.PREVIEW_STARTED) {
            //已打算结束预览或已开启预览
            //Log.d("CameraPreview", "startPreview previewStartContinuation resumeWithException and null")
            it.resumeWithException(IllegalStateException())
            previewStartContinuation = null
        } else {
            previewStartContinuation = it
            val surfaceTexture = surfaceTexture
            val attributes = attributes
            if (surfaceTexture != null && attributes != null) {
                cameraState = CameraState.PREVIEW_STARTING
                previewOrientation = when (cameraFacing) {
                    CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                    CameraFacing.FRONT -> {
                        val result = (attributes.sensorOrientation + displayOrientation) % 360
                        (360 - result) % 360
                    }
                }

                Log.d("CameraPreview", "previewOri = $previewOrientation , displayOri = $displayOrientation, sensorOri = ${attributes.sensorOrientation}")

                captureOrientation = when (cameraFacing) {
                    CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                    CameraFacing.FRONT -> (attributes.sensorOrientation + displayOrientation + 360) % 360
                }

                if (Build.VERSION.SDK_INT >= 21 && !FORCE_DEPRECATED_API) {
                    surfaceTexture.setRotation(displayOrientation)
                }

                previewSize = CameraSizeCalculator(attributes.previewSizes)
                        .findClosestSizeContainingTarget(when (previewOrientation % 180 == 0) {
                            true -> CameraSize(width, height)
                            false -> CameraSize(height, width)
                        })

                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                surfaceTexture.size = when (previewOrientation % 180) {
                    0 -> previewSize
                    else -> CameraSize(previewSize.height, previewSize.width)
                }

                /*photoSize = CameraSizeCalculator(attributes.photoSizes)
                        .findClosestSizeMatchingArea((imageMegaPixels * 1000000).toInt())*/
                //拍照的图片大小更改为6M
                photoSize = when (previewOrientation % 180 == 0) {
                    true -> CameraSize(3264, 1840)
                    false -> CameraSize(1840, 3264)
                }
                Log.d("CameraPreview", "${photoSize.height} , ${photoSize.width}")

                cameraApi.setPreviewOrientation(previewOrientation)
                cameraApi.setPreviewSize(previewSize)
                cameraApi.setPhotoSize(photoSize)
                cameraApi.startPreview(surfaceTexture)
            } else {
                //Log.d("CameraPreview", "startPreview previewStartContinuation resumeWithException and null")
                it.resumeWithException(IllegalStateException())
                previewStartContinuation = null
            }
        }
        //Log.d("CameraPreview", "startPreview end")
    }

    private fun stopPreview() {
        cameraState = CameraState.PREVIEW_STOPPING
        cameraApi.stopPreview()
        //Log.d("CameraPreview", "stopPreview end")
    }

    private fun closeCamera() {
        cameraState = CameraState.CAMERA_CLOSING
        cameraApi.release()
        //Log.d("CameraPreview", "closeCamera end")
    }

    override fun tapFocus(x: Int, y: Int) {
        cameraApi.tapFocus(x, y)
    }

    // Listener:

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPreviewStarted()
        fun onPreviewStopped()

        //+lijiwei add
        fun onTapFocusFinish()
        //-lijiwei add
    }

}