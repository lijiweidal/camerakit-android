package com.camerakit.api

interface CameraEvents {

    fun onCameraOpened(cameraAttributes: CameraAttributes)
    fun onCameraClosed()
    fun onCameraError()

    fun onPreviewStarted()
    fun onPreviewStopped()
    fun onPreviewError()

    //+lijiwei.youdao add
    fun startCamera2PreView(callBack: FrameCallBack)
    fun stopCamera2PreView()
    //-lijiwei.youdao add
}