package com.camerakit.api

interface CameraEvents {

    fun onCameraOpened(cameraAttributes: CameraAttributes)
    fun onCameraClosed()
    fun onCameraError()

    fun onPreviewStarted()
    fun onPreviewStopped()
    fun onPreviewError()

    //+lijiwei add for get frame data
    fun startCamera2PreView(callBack: FrameCallBack)
    fun stopCamera2PreView()
    //-lijiwei add for get frame data
}