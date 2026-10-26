package com.example.studenthelper;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.LocalCacheSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class StudentHelperApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        int themeMode = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
        
        FirebaseApp.initializeApp(this);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build();
        db.setFirestoreSettings(settings);
    }
}
