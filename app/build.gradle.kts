plugins {
    id("com.android.application")
}

android {
    namespace = "com.calcoloinsulina.app"
    compileSdk = 37 // Android 17

    defaultConfig {
        applicationId = "com.calcoloinsulina.app"
        minSdk = 23 // Android 6.0
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.19.1")
}
