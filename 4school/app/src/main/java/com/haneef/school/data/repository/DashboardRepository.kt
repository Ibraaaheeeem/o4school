package com.haneef.school.data.repository

import android.util.Log
import com.google.gson.Gson
import com.haneef.school.data.api.DashboardApiService
import com.haneef.school.data.models.ApiError
import com.haneef.school.data.models.DashboardResponse
import com.haneef.school.data.models.ErrorResponse

class DashboardRepository(
    private val apiService: DashboardApiService,
    private val gson: Gson
) {
    
    companion object {
        private const val TAG = "DashboardRepository"
    }
    
    suspend fun getDashboardData(accessToken: String): Result<DashboardResponse> {
        return try {
            Log.d(TAG, "=== DASHBOARD REQUEST START ===")
            Log.d(TAG, "Request URL: GET /auth/dashboard")
            
            val response = apiService.getDashboardData("Bearer $accessToken")
            
            Log.d(TAG, "=== DASHBOARD RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { dashboardData ->
                    Log.d(TAG, "Dashboard data loaded successfully for school: ${dashboardData.schoolName}")
                    
                    // Log role-specific overview availability
                    dashboardData.adminOverview?.let { 
                        Log.d(TAG, "Admin overview: ${it.totalStudents} students, ${it.totalStaff} staff")
                    }
                    
                    Log.d(TAG, "=== DASHBOARD SUCCESS ===")
                    Result.success(dashboardData)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Log.e(TAG, "=== DASHBOARD FAILED ===")
                Log.e(TAG, "Response Code: ${response.code()}")
                Result.failure(Exception("Dashboard API request failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== DASHBOARD EXCEPTION ===")
            Log.e(TAG, "Exception: ${e.message}")
            Result.failure(e)
        }
    }
}