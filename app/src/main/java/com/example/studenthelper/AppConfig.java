package com.example.studenthelper;

/**
 * Central configuration for StudySphere AI client.
 * Stores backend endpoint base URL and authorization credentials.
 */
public final class AppConfig {

    private AppConfig() {}

    // Base URL for StudySphere Node.js backend
    // Defaults to 10.0.2.2 (Android Emulator host alias)
    // Replace with your public URL (e.g. https://your-app.onrender.com) for production
    public static final String BACKEND_BASE_URL = "http://10.0.2.2:3000";

    // Shared secret header required by backend to prevent unauthorized public abuse
    public static final String APP_TOKEN_HEADER = "x-app-token";
    public static final String APP_TOKEN_VALUE = "StudySphere_Secure_App_Key_2026";
}
