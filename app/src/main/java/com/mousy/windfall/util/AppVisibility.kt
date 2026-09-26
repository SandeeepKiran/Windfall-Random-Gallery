package com.mousy.windfall.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Foreground gate for idle background work (thumbnail farmer etc.), with no extra
 * lifecycle-process dependency: counts started activities via lifecycle callbacks.
 * Background work should stand down when the user isn't looking — old phones especially.
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile
    var visible: Boolean = false
        private set

    private var started = 0

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started++
        visible = started > 0
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        visible = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
