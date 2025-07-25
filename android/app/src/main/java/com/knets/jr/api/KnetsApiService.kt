package com.knets.jr.api

import com.knets.jr.model.ApiResponse
import com.knets.jr.model.DeviceRegistration
import com.knets.jr.model.DeviceStatus
import com.knets.jr.model.Schedule
import retrofit2.http.*

interface KnetsApiService {
    
    @POST("api/companion/register")
    suspend fun registerDevice(@Body registration: DeviceRegistration): ApiResponse<Any>
    
    @GET("api/companion/schedules/{imei}")
    suspend fun getDeviceSchedules(@Path("imei") imei: String): List<Schedule>
    
    @PUT("api/companion/status/{imei}")
    suspend fun updateDeviceStatus(@Path("imei") imei: String, @Body status: DeviceStatus): ApiResponse<Any>
    
    @POST("api/companion/heartbeat/{imei}")
    suspend fun sendHeartbeat(@Path("imei") imei: String, @Body timestamp: Long): ApiResponse<Any>
    
    @GET("api/companion/device/{imei}")
    suspend fun getDeviceInfo(@Path("imei") imei: String): ApiResponse<DeviceStatus>
    
    @POST("api/companion/lock/{imei}")
    suspend fun lockDevice(@Path("imei") imei: String): ApiResponse<Any>
    
    @POST("api/companion/unlock/{imei}")
    suspend fun unlockDevice(@Path("imei") imei: String): ApiResponse<Any>
}