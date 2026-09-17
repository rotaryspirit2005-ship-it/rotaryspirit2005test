package com.example.schedulelink

import android.app.Application
import com.example.schedulelink.data.AppDatabase
import com.example.schedulelink.data.ScheduleRepository

class ScheduleLinkApplication : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { ScheduleRepository(database.scheduleDao()) }
}
