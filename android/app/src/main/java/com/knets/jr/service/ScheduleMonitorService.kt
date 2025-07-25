package com.knets.jr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.knets.jr.R
import com.knets.jr.admin.KnetsDeviceAdminReceiver
import com.knets.jr.api.KnetsApiService
import com.knets.jr.api.RetrofitClient
import com.knets.jr.model.DeviceStatus
import com.knets.jr.model.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleMonitorService : Service() {
    
    companion object {
        private const val TAG = "ScheduleMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "knets_jr_service"
        private const val CHECK_INTERVAL = 30000L // 30 seconds
    }

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var apiService: KnetsApiService
    private lateinit var notificationManager: NotificationManager
    
    private var monitoringJob: Job? = null
    private var deviceImei: String? = null
    private var isDeviceLocked = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, KnetsDeviceAdminReceiver::class.java)
        apiService = RetrofitClient.instance.create(KnetsApiService::class.java)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        
        // Load device IMEI from preferences
        val prefs = getSharedPreferences("knets_jr_prefs", Context.MODE_PRIVATE)
        deviceImei = prefs.getString("device_imei", null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        startForeground(NOTIFICATION_ID, createNotification("Knets Jr is protecting this device"))
        
        startMonitoring()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Knets Jr Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Knets Jr parental control service"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Knets Jr")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkSchedulesAndEnforce()
                    delay(CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                    delay(CHECK_INTERVAL)
                }
            }
        }
    }

    private suspend fun checkSchedulesAndEnforce() {
        if (deviceImei == null) {
            Log.w(TAG, "No device IMEI configured")
            return
        }

        if (!isDeviceAdminActive()) {
            Log.w(TAG, "Device admin not active")
            return
        }

        try {
            // Fetch current schedules for this device
            val schedules = apiService.getDeviceSchedules(deviceImei!!)
            val activeSchedules = schedules.filter { isScheduleActive(it) }

            if (activeSchedules.isNotEmpty() && !isDeviceLocked) {
                // Should be locked
                lockDevice()
                updateNotification("Device locked - Schedule active")
                reportDeviceStatus(true)
            } else if (activeSchedules.isEmpty() && isDeviceLocked) {
                // Should be unlocked (but we can't unlock programmatically, just notify)
                updateNotification("Schedule ended - Device can be unlocked")
                reportDeviceStatus(false)
                isDeviceLocked = false
            }

            // Send heartbeat
            sendHeartbeat()

        } catch (e: Exception) {
            Log.e(TAG, "Error checking schedules", e)
        }
    }

    private fun isScheduleActive(schedule: Schedule): Boolean {
        if (!schedule.isActive) return false

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Convert to minutes for easier comparison
        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Parse schedule times
        val startParts = schedule.startTime.split(":")
        val endParts = schedule.endTime.split(":")
        val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
        val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

        // Check if current day is in schedule
        val dayMatches = when {
            schedule.daysOfWeek.contains("sunday") && currentDayOfWeek == Calendar.SUNDAY -> true
            schedule.daysOfWeek.contains("monday") && currentDayOfWeek == Calendar.MONDAY -> true
            schedule.daysOfWeek.contains("tuesday") && currentDayOfWeek == Calendar.TUESDAY -> true
            schedule.daysOfWeek.contains("wednesday") && currentDayOfWeek == Calendar.WEDNESDAY -> true
            schedule.daysOfWeek.contains("thursday") && currentDayOfWeek == Calendar.THURSDAY -> true
            schedule.daysOfWeek.contains("friday") && currentDayOfWeek == Calendar.FRIDAY -> true
            schedule.daysOfWeek.contains("saturday") && currentDayOfWeek == Calendar.SATURDAY -> true
            else -> false
        }

        if (!dayMatches) return false

        // Check time range (handle overnight schedules)
        return if (endMinutes > startMinutes) {
            // Same day schedule
            currentTimeMinutes in startMinutes..endMinutes
        } else {
            // Overnight schedule
            currentTimeMinutes >= startMinutes || currentTimeMinutes <= endMinutes
        }
    }

    private fun lockDevice() {
        try {
            if (isDeviceAdminActive()) {
                devicePolicyManager.lockNow()
                isDeviceLocked = true
                Log.d(TAG, "Device locked successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock device", e)
        }
    }

    private fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    private suspend fun reportDeviceStatus(locked: Boolean) {
        try {
            deviceImei?.let { imei ->
                val status = DeviceStatus(
                    imei = imei,
                    isLocked = locked,
                    lastChecked = System.currentTimeMillis(),
                    batteryLevel = getBatteryLevel(),
                    isOnline = true
                )
                apiService.updateDeviceStatus(imei, status)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report device status", e)
        }
    }

    private suspend fun sendHeartbeat() {
        try {
            deviceImei?.let { imei ->
                apiService.sendHeartbeat(imei, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send heartbeat", e)
        }
    }

    private fun getBatteryLevel(): Int {
        // Implement battery level detection
        return 100 // Placeholder
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        monitoringJob?.cancel()
    }
}