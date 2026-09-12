package com.deniz.eda.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Orijinal guvenlik modu ("telefonu kimse ellemesin") ozelliginin karsiligi.
 * Ivmeolcer (accelerometer) ile ani hareket algilanirsa alarmTetiklendi
 * callback'i cagrilir (servis bunu TTS ile yuksek sesle uyari vermek icin
 * kullanir).
 */
class SecurityMode(context: Context, private val alarmTetiklendi: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val ivmeSensoru = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var referansX = 0f
    private var referansY = 0f
    private var referansZ = 0f
    private var referansAlindiMi = false
    private var sonAlarmZamani = 0L

    // Bu esigin uzerindeki ani degisimler "hareket ettirildi" sayilir.
    private val HAREKET_ESIGI = 2.5f
    private val ALARM_ARALIGI_MS = 5000L

    var aktif = false
        private set

    fun baslat() {
        if (aktif || ivmeSensoru == null) return
        aktif = true
        referansAlindiMi = false
        sensorManager.registerListener(this, ivmeSensoru, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun durdur() {
        if (!aktif) return
        aktif = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        if (!referansAlindiMi) {
            referansX = x; referansY = y; referansZ = z
            referansAlindiMi = true
            return
        }
        val fark = abs(x - referansX) + abs(y - referansY) + abs(z - referansZ)
        if (fark > HAREKET_ESIGI) {
            val simdi = System.currentTimeMillis()
            if (simdi - sonAlarmZamani > ALARM_ARALIGI_MS) {
                sonAlarmZamani = simdi
                alarmTetiklendi()
            }
        }
        referansX = x; referansY = y; referansZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
