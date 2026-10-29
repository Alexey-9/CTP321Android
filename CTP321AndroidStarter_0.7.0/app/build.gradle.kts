plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.ctplab.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.ctplab.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.8.4"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
