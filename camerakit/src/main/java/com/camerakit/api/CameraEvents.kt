package com.camerakit.api

interface CameraEvents {

    fun onCameraOpened(cameraAttributes: CameraAttributes)
    fun onCameraClosed()
    fun onCameraError()

    fun onPreviewStarted()
    fun onPreviewStopped()
    fun onPreviewError()

    //+lijiwei add
    fun startImageReader(callback: FrameCallback)

    fun stopImageReader()

    fun tapFocus(x: Int, y: Int)

    fun onTapFocusFinish()
    //-lijiwei add
}