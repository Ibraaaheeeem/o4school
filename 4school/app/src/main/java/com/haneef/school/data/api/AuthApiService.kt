package com.haneef.school.data.api

import com.haneef.school.data.models.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    
    @POST("auth/sign-up")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>
    
    @POST("auth/sign-in")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<SignUpResponse>

    @POST("auth/resend-otp")
    suspend fun resendOtp(@Body request: Map<String, String>): Response<SignUpResponse>
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: Map<String, String>): Response<SignUpResponse>
}