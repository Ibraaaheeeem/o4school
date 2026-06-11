package com.haneef.school.data.api

import com.haneef.school.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface SchoolApiService {

    @PUT("auth/school/schools/{school_id}")
    suspend fun updateSchoolData(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String,
        @Body request: SchoolData
    ): Response<SchoolData>

    @GET("auth/school/{school_id}")
    suspend fun getCurrentSchoolData(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String
    ): Response<SchoolData>

    @GET("auth/school/{school_id}/academic-structure")
    suspend fun getAcademicStructure(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String
    ): Response<AcademicStructureResponse>

    @POST("auth/school/tracks")
    suspend fun createTrack(
        @Header("Authorization") authorization: String,
        @Body request: CreateEducationTrackRequest
    ): Response<AcademicTrackNode>

    @POST("auth/school/departments")
    suspend fun createDepartment(
        @Header("Authorization") authorization: String,
        @Body request: CreateDepartmentRequest
    ): Response<DepartmentNode>

    @POST("auth/school/classes")
    suspend fun createClass(
        @Header("Authorization") authorization: String,
        @Body requ
// MISSING LINE 44
// MISSING LINE 45
// MISSING LINE 46
// MISSING LINE 47
// MISSING LINE 48
// MISSING LINE 49
// MISSING LINE 50
// MISSING LINE 51
// MISSING LINE 52
// MISSING LINE 53
// MISSING LINE 54
// MISSING LINE 55
// MISSING LINE 56
// MISSING LINE 57
// MISSING LINE 58
// MISSING LINE 59
// MISSING LINE 60
// MISSING LINE 61
// MISSING LINE 62
// MISSING LINE 63
// MISSING LINE 64
// MISSING LINE 65
// MISSING LINE 66
// MISSING LINE 67
// MISSING LINE 68
// MISSING LINE 69
// MISSING LINE 70
// MISSING LINE 71
// MISSING LINE 72
// MISSING LINE 73
// MISSING LINE 74
// MISSING LINE 75
// MISSING LINE 76
// MISSING LINE 77
// MISSING LINE 78
// MISSING LINE 79
// MISSING LINE 80
// MISSING LINE 81
// MISSING LINE 82
// MISSING LINE 83
// MISSING LINE 84
// MISSING LINE 85
// MISSING LINE 86
// MISSING LINE 87
// MISSING LINE 88
// MISSING LINE 89
// MISSING LINE 90
// MISSING LINE 91
// MISSING LINE 92
// MISSING LINE 93
// MISSING LINE 94
// MISSING LINE 95
// MISSING LINE 96
// MISSING LINE 97
// MISSING LINE 98
// MISSING LINE 99
// MISSING LINE 100
// MISSING LINE 101
// MISSING LINE 102
// MISSING LINE 103
// MISSING LINE 104
    @POST("auth/staff/with-user")
    suspend fun createStaffWithUser(
        @Header("Authorization") authorization: String,
        @Body request: CreateStaffWithUserRequest
    ): Response<Staff>

    @PUT("auth/staff/{id}")
    suspend fun updateStaff(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: Staff
    ): Response<Staff>

    @DELETE("auth/staff/{id}")
    suspend fun deleteStaff(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("auth/staff/{id}/classes/assign")
    suspend fun assignStaffClasses(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffClassesRequest
    ): Response<Unit>

    @POST("auth/staff/{id}/subjects/assign")
    suspend fun assignStaffSubjects(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffSubjectsRequest
    ): Response<Unit>

    @GET("auth/schedule/current")
    suspend fun getCurrentSchedule(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<CurrentScheduleResponse>

    @GET("auth/parents")
    suspend fun getParents(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("track_id") trackId: String? = null,
        @Query("class_id") classId: String? = null
    ): Response<PaginatedResponse<ParentListResponse>>
}
