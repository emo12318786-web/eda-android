package com.deniz.eda.utils

import android.content.Context
import android.hardware.camera2.CameraManager

/** termux-torch cagrisinin native karsiligi - fener_ac()/fener_kapat(). */
object FlashlightUtils {

    private var aktif = false

    private fun geriTorchId(context: Context): String? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun ac(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = geriTorchId(context) ?: return false
            cm.setTorchMode(id, true)
            aktif = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun kapat(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = geriTorchId(context) ?: return false
            cm.setTorchMode(id, false)
            aktif = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun aktifMi(): Boolean = aktif
}
