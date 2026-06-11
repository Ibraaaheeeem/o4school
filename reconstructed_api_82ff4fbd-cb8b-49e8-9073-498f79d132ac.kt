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
        @Header("Authorization") authorization: String,
        @Body request: LinkClassToSubjectRequest
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}/classes/{class_id}/unlink")
    suspend fun unlinkClassFromSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Path("class_id") classId: String,
        @Header("Authorization") authorization: String
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}")
    suspend fun deactivateSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Header("Authorization") authorization: String
    ): Response<List<SchoolSubjectResponse>>

    @GET("auth/staff")
    suspend fun getStaff(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("track_id") trackId: String? = null,
        @Query("department_id") departmentId: String? = null,
        @Query("class_id") classId: String? = null,
        @Query("designation") designation: String? = null
    ): Response<PaginatedResponse<Staff>>

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
