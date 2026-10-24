import java.util.Properties

plugins {
    id("com.android.application")
}

// Chiave di firma release: vive fuori dal repository. Il percorso del file .properties
// (storeFile, storePassword, keyAlias, keyPassword) si puo' cambiare con -PfirmaRelease=...
// o con la variabile d'ambiente CALCOLO_INSULINA_FIRMA.
val fileFirma = file(
    providers.gradleProperty("firmaRelease")
        .orElse(providers.environmentVariable("CALCOLO_INSULINA_FIRMA"))
        .getOrElse(System.getProperty("user.home") + "/android-keys/calcolo-insulina.properties")
)
val firma = Properties().apply {
    if (fileFirma.exists()) fileFirma.inputStream().use { load(it) }
}

android {
    namespace = "com.calcoloinsulina.app"
    compileSdk = 37 // Android 17

    defaultConfig {
        applicationId = "com.calcoloinsulina.app"
        minSdk = 23 // Android 6.0
        targetSdk = 37
        versionCode = 8
        versionName = "2.1.0"
    }

    signingConfigs {
        if (fileFirma.exists()) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: run {
                logger.warn("Chiave release non trovata ($fileFirma): APK firmato con la chiave debug")
                signingConfigs.getByName("debug")
            }
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
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
    // org.json vero per i test JVM (quello di android.jar e' solo uno stub)
    testImplementation("org.json:json:20240303")
}
