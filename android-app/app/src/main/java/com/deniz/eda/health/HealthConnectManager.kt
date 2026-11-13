package com.deniz.eda.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "EDA-HealthConnect"
    }

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class)
    )

    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Could not read Health Connect permissions", e)
            emptySet()
        }
    }

    suspend fun canReadHeartRate(): Boolean {
        if (!isAvailable()) return false
        val permission = HealthPermission.getReadPermission(HeartRateRecord::class)
        return permission in getGrantedPermissions()
    }

    suspend fun canReadSleep(): Boolean {
        if (!isAvailable()) return false
        val permission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        return permission in getGrantedPermissions()
    }

    /** Diagnose whether EDA can read synced Health Connect data. */
    suspend fun getConnectionDiagnostic(): String {
        if (!isAvailable()) {
            android.util.Log.w(TAG, "Health Connect SDK/provider is not available")
            return "Health Connect kullanılamıyor. Google Health Connect uygulamasını kontrol et."
        }
        val granted = getGrantedPermissions()
        val missing = requiredPermissions - granted
        if (missing.isNotEmpty()) {
            android.util.Log.w(TAG, "Health permissions missing: $missing")
            val labels = buildList {
                if (HealthPermission.getReadPermission(HeartRateRecord::class) in missing) add("nabız")
                if (HealthPermission.getReadPermission(SleepSessionRecord::class) in missing) add("uyku")
                if (HealthPermission.getReadPermission(StepsRecord::class) in missing) add("adım")
            }.joinToString(", ")
            return "Health Connect izinleri eksik: $labels. EDA'nın sağlık izinlerini Health Connect ayarlarından yeniden aç."
        }
        val hr = getLatestHeartRate(7 * 24 * 60L)
        val sleep = getLastNightSleepHours()
        android.util.Log.i(TAG, "Health diagnostic: permissions=OK, HR=$hr, sleepHours=$sleep")
        return when {
            hr != null && sleep != null -> "Health Connect izinleri açık. Son nabız verisi $hr; uyku kaydı " + String.format(java.util.Locale.ROOT, "%.1f", sleep) + " saat."
            hr != null -> "Health Connect izinleri açık ve nabız verisi var ($hr), ancak uyku kaydı bulunamadı. Zepp Life senkronizasyonunu kontrol et."
            sleep != null -> "Health Connect izinleri açık ve uyku kaydı var, ancak nabız verisi bulunamadı. Zepp Life senkronizasyonunu kontrol et."
            else -> "Health Connect izinleri açık, ancak nabız ve uyku verisi gelmiyor. Zepp Life verilerinin Health Connect'e senkronize edildiğini kontrol et."
        }
    }

    suspend fun getLatestHeartRateSample(minutesAgo: Long = 1440): Pair<Int, Instant>? {
        val endTime = Instant.now()
        val startTime = endTime.minus(minutesAgo, ChronoUnit.MINUTES)
        return try {
            if (!isAvailable() || !canReadHeartRate()) return null
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.flatMap { it.samples }
                .filter { !it.time.isAfter(endTime) && !it.time.isBefore(startTime) }
                .maxByOrNull { it.time }
                ?.let { it.beatsPerMinute.toInt() to it.time }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Heart-rate query failed", e)
            null
        }
    }

    suspend fun getLatestHeartRate(minutesAgo: Long = 1440): Int? =
        getLatestHeartRateSample(minutesAgo)?.first

    suspend fun getLastNightSleepHours(): Double? {
        if (!isAvailable() || !canReadSleep()) return null
        val endTime = Instant.now()
        val startTime = endTime.minus(2, ChronoUnit.DAYS)
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
                .filter { !it.endTime.isAfter(endTime) }
                .maxByOrNull { it.endTime }
                ?.let { ChronoUnit.MINUTES.between(it.startTime, it.endTime) / 60.0 }
        } catch (e: Exception) { null }
    }

    suspend fun getTodaySteps(): Long {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
        val endTime = Instant.now()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) { 0L }
    }
    /** Today's active energy reported by apps syncing to Health Connect. */
    suspend fun getTodayCaloriesKcal(): Double? {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()
        return try {
            if (!isAvailable() || HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) !in getGrantedPermissions()) return null
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endTime)
                )
            )
            response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Calories query failed", e)
            null
        }
    }

    /** Today's distance reported by apps syncing to Health Connect. */
    suspend fun getTodayDistanceMeters(): Double? {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()
        return try {
            if (!isAvailable() || HealthPermission.getReadPermission(DistanceRecord::class) !in getGrantedPermissions()) return null
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endTime)
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inMeters
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Distance query failed", e)
            null
        }
    }

}
