package com.haneef.school.data.repository

import com.haneef.school.data.api.ApiService
import com.haneef.school.data.api.DashboardResponse
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.*
import com.haneef.school.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/**
 * User repository for profile management
 */
class UserRepository(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) {
    
    suspend fun getUserProfile(): Flow<Resource<User>> = flow {
        try {
            emit(Resource.Loading())
            
            val response = apiService.getUserProfile()
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    // Update local user data
                    preferencesManager.saveUserData(apiResponse.data)
                    emit(Resource.Success(apiResponse.data))
                } else {
                    emit(Resource.Error(apiResponse?.message ?: "Failed to fetch profile"))
                }
            } else {
                emit(Resource.Error("Failed to fetch profile: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }
    
    suspend fun updateProfile(
        fullName: String,
        email: String,
        phoneNumber: String?,
        department: String?
    ): Flow<Resource<User>> = flow {
        try {
            emit(Resource.Loading())
            
            val updateRequest = UpdateProfileRequest(
                fullName = fullName,
                email = email,
                phoneNumber = phoneNumber,
                department = department
            )
            
            val response = apiService.updateProfile(updateRequest)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    // Update local user data
                    preferencesManager.saveUserData(apiResponse.data)
                    emit(Resource.Success(apiResponse.data))
                } else {
                    emit(Resource.Error(apiResponse?.message ?: "Failed to update profile"))
                }
            } else {
                emit(Resource.Error("Failed to update profile: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }
    
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        newPasswordConfirmation: String
    ): Flow<Resource<String>> = flow {
        try {
            emit(Resource.Loading())
            
            val changePasswordRequest = ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = newPassword,
                newPasswordConfirmation = newPasswordConfirmation
            )
            
            val response = apiService.changePassword(changePasswordRequest)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true) {
                    emit(Resource.Success(apiResponse.message))
                } else {
                    emit(Resource.Error(apiResponse?.message ?: "Failed to change password"))
                }
            } else {
                emit(Resource.Error("Failed to change password: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }
    
    suspend fun uploadAvatar(imageFile: File): Flow<Resource<String>> = flow {
        try {
            emit(Resource.Loading())
            
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("avatar", imageFile.name, requestFile)
            
            val response = apiService.uploadAvatar(body)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    // Update user profile with new avatar URL
                    val currentUser = preferencesManager.getUserData()
                    if (currentUser != null) {
                        //val updatedUser = currentUser.copy(profileImageUrl = apiResponse.data)
                        //preferencesManager.saveUserData(updatedUser)
                    }
                    emit(Resource.Success(apiResponse.data))
                } else {
                    emit(Resource.Error(apiResponse?.message ?: "Failed to upload avatar"))
                }
            } else {
                emit(Resource.Error("Failed to upload avatar: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }
    
    suspend fun getDashboard(): Flow<Resource<DashboardResponse>> = flow {
        try {
            emit(Resource.Loading())
            val response = apiService.getDashboardData()
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    emit(Resource.Success(apiResponse.data))
                } else {
                    emit(Resource.Error(apiResponse?.message ?: "Failed to load dashboard"))
                }
            } else {
                emit(Resource.Error("Failed to load dashboard: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }

    fun getCachedUser(): User? {
        return preferencesManager.getUserData()
    }

    suspend fun getSchoolUsers(
        schoolId: String,
        role: String? = null,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 20
    ): Flow<Resource<PaginatedResponse<SchoolUser>>> = flow {
        try {
            emit(Resource.Loading())
            val accessToken = preferencesManager.getAccessToken() ?: ""
            val response = apiService.getSchoolUsers(
                authorization = "Bearer $accessToken",
                schoolId = schoolId,
                role = role,
                search = search,
                page = page,
                perPage = perPage
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("Failed to fetch users: ${response.message()}"))
            }
        } catch (e: HttpException) {
            emit(Resource.Error("Network error: ${e.localizedMessage}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: Check your internet connection"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.localizedMessage}"))
        }
    }

    suspend fun deactivateUser(userId: String): Flow<Resource<Unit>> = flow {
        try {
            emit(Resource.Loading())
            val accessToken = preferencesManager.getAccessToken() ?: ""
            val response = apiService.deactivateUser(
                authorization = "Bearer $accessToken",
                userId = userId
            )
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Failed to deactivate user: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Unexpected error occurred"))
        }
    }

    suspend fun activateUser(userId: String): Flow<Resource<Unit>> = flow {
        try {
            emit(Resource.Loading())
            val accessToken = preferencesManager.getAccessToken() ?: ""
            val response = apiService.activateUser(
                authorization = "Bearer $accessToken",
                userId = userId
            )
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Failed to activate user: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Unexpected error occurred"))
        }
    }

    suspend fun deverifyUser(userId: String): Flow<Resource<Unit>> = flow {
        try {
            emit(Resource.Loading())
            val accessToken = preferencesManager.getAccessToken() ?: ""
            val response = apiService.deverifyUser(
                authorization = "Bearer $accessToken",
                userId = userId
            )
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Failed to deverify user: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Unexpected error occurred"))
        }
    }

    suspend fun sendActivationReminder(userId: String): Flow<Resource<Unit>> = flow {
        try {
            emit(Resource.Loading())
            val accessToken = preferencesManager.getAccessToken() ?: ""
            val response = apiService.sendActivationReminder(
                authorization = "Bearer $accessToken",
                userId = userId
            )
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Failed to send activation reminder: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Unexpected error occurred"))
        }
    }
}