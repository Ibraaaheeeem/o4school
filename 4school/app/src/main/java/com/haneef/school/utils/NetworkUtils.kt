package com.haneef.school.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.*

/**
 * Network utility functions
 */
object NetworkUtils {
    
    /**
     * Check if device has internet connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Generate a unique device ID
     */
    fun generateDeviceId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Format error message from HTTP response
     */
    fun formatErrorMessage(errorCode: Int, defaultMessage: String = "Unknown error"): String {
        return when (errorCode) {
            400 -> "Bad request. Please check your input."
            401 -> "Unauthorized. Please login again."
            403 -> "Access forbidden. You don't have permission."
            404 -> "Resource not found."
            408 -> "Request timeout. Please try again."
            422 -> "Validation error. Please check your input."
            429 -> "Too many requests. Please try again later."
            500 -> "Server error. Please try again later."
            502 -> "Bad gateway. Please try again later."
            503 -> "Service unavailable. Please try again later."
            504 -> "Gateway timeout. Please try again later."
            else -> defaultMessage
        }
    }
    
    /**
     * Check if error is network related
     */
    fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is java.net.UnknownHostException ||
                throwable is java.net.SocketTimeoutException ||
                throwable is java.net.ConnectException ||
                throwable is java.io.IOException
    }
    
    /**
     * Get user-friendly error message
     */
    fun getErrorMessage(throwable: Throwable): String {
        return when {
            isNetworkError(throwable) -> "Network error. Please check your internet connection."
            throwable.message?.contains("timeout", ignoreCase = true) == true -> "Request timeout. Please try again."
            else -> throwable.localizedMessage ?: "An unexpected error occurred."
        }
    }
}