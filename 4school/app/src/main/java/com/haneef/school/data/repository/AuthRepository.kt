package com.haneef.school.data.repository

import android.util.Log
import com.google.gson.Gson
import com.haneef.school.data.api.AuthApiService
import com.haneef.school.data.models.*

interface SessionStore {
    suspend fun saveAccessToken(token: String)
    suspend fun saveSchoolId(schoolId: String?)
}
class AuthRepository(
    private val apiService: AuthApiService,
    private val gson: Gson,
    private val sessionStore: SessionStore
) {
    
    companion object {
        private const val TAG = "AuthRepository"
    }
    
    suspend fun signUp(request: SignUpRequest): Result<SignUpResponse> {
        return try {
            Log.d(TAG, "=== SIGNUP REQUEST START ===")
            Log.d(TAG, "Request URL: POST /auth/signup")
            Log.d(TAG, "Request Body: ${gson.toJson(request)}")
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  - Email: ${request.email}")
            Log.d(TAG, "  - First Name: ${request.firstName}")
            Log.d(TAG, "  - Last Name: ${request.lastName}")
            Log.d(TAG, "  - Role: ${request.role}")
            Log.d(TAG, "  - Phone: ${request.phoneNumber} (${request.phoneCountryCode})")
            Log.d(TAG, "  - Address Line 1: ${request.addressLine1}")
            Log.d(TAG, "  - Address Line 2: ${request.addressLine2}")
            Log.d(TAG, "  - City: ${request.city}")
            Log.d(TAG, "  - State: ${request.state}")
            Log.d(TAG, "  - Country: ${request.country}")
            
            val response = apiService.signUp(request)
            
            Log.d(TAG, "=== SIGNUP RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Response Message: ${response.message()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Log.d(TAG, "Response Body: ${gson.toJson(body)}")
                    Log.d(TAG, "Response Details:")
                    Log.d(TAG, "  - User ID: ${body.userId}")
                    Log.d(TAG, "  - Email: ${body.email}")
                    Log.d(TAG, "  - Role: ${body.role}")
                    Log.d(TAG, "  - School ID: ${body.schoolId}")
                    Log.d(TAG, "  - School Name: ${body.schoolName}")
                    Log.d(TAG, "  - User School Role ID: ${body.userSchoolRoleId}")
                    Log.d(TAG, "  - Message: ${body.message}")
                    Log.d(TAG, "  - Next Route: ${body.nextRoute}")
                    Log.d(TAG, "=== SIGNUP SUCCESS ===")
                    Result.success(body)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error Response Body: $errorBody")
                Log.e(TAG, "Error Headers: ${response.headers()}")
                
                val apiError = try {
                    gson.fromJson(errorBody, ApiError::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse error response: ${e.message}")
                    ApiError("UNKNOWN_ERROR", "An unknown error occurred")
                }
                
                Log.e(TAG, "Parsed API Error: ${gson.toJson(apiError)}")
                Log.e(TAG, "=== SIGNUP FAILED ===")
                Result.failure(Exception(apiError.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== SIGNUP EXCEPTION ===")
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            Log.e(TAG, "Exception Stack Trace:", e)
            Result.failure(e)
        }
    }
    
    suspend fun login(request: LoginRequest): Result<LoginResponse> {
        return try {
            Log.d(TAG, "=== LOGIN REQUEST START ===")
            Log.d(TAG, "Request URL: POST /auth/login")
            Log.d(TAG, "Request Body: ${gson.toJson(request)}")
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  - Email: ${request.email}")
            Log.d(TAG, "  - Password: [HIDDEN]")
            
            val response = apiService.login(request)
            
            Log.d(TAG, "=== LOGIN RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Response Message: ${response.message()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Log.d(TAG, "Response Body: ${gson.toJson(body)}")
                    Log.d(TAG, "Response Details:")
                    Log.d(TAG, "  - User ID: ${body.userId}")
                    Log.d(TAG, "  - Email: ${body.email}")
                    Log.d(TAG, "  - First Name: ${body.firstName}")
                    Log.d(TAG, "  - Last Name: ${body.lastName}")
                    Log.d(TAG, "  - Access Token: ${if (body.accessToken.isNotEmpty()) "[TOKEN_PRESENT]" else "[NO_TOKEN]"}")
                    Log.d(TAG, "  - Refresh Token: ${if (body.refreshToken != null) "[REFRESH_TOKEN_PRESENT]" else "[NO_REFRESH_TOKEN]"}")
                    Log.d(TAG, "  - Token Type: ${body.tokenType}")
                    Log.d(TAG, "  - Expires In: ${body.expiresIn} seconds")
                    Log.d(TAG, "  - Message: ${body.message}")
                    Log.d(TAG, "  - Next Route: ${body.nextRoute}")
                    Log.d(TAG, "  - Status: ${body.status}")
                    Log.d(TAG, "  - Schools Count: ${body.schools.size}")
                    body.schools.forEachIndexed { index, school ->
                        Log.d(TAG, "    School $index: ${school.schoolName} (${school.schoolId})")
                        Log.d(TAG, "      - Primary: ${school.isPrimary}")
                        Log.d(TAG, "      - Roles: ${school.roles.map { it.roleName }}")
                    }
                    Log.d(TAG, "=== LOGIN SUCCESS ===")
                    if (body.accessToken.isNotEmpty()) {
                        sessionStore.saveAccessToken(body.accessToken)
                        Log.d(TAG, "Access token persisted")
                    } else {
                        Log.w(TAG, "Access token missing, skipping persistence")
                    }

                        // Persist first primary school id, otherwise first/only school id
                    val schoolIdToPersist = body.schools .firstOrNull { it.isPrimary }?.schoolId ?: body.schools.firstOrNull()?.schoolId
                    if (!schoolIdToPersist.isNullOrBlank()) {
                        sessionStore.saveSchoolId(schoolIdToPersist)
                        Log.d(TAG, "School ID persisted: $schoolIdToPersist")
                    } else {
                        Log.w(TAG, "No school id available to persist")
                    }

                    Log.d(TAG, "=== LOGIN SUCCESS ===")
                    Result.success(body)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error Response Body: $errorBody")
                
                val apiError = try {
                    gson.fromJson(errorBody, ApiError::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse error response: ${e.message}")
                    ApiError("UNKNOWN_ERROR", "An unknown error occurred")
                }
                
                Log.e(TAG, "Parsed API Error: ${gson.toJson(apiError)}")
                Log.e(TAG, "=== LOGIN FAILED ===")
                Result.failure(Exception(apiError.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== LOGIN EXCEPTION ===")
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            Log.e(TAG, "Exception Stack Trace:", e)
            Result.failure(e)
        }
    }
    
    suspend fun verifyOtp(email: String, otp: String, nextRoute: AuthNextRoute): Result<SignUpResponse> {
        return try {
            val request = VerifyOtpRequest(email = email, otpCode = otp, nextRoute = nextRoute)

            Log.d(TAG, "=== OTP VERIFICATION REQUEST START ===")
            Log.d(TAG, "Request URL: POST /auth/verify-otp")
            Log.d(TAG, "Request Body: ${gson.toJson(request)}")
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  - Email: $email")
            Log.d(TAG, "  - OTP: $otp")
            Log.d(TAG, "  - Next Route: $nextRoute")

            val response = apiService.verifyOtp(request)
            
            Log.d(TAG, "=== OTP VERIFICATION RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Response Message: ${response.message()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Log.d(TAG, "Response Body: ${gson.toJson(body)}")
                    Log.d(TAG, "=== OTP VERIFICATION SUCCESS ===")
                    Result.success(body)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error Response Body: $errorBody")
                
                val apiError = try {
                    gson.fromJson(errorBody, ApiError::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse error response: ${e.message}")
                    ApiError("UNKNOWN_ERROR", "An unknown error occurred")
                }
                
                Log.e(TAG, "Parsed API Error: ${gson.toJson(apiError)}")
                Log.e(TAG, "=== OTP VERIFICATION FAILED ===")
                Result.failure(Exception(apiError.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== OTP VERIFICATION EXCEPTION ===")
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            Log.e(TAG, "Exception Stack Trace:", e)
            Result.failure(e)
        }
    }
    
    suspend fun resendOtp(email: String): Result<SignUpResponse> {
        return try {
            val request = mapOf("email" to email)
            
            Log.d(TAG, "=== RESEND OTP REQUEST START ===")
            Log.d(TAG, "Request URL: POST /auth/resend-otp")
            Log.d(TAG, "Request Body: ${gson.toJson(request)}")
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  - Email: $email")
            
            val response = apiService.resendOtp(request)
            
            Log.d(TAG, "=== RESEND OTP RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Response Message: ${response.message()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Log.d(TAG, "Response Body: ${gson.toJson(body)}")
                    Log.d(TAG, "=== RESEND OTP SUCCESS ===")
                    Result.success(body)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error Response Body: $errorBody")
                
                val apiError = try {
                    gson.fromJson(errorBody, ApiError::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse error response: ${e.message}")
                    ApiError("UNKNOWN_ERROR", "An unknown error occurred")
                }
                
                Log.e(TAG, "Parsed API Error: ${gson.toJson(apiError)}")
                Log.e(TAG, "=== RESEND OTP FAILED ===")
                Result.failure(Exception(apiError.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== RESEND OTP EXCEPTION ===")
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            Log.e(TAG, "Exception Stack Trace:", e)
            Result.failure(e)
        }
    }
    
    suspend fun forgotPassword(email: String): Result<SignUpResponse> {
        return try {
            val request = mapOf("email" to email)
            
            Log.d(TAG, "=== FORGOT PASSWORD REQUEST START ===")
            Log.d(TAG, "Request URL: POST /auth/forgot-password")
            Log.d(TAG, "Request Body: ${gson.toJson(request)}")
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  - Email: $email")
            
            val response = apiService.forgotPassword(request)
            
            Log.d(TAG, "=== FORGOT PASSWORD RESPONSE ===")
            Log.d(TAG, "Response Code: ${response.code()}")
            Log.d(TAG, "Response Message: ${response.message()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Log.d(TAG, "Response Body: ${gson.toJson(body)}")
                    Log.d(TAG, "=== FORGOT PASSWORD SUCCESS ===")
                    Result.success(body)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error Response Body: $errorBody")
                
                val apiError = try {
                    gson.fromJson(errorBody, ApiError::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse error response: ${e.message}")
                    ApiError("UNKNOWN_ERROR", "An unknown error occurred")
                }
                
                Log.e(TAG, "Parsed API Error: ${gson.toJson(apiError)}")
                Log.e(TAG, "=== FORGOT PASSWORD FAILED ===")
                Result.failure(Exception(apiError.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== FORGOT PASSWORD EXCEPTION ===")
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            Log.e(TAG, "Exception Stack Trace:", e)
            Result.failure(e)
        }
    }
}