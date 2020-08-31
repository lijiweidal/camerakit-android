package com.camerakit.api

import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class CameraHandler private constructor(private val thread: HandlerThread) : Handler(thread.looper) {

    companion object {
        fun get(): CameraHandler {
            val cameraThread = HandlerThread("CameraHandler@${System.currentTimeMillis()}")
            cameraThread.start()
            return CameraHandler(cameraThread)
        }
    }

    fun quit() {
        thread.quit()
    }

    init {
        thread.setUncaughtExceptionHandler { thread, exception ->
            Log.d("CameraHandler", "thread name is " + thread.name + exception.toString())
        }
    }

}
