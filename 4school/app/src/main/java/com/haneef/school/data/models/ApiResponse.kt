package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

/**
 * Generic API Response wrapper
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: T? = null,
    @SerializedName("errors")
    val errors: List<String>? = null,
    @SerializedName("status_code")
    val statusCode: Int? = null
)

/**
 * Paginated API Response
 */
data class PaginatedResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<T>? = null,
    @SerializedName("pagination")
    val pagination: Pagination? = null,
    @SerializedName("errors")
    val errors: List<String>? = null
)

data class Pagination(
    @SerializedName("current_page")
    val currentPage: Int,
    @SerializedName("per_page")
    val perPage: Int,
    @SerializedName("total")
    val total: Int,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("has_next")
    val hasNext: Boolean,
    @SerializedName("has_previous")
    val hasPrevious: Boolean
)

/**
 * Error Response
 */
data class ErrorResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String,
    @SerializedName("errors")
    val errors: List<String>? = null,
    @SerializedName("error_code")
    val errorCode: String? = null
)