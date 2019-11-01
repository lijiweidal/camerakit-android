package com.camerakit.api

interface CameraEvents {

    fun onCameraOpened(cameraAttributes: CameraAttributes)
    fun onCameraClosed()
    fun onCameraError()

    fun onPreviewStarted()
    fun onPreviewStopped()
    fun onPreviewError()

    //+lijiwei add
    fun startCamera2PreView(callBack: FrameCallBack)

    fun stopCamera2PreView()

    fun tapFocus(x: Int, y: Int)

    fun onTapFocusFinish()
    //-lijiwei add
}