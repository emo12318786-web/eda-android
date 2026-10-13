plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.eda.phoneagent"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.eda.phoneagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
