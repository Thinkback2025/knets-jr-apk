package com.knets.jr.model

data class DeviceRegistration(
    val imei: String,
    val deviceName: String,
    val childName: String,
    val parentPhone: String,
    val deviceModel: String,
    val deviceBrand: String,
    val androidVersion: String
)

data class DeviceStatus(
    val imei: String,
    val isLocked: Boolean,
    val lastChecked: Long,
    val batteryLevel: Int,
    val isOnline: Boolean
)

data class Schedule(
    val id: Int,
    val name: String,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: List<String>,
    val isActive: Boolean
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)