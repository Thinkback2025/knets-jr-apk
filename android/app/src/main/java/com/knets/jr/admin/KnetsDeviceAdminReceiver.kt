package com.knets.jr.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class KnetsDeviceAdminReceiver : DeviceAdminReceiver() {
    
    companion object {
        private const val TAG = "KnetsDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Knets Jr Device Admin enabled")
        Toast.makeText(context, "Knets Jr protection enabled", Toast.LENGTH_SHORT).show()
        
        // Start the schedule monitoring service
        context.startService(Intent(context, com.knets.jr.service.ScheduleMonitorService::class.java))
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Knets Jr Device Admin disabled")
        Toast.makeText(context, "Knets Jr protection disabled", Toast.LENGTH_SHORT).show()
        
        // Stop the schedule monitoring service
        context.stopService(Intent(context, com.knets.jr.service.ScheduleMonitorService::class.java))
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordChanged(context, intent, user)
        Log.d(TAG, "Device password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.d(TAG, "Device password failed")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.d(TAG, "Device password succeeded")
    }

    override fun onLockTaskModeEntering(
        context: Context,
        intent: Intent,
        pkg: String
    ) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock task mode entering: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock task mode exiting")
    }
}