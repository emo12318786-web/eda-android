package com.deniz.eda.health

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Google Fit Manager — به جای Health Connect
 * 
 * مزایا:
 * - تو گوشی نصبه
 * - Zepp باهاش sync داره
 * - نیازی به Health Connect گوگل نداره
 */
class GoogleFitManager(private val context: Context) {

    companion object {
        private const val TAG = "EDA-GoogleFit"
        const val RC_SIGN_IN = 9001
    }

    /**
     * گزینه‌های Fitness — شامل همه مجوزهای لازم
     */
    val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
    }

    /**
     * آیا کاربر به Google Fit دسترسی داره؟
     */
    fun isAuthorized(): Boolean {
        return GoogleSignIn.hasPermissions(
            GoogleSignIn.getLastSignedInAccount(context),
            fitnessOptions
        )
    }

    /**
     * ساخت GoogleSignInClient برای درخواست دسترسی
     */
    fun getSignInClient(): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .addExtension(fitnessOptions)
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * آخرین ضربان قلب در X دقیقه اخیر
     */
    suspend fun getLatestHeartRate(minutesAgo: Long = 1440): Int? {
        return try {
            if (!isAuthorized()) {
                Log.w(TAG, "Google Fit'e yetki yok")
                return null
            }

            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.MINUTES.toMillis(minutesAgo)

            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .setLimit(1)
                .build()

            val response = Fitness.getHistoryClient(
                context,
                GoogleSignIn.getLastSignedInAccount(context)!!
            ).readData(request).await()

            val dataPoints = response.dataPoints
            if (dataPoints.isEmpty()) {
                Log.d(TAG, "HR verisi yok")
                return null
            }

            val lastPoint = dataPoints.last()
            for (field in lastPoint.dataType.fields) {
                if (field.name == Field.FIELD_BPM.name) {
                    val bpm = lastPoint.getValue(field).asFloat().toInt()
                    Log.i(TAG, "✅ HR: $bpm")
                    return bpm
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "HR hatası: ${e.message}")
            null
        }
    }

    /**
     * خواب دیشب (ساعت)
     */
    suspend fun getLastNightSleepHours(): Double? {
        return try {
            if (!isAuthorized()) return null

            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.HOURS.toMillis(24)

            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val response = Fitness.getHistoryClient(
                context,
                GoogleSignIn.getLastSignedInAccount(context)!!
            ).readData(request).await()

            var totalSleepMs = 0L
            for (dataPoint in response.dataPoints) {
                for (field in dataPoint.dataType.fields) {
                    if (field.name == Field.FIELD_DURATION.name) {
                        val duration = dataPoint.getValue(field).asInt().toLong()
                        totalSleepMs += duration
                    }
                }
            }

            if (totalSleepMs == 0L) return null
            val hours = totalSleepMs / 1000.0 / 60.0 / 60.0
            Log.i(TAG, "✅ Sleep: $hours saat")
            hours
        } catch (e: Exception) {
            Log.e(TAG, "Sleep hatası: ${e.message}")
            null
        }
    }

    /**
     * قدم‌های امروز
     */
    suspend fun getTodaySteps(): Long {
        return try {
            if (!isAuthorized()) return 0L

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val request = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val response = Fitness.getHistoryClient(
                context,
                GoogleSignIn.getLastSignedInAccount(context)!!
            ).readData(request).await()

            var totalSteps = 0L
            for (bucket in response.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (dataPoint in dataSet.dataPoints) {
                        for (field in dataPoint.dataType.fields) {
                            if (field.name == Field.FIELD_STEPS.name) {
                                totalSteps += dataPoint.getValue(field).asInt().toLong()
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "✅ Steps: $totalSteps")
            totalSteps
        } catch (e: Exception) {
            Log.e(TAG, "Steps hatası: ${e.message}")
            0L
        }
    }
}
