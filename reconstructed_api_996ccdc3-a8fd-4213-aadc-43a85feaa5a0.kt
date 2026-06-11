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
// MISSING LINE 105
// MISSING LINE 106
// MISSING LINE 107
// MISSING LINE 108
// MISSING LINE 109
// MISSING LINE 110
// MISSING LINE 111
// MISSING LINE 112
// MISSING LINE 113
// MISSING LINE 114
// MISSING LINE 115
// MISSING LINE 116
// MISSING LINE 117
// MISSING LINE 118
// MISSING LINE 119
// MISSING LINE 120
// MISSING LINE 121
// MISSING LINE 122
// MISSING LINE 123
// MISSING LINE 124
// MISSING LINE 125
// MISSING LINE 126
// MISSING LINE 127
// MISSING LINE 128
// MISSING LINE 129
// MISSING LINE 130
// MISSING LINE 131
// MISSING LINE 132
// MISSING LINE 133
// MISSING LINE 134
// MISSING LINE 135
// MISSING LINE 136
// MISSING LINE 137
// MISSING LINE 138
// MISSING LINE 139
// MISSING LINE 140
// MISSING LINE 141
// MISSING LINE 142
// MISSING LINE 143
// MISSING LINE 144
// MISSING LINE 145
// MISSING LINE 146
// MISSING LINE 147
// MISSING LINE 148
// MISSING LINE 149
// MISSING LINE 150
// MISSING LINE 151
// MISSING LINE 152
// MISSING LINE 153
// MISSING LINE 154
// MISSING LINE 155
// MISSING LINE 156
// MISSING LINE 157
// MISSING LINE 158
// MISSING LINE 159
// MISSING LINE 160
// MISSING LINE 161
// MISSING LINE 162
// MISSING LINE 163
// MISSING LINE 164
// MISSING LINE 165
// MISSING LINE 166
// MISSING LINE 167
// MISSING LINE 168
// MISSING LINE 169
// MISSING LINE 170
// MISSING LINE 171
// MISSING LINE 172
// MISSING LINE 173
// MISSING LINE 174
// MISSING LINE 175
// MISSING LINE 176
// MISSING LINE 177
// MISSING LINE 178
// MISSING LINE 179
// MISSING LINE 180
// MISSING LINE 181
// MISSING LINE 182
// MISSING LINE 183
// MISSING LINE 184
// MISSING LINE 185
// MISSING LINE 186
// MISSING LINE 187
// MISSING LINE 188
// MISSING LINE 189
// MISSING LINE 190
// MISSING LINE 191
// MISSING LINE 192
// MISSING LINE 193
// MISSING LINE 194
// MISSING LINE 195
// MISSING LINE 196
// MISSING LINE 197
// MISSING LINE 198
// MISSING LINE 199
// MISSING LINE 200
// MISSING LINE 201
// MISSING LINE 202
// MISSING LINE 203
// MISSING LINE 204
// MISSING LINE 205
        @Query("track_id") trackId: String? = null,
        @Query("class_id") classId: String? = null
    ): Response<PaginatedResponse<Student>>

    @POST("auth/students/with-user")
    suspend fun createStudentWithUser(
        @Header("Authorization") authorization: String,
        @Body request: CreateStudentRequest
    ): Response<Student>

    @GET("auth/students/{id}")
    suspend fun getStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<StudentDetailDto>

    @PUT("auth/students/{id}")
    suspend fun updateStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: StudentDetailDto
    ): Response<StudentDetailDto>

    @DELETE("auth/students/{id}")
    suspend fun deleteStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/students/{id}/classes")
    suspend fun getStudentClassAssignments(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<List<StudentClassAssignmentResponse>>

    @POST("auth/students/{id}/classes/assign")
    suspend fun assignStudentClasses(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStudentClassesRequest
    ): Response<okhttp3.ResponseBody>

    @DELETE("auth/students/classes/assignments/{assignment_id}")
    suspend fun deleteStudentClassAssignment(
        @Path("assignment_id") assignmentId: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>
}
