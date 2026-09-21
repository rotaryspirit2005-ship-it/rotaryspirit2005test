package com.example.schedulelink

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.schedulelink.data.AuthRepository
import com.example.schedulelink.data.FamilyRepository
import com.example.schedulelink.data.WidgetPreferences
import com.example.schedulelink.ui.settings.PhotoFeaturePreferences
import com.example.schedulelink.ui.theme.ThemePreferences
import com.example.schedulelink.ui.widget.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

private const val WIDGET_UPDATE_WORK_NAME = "widget_update"

class ScheduleLinkApplication : Application() {

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val authRepository by lazy { AuthRepository(firebaseAuth) }
    val familyRepository by lazy { FamilyRepository(firestore) }
    val themePreferences by lazy { ThemePreferences(this) }
    val photoFeaturePreferences by lazy { PhotoFeaturePreferences(this) }
    val widgetPreferences by lazy { WidgetPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        // ウィジェットは手動更新ボタンに加えて、30分おきの定期更新でも内容を最新化する。
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
