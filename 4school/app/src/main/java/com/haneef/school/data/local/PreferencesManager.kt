package com.haneef.school.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.haneef.school.data.models.User
import com.haneef.school.data.repository.SessionStore

/**
 * Preferences manager for local data storage
 */
class PreferencesManager(context: Context) : SessionStore {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )
    
    private val gson = Gson()
    
    companion object {
        private const val PREF_NAME = "edumanage_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FIRST_TIME = "first_time"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_SCHOOL_ID = "school_id"
        private const val KEY_SCHOOL_NAME = "school_name"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
    }
    
    // Authentication related
    override suspend fun saveAccessToken(token: String) {
        sharedPreferences.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }
    
    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun saveRefreshToken(token: String) {
        sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }
    
    fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }
    
    fun saveLoginTimestamp(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LOGIN_TIMESTAMP, timestamp).apply()
    }
    
    fun getLoginTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LOGIN_TIMESTAMP, 0L)
    }

    /**
     * Returns true if there is a saved access token AND it was obtained less than 24 hours ago.
     */
    fun isTokenValid(): Boolean {
        val token = getAccessToken()
        if (token.isNullOrBlank()) return false
        val timestamp = getLoginTimestamp()
        if (timestamp == 0L) return false
        val elapsedMs = System.currentTimeMillis() - timestamp
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L
        return elapsedMs < twentyFourHoursMs
    }
    
    fun saveUserData(user: User) {
        val userJson = gson.toJson(user)
        sharedPreferences.edit().putString(KEY_USER_DATA, userJson).apply()
    }
    
    fun getUserData(): User? {
        val userJson = sharedPreferences.getString(KEY_USER_DATA, null)
        return if (userJson != null) {
            try {
                gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    fun setLoggedIn(isLoggedIn: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }
    
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    // School related
    override suspend fun saveSchoolId(schoolId: String?) {
        sharedPreferences.edit().putString(KEY_SCHOOL_ID, schoolId).apply()
    }
    
    fun getSchoolId(): String? {
        return sharedPreferences.getString(KEY_SCHOOL_ID, null)
    }
    
    fun saveSchoolName(schoolName: String?) {
        sharedPreferences.edit().putString(KEY_SCHOOL_NAME, schoolName).apply()
    }
    
    fun getSchoolName(): String? {
        return sharedPreferences.getString(KEY_SCHOOL_NAME, null)
    }
    
    // App settings
    fun setFirstTime(isFirstTime: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FIRST_TIME, isFirstTime).apply()
    }
    
    fun isFirstTime(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_TIME, true)
    }
    
    fun setThemeMode(themeMode: String) {
        sharedPreferences.edit().putString(KEY_THEME_MODE, themeMode).apply()
    }
    
    fun getThemeMode(): String {
        return sharedPreferences.getString(KEY_THEME_MODE, "system") ?: "system"
    }
    
    fun setLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply()
    }
    
    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "en") ?: "en"
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    fun areNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }
    
    // Clear all data (logout)
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    // Clear only auth data
    fun clearAuthData() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_DATA)
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_SCHOOL_ID)
            .remove(KEY_SCHOOL_NAME)
            .apply()
    }
}