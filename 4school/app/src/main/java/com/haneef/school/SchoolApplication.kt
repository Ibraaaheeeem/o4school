package com.haneef.school

import android.app.Application
import com.haneef.school.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SchoolApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@SchoolApplication)
            modules(appModule)
        }
    }
}