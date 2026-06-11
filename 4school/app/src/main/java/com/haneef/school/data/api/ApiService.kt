package com.haneef.school.data.api

import com.google.gson.annotations.SerializedName
import com.haneef.school.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Main API service interface
 */
interface ApiService {
    
    // Authentication endpoints
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>
    
    @POST("auth/sign-up")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<User>>
    
    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: OtpVerificationRequest): Response<ApiResponse<LoginResponse>>
    
    @POST("auth/resend-otp")
    suspend fun resendOtp(@Body request: Map<String, String>): Response<ApiResponse<String>>
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: PasswordResetRequest): Response<ApiResponse<String>>
    
    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: Map<String, String>): Response<ApiResponse<LoginResponse>>
    
    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<String>>
    
    // User profile endpoints
    @GET("user/profile")
    suspend fun getUserProfile(): Response<ApiResponse<User>>
    
    @PUT("user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<User>>
    
    @POST("user/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<String>>
    
    @Multipart
    @POST("user/upload-avatar")
    suspend fun uploadAvatar(@Part avatar: okhttp3.MultipartBody.Part): Response<ApiResponse<String>>
    
    // School management endpoints
    @GET("school/info")
    suspend fun getSchoolInfo(): Response<ApiResponse<School>>
    
    @PUT("school/info")
    suspend fun updateSchoolInfo(@Body school: School): Response<ApiResponse<School>>
    
    // Student endpoints
    @GET("students")
    suspend fun getStudents(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("class_id") classId: String? = null
    ): Response<PaginatedResponse<Student>>
    
    @GET("students/{id}")
    suspend fun getStudent(@Path("id") studentId: String): Response<ApiResponse<Student>>
    
    @POST("students")
    suspend fun createStudent(@Body student: Student): Response<ApiResponse<Student>>
    
    @PUT("students/{id}")
    suspend fun updateStudent(@Path("id") studentId: String, @Body student: Student): Response<ApiResponse<Student>>
    
    @DELETE("students/{id}")
    suspend fun deleteStudent(@Path("id") studentId: String): Response<ApiResponse<String>>
    
    // Staff endpoints
    @GET("staff")
    suspend fun getStaff(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("department") department: String? = null
    ): Response<PaginatedResponse<Staff>>
    
    @GET("staff/{id}")
    suspend fun getStaffMember(@Path("id") staffId: String): Response<ApiResponse<Staff>>
    
    @POST("staff")
    suspend fun createStaff(@Body staff: Staff): Response<ApiResponse<Staff>>
    
    @PUT("staff/{id}")
    suspend fun updateStaff(@Path("id") staffId: String, @Body staff: Staff): Response<ApiResponse<Staff>>
    
    @DELETE("staff/{id}")
    suspend fun deleteStaff(@Path("id") staffId: String): Response<ApiResponse<String>>
    
    // Subject endpoints
    @GET("subjects")
    suspend fun getSubjects(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<PaginatedResponse<Subject>>
    
    @POST("subjects")
    suspend fun createSubject(@Body subject: Subject): Response<ApiResponse<Subject>>
    
    @PUT("subjects/{id}")
    suspend fun updateSubject(@Path("id") subjectId: String, @Body subject: Subject): Response<ApiResponse<Subject>>
    
    @DELETE("subjects/{id}")
    suspend fun deleteSubject(@Path("id") subjectId: String): Response<ApiResponse<String>>
    
    // Class endpoints
    @GET("classes")
    suspend fun getClasses(): Response<ApiResponse<List<SchoolClass>>>
    
    @POST("classes")
    suspend fun createClass(@Body schoolClass: SchoolClass): Response<ApiResponse<SchoolClass>>
    
    @PUT("classes/{id}")
    suspend fun updateClass(@Path("id") classId: String, @Body schoolClass: SchoolClass): Response<ApiResponse<SchoolClass>>
    
    @DELETE("classes/{id}")
    suspend fun deleteClass(@Path("id") classId: String): Response<ApiResponse<String>>

    // Dashboard/Analytics endpoints
    // Dashboard route that returns full dashboard payload
    @GET("auth/dashboard")
    suspend fun getDashboardData(): Response<ApiResponse<DashboardResponse>>

    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<ApiResponse<DashboardResponse>>

    @GET("dashboard/overview")
    suspend fun getDashboardOverview(): Response<ApiResponse<DashboardResponse>>

    @GET("analytics/enrollment")
    suspend fun getEnrollmentAnalytics(@Query("period") period: String): Response<ApiResponse<List<EnrollmentAnalytics>>>

    @GET("analytics/finance")
    suspend fun getFinanceAnalytics(@Query("period") period: String): Response<ApiResponse<FinancialHealth>>

    // User management endpoints
    @GET("auth/users")
    suspend fun getSchoolUsers(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("role") role: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<PaginatedResponse<SchoolUser>>

    @PUT("auth/users/{id}/deactivate")
    suspend fun deactivateUser(
        @Header("Authorization") authorization: String,
        @Path("id") userId: String
    ): Response<ApiResponse<Unit>>

    @PUT("auth/users/{id}/activate")
    suspend fun activateUser(
        @Header("Authorization") authorization: String,
        @Path("id") userId: String
    ): Response<ApiResponse<Unit>>

    @PUT("auth/users/{id}/deverify")
    suspend fun deverifyUser(
        @Header("Authorization") authorization: String,
        @Path("id") userId: String
    ): Response<ApiResponse<Unit>>

    @POST("auth/users/{id}/activation-reminder")
    suspend fun sendActivationReminder(
        @Header("Authorization") authorization: String,
        @Path("id") userId: String
    ): Response<ApiResponse<Unit>>

}

data class DashboardResponse(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("school_name")
    val schoolName: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("admin_overview")
    val adminOverview: AdminOverview? = null,
    @SerializedName("staff_overview")
    val staffOverview: StaffOverview? = null,
    @SerializedName("parent_overview")
    val parentOverview: ParentOverview? = null,
    @SerializedName("student_overview")
    val studentOverview: StudentOverview? = null,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealth,
    @SerializedName("critical_alerts")
    val criticalAlerts: List<CriticalAlert> = emptyList(),
    @SerializedName("upcoming_events")
    val upcomingEvents: List<CalendarEventSummary> = emptyList()
)
