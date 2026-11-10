package com.example.anonymouspager.push

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks whether any Wen screen is visible. While it is, the in-app ring (sound + Accept/Ignore UI)
 * handles incoming rings, so the push notification is skipped to avoid a double alert.
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var startedActivities = 0

    val isForeground: Boolean get() = startedActivities > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
