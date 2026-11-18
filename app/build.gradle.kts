plugins {
    id("com.android.application")
}

android {
    namespace = "com.rumpa.gemduel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rumpa.gemduel"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "15.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
// No external dependencies on purpose: builds fully offline on-device.
