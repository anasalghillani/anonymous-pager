package com.example.anonymouspager

import android.app.Application
import com.example.anonymouspager.push.AppVisibility
import com.example.anonymouspager.push.NotificationHelper
import com.google.firebase.FirebaseApp

class PagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        NotificationHelper.createChannels(this)
        AppVisibility.register(this)
    }
}
