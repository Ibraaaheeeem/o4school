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
        @Body request: CreateClassRequest
    ): Response<ClassNode>

    @POST("auth/school/default-structure/initialize")
    suspend fun initializeDefaultStructure(
        @Header("Authorization") authorization: String,
        @Body request: InitializeDefaultStructureRequest
    ): Response<InitializeDefaultStructureResponse>

    @GET("auth/school/subjects")
    suspend fun getAllSubjects(
        @Header("Authorization") authorization: String
    ): Response<List<SubjectNode>>

    @GET("auth/school/{school_id}/subjects")
    suspend fun getSchoolSubjects(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String,
        @Query("class_name") className: String? = null,
        @Query("department_id") departmentId: String? = null,
        @Query("track_id") trackId: String? = null
    ): Response<List<SchoolSubjectResponse>>

    @POST("auth/school/school-subjects")
    suspend fun saveSchoolSubjects(
        @Header("Authorization") authorization: String,
        @Body request: SaveSchoolSubjectsRequest
    ): Response<List<SchoolSubjectResponse>>

    @POST("auth/school/school-subjects/{school_subject_id}/classes/link")
    suspend fun linkClassesToSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Header("Authorization") authorization: String,
        @Body request: LinkClassToSubjectRequest
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}/classes/{class_id}/unlink")
    suspend fun unlinkClassFromSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Path("class_id") classId: String,
        @Header("Authorization") authorization: String
    ): Response<List<SchoolSubjectResponse>>
}
