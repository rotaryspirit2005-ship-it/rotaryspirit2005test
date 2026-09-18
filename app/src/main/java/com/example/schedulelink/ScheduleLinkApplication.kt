package com.example.schedulelink

import android.app.Application
import com.example.schedulelink.data.AuthRepository
import com.example.schedulelink.data.FamilyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ScheduleLinkApplication : Application() {

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val authRepository by lazy { AuthRepository(firebaseAuth) }
    val familyRepository by lazy { FamilyRepository(firestore) }
}
