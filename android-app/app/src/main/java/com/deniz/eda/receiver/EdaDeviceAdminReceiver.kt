package com.deniz.eda.receiver

import android.app.admin.DeviceAdminReceiver

/**
 * Telefonu kilitleme ozelligi icin gerekli admin receiver.
 * Kullanici ayarlardan "Eda" yoneticisini aktif ederse,
 * "telefonu kilitle" komutu calisir.
 */
class EdaDeviceAdminReceiver : DeviceAdminReceiver()
