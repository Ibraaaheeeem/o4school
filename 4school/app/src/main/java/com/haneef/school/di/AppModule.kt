package com.haneef.school.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.haneef.school.data.api.AuthApiService
import com.haneef.school.data.api.DashboardApiService
import com.haneef.school.data.api.SchoolApiService
import com.haneef.school.data.api.ApiService
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.repository.AuthRepository
import com.haneef.school.data.repository.DashboardRepository
import com.haneef.school.data.repository.SchoolRepository
import com.haneef.school.data.repository.UserRepository
import com.haneef.school.viewmodel.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val appModule = module {
    
    // Preferences Manager
    single<PreferencesManager> {
        PreferencesManager(androidContext())
    }
    
    // Gson
    single<Gson> {
        GsonBuilder()
            .setLenient()
            .create()
    }
    
    // HTTP Logging Interceptor
    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    // OkHttp Client
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Retrofit
    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("http://localhost:8080/api/")
            .client(get<OkHttpClient>())
            .addConverterFactory(GsonConverterFactory.create(get<Gson>()))
            .build()
    }
    
    // API Service
    single<AuthApiService> {
        get<Retrofit>().create(AuthApiService::class.java)
    }
    
    single<DashboardApiService> {
        get<Retrofit>().create(DashboardApiService::class.java)
    }
    
    single<SchoolApiService> {
        get<Retrofit>().create(SchoolApiService::class.java)
    }

    single<ApiService> {
        get<Retrofit>().create(ApiService::class.java)
    }
    
    // Repository
    single<AuthRepository> {
        AuthRepository(get<AuthApiService>(), get<Gson>(), get<PreferencesManager>())
    }
    
    single<DashboardRepository> {
        DashboardRepository(get<DashboardApiService>(), get<Gson>())
    }
    
    single<SchoolRepository> {
        SchoolRepository(get<SchoolApiService>(), get<Gson>())
    }

    single<UserRepository> {
        UserRepository(get<ApiService>(), get<PreferencesManager>())
    }
    
    // ViewModel
    viewModel<AuthViewModel> {
        AuthViewModel(get<AuthRepository>(), get<PreferencesManager>())
    }
    
    viewModel<DashboardViewModel> {
        DashboardViewModel(get<DashboardRepository>(), get<PreferencesManager>())
    }
    
    viewModel<SchoolViewModel> {
        SchoolViewModel(get<SchoolRepository>())
    }

    viewModel<UserViewModel> {
        UserViewModel(get<UserRepository>())
    }

    viewModel<AcademicCalendarViewModel> {
        AcademicCalendarViewModel(get<SchoolRepository>())
    }

    viewModel<SchoolCalendarViewModel> {
        SchoolCalendarViewModel(get<SchoolRepository>())
    }

    viewModel<ParentViewModel> {
        ParentViewModel(get<SchoolRepository>())
    }

    viewModel<StudentViewModel> {
        StudentViewModel(get<SchoolRepository>())
    }

    viewModel<StaffViewModel> {
        StaffViewModel(get<SchoolRepository>())
    }

    viewModel<TimetableViewModel> {
        TimetableViewModel(get<SchoolRepository>())
    }
}
