package com.haneef.school.data.api

import com.haneef.school.data.models.DashboardResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DashboardApiService {
    
    @GET("auth/dashboard")
    suspend fun getDashboardData(
        @Header("Authorization") authorization: String,

    ): Response<DashboardResponse>
}